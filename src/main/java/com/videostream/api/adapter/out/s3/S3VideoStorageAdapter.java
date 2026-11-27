package com.videostream.api.adapter.out.s3;

import com.videostream.api.domain.exception.InvalidRangeException;
import com.videostream.api.domain.exception.InvalidVideoIdException;
import com.videostream.api.domain.exception.VideoNotFoundException;
import com.videostream.api.domain.exception.VideoStorageException;
import com.videostream.api.domain.model.ByteRange;
import com.videostream.api.domain.model.VideoId;
import com.videostream.api.domain.model.VideoMetadata;
import com.videostream.api.domain.model.VideoSummary;
import com.videostream.api.domain.port.VideoStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Adaptador de saída: implementa {@link VideoStoragePort} sobre o AWS S3.
 *
 * <p>Decisões relevantes:
 * <ul>
 *   <li>{@code HEAD} antes do {@code GET} para conhecer o tamanho total, validar a faixa
 *       e devolver {@code Content-Range} correto;</li>
 *   <li>{@code GET} com {@code Range} nativo do S3, então a AWS só transfere os bytes pedidos;</li>
 *   <li>exceções do SDK são traduzidas para exceções de domínio, mantendo o resto da
 *       aplicação ignorante ao provedor;</li>
 *   <li>o stream devolvido é um {@code ResponseInputStream}, que aborta a conexão ao ser
 *       fechado (evita conexões penduradas quando o cliente cancela o download).</li>
 * </ul>
 */
@Component
public class S3VideoStorageAdapter implements VideoStoragePort {

    private static final Logger log = LoggerFactory.getLogger(S3VideoStorageAdapter.class);

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_RANGE_NOT_SATISFIABLE = 416;

    private final S3Client s3Client;
    private final S3StorageProperties properties;

    public S3VideoStorageAdapter(S3Client s3Client, S3StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public VideoMetadata getMetadata(VideoId id) {
        String key = keyOf(id);
        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build();
        try {
            HeadObjectResponse head = s3Client.headObject(request);
            return new VideoMetadata(
                    id,
                    filenameOf(key),
                    resolveContentType(head, key),
                    head.contentLength(),
                    head.eTag(),
                    head.lastModified());
        } catch (NoSuchKeyException ex) {
            throw new VideoNotFoundException(id.value());
        } catch (S3Exception ex) {
            if (ex.statusCode() == HTTP_NOT_FOUND) {
                throw new VideoNotFoundException(id.value());
            }
            throw new VideoStorageException("Falha ao consultar metadados do vídeo " + id.value(), ex);
        } catch (SdkException ex) {
            throw new VideoStorageException("Falha ao consultar metadados do vídeo " + id.value(), ex);
        }
    }

    @Override
    public InputStream openStream(VideoId id, ByteRange range) {
        String key = keyOf(id);
        GetObjectRequest.Builder request = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key);
        if (!range.isWholeRepresentation()) {
            request.range("bytes=%d-%d".formatted(range.start(), range.endInclusive()));
        }
        try {
            log.debug("GetObject bucket={} key={} range={}", properties.bucket(), key, range);
            return s3Client.getObject(request.build());
        } catch (NoSuchKeyException ex) {
            throw new VideoNotFoundException(id.value());
        } catch (S3Exception ex) {
            if (ex.statusCode() == HTTP_NOT_FOUND) {
                throw new VideoNotFoundException(id.value());
            }
            if (ex.statusCode() == HTTP_RANGE_NOT_SATISFIABLE) {
                // Objeto mudou de tamanho entre o HEAD e o GET: a faixa deixou de existir.
                throw new InvalidRangeException(range.toString(), range.totalSize());
            }
            throw new VideoStorageException("Falha ao ler o vídeo " + id.value(), ex);
        } catch (SdkException ex) {
            throw new VideoStorageException("Falha ao ler o vídeo " + id.value(), ex);
        }
    }

    @Override
    public List<VideoSummary> listVideos(int limit) {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(properties.bucket())
                .prefix(properties.keyPrefix())
                .maxKeys(limit)
                .build();
        try {
            ListObjectsV2Response response = s3Client.listObjectsV2(request);
            return response.contents().stream()
                    .map(this::toSummary)
                    .flatMap(Optional::stream)
                    .limit(limit)
                    .toList();
        } catch (NoSuchBucketException ex) {
            log.info("Bucket '{}' ainda não existe no storage; criando bucket vazio.", properties.bucket());
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
                return List.of();
            } catch (SdkException createEx) {
                throw new VideoStorageException("Falha ao criar o bucket " + properties.bucket(), createEx);
            }
        } catch (SdkException ex) {
            throw new VideoStorageException("Falha ao listar os vídeos do bucket " + properties.bucket(), ex);
        }
    }

    @Override
    public VideoMetadata save(VideoId id, InputStream content, long sizeBytes, String contentType) {
        String key = keyOf(id);
        String filename = filenameOf(key);
        String resolvedContentType = (contentType != null && !contentType.isBlank())
                ? contentType
                : guessContentType(filename);

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("video-upload-", ".tmp");
            long copiedBytes = Files.copy(content, tempFile, StandardCopyOption.REPLACE_EXISTING);
            long effectiveSize = copiedBytes > 0 ? copiedBytes : sizeBytes;

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .contentType(resolvedContentType)
                    .contentLength(effectiveSize)
                    .build();

            log.debug("PutObject bucket={} key={} sizeBytes={} contentType={}",
                    properties.bucket(), key, effectiveSize, resolvedContentType);
            PutObjectResponse response = putObjectWithBucketFallback(request, tempFile);
            return new VideoMetadata(
                    id,
                    filename,
                    resolvedContentType,
                    effectiveSize,
                    response.eTag(),
                    Instant.now());
        } catch (IOException | RuntimeException ex) {
            throw new VideoStorageException("Falha ao salvar o vídeo " + id.value() + " no bucket " + properties.bucket(), ex);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ex) {
                    log.debug("Não foi possível remover arquivo temporário {}: {}", tempFile, ex.getMessage());
                }
            }
        }
    }

    private PutObjectResponse putObjectWithBucketFallback(PutObjectRequest request, Path file) {
        try {
            return s3Client.putObject(request, RequestBody.fromFile(file));
        } catch (NoSuchBucketException ex) {
            log.info("Bucket '{}' não encontrado; criando automaticamente antes de salvar {}",
                    properties.bucket(), request.key());
            s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
            return s3Client.putObject(request, RequestBody.fromFile(file));
        }
    }

    private Optional<VideoSummary> toSummary(S3Object object) {
        String key = object.key();
        String name = key.substring(Math.min(key.length(), properties.keyPrefix().length()));
        if (name.isBlank() || name.endsWith("/")) {
            return Optional.empty();
        }
        try {
            return Optional.of(new VideoSummary(
                    VideoId.of(name),
                    object.size() == null ? 0L : object.size(),
                    object.lastModified()));
        } catch (InvalidVideoIdException ex) {
            log.debug("Ignorando objeto fora do padrão de id de vídeo: {}", key);
            return Optional.empty();
        }
    }

    private String keyOf(VideoId id) {
        return properties.keyPrefix() + id.value();
    }

    private static String filenameOf(String key) {
        int lastSlash = key.lastIndexOf('/');
        return lastSlash < 0 || lastSlash == key.length() - 1 ? key : key.substring(lastSlash + 1);
    }

    private static String resolveContentType(HeadObjectResponse head, String key) {
        String contentType = head.contentType();
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }
        return guessContentType(filenameOf(key));
    }

    private static String guessContentType(String filename) {
        int dot = filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "mp4", "m4v" -> "video/mp4";
            case "webm" -> "video/webm";
            case "mkv" -> "video/x-matroska";
            case "mov" -> "video/quicktime";
            case "m3u8" -> "application/vnd.apple.mpegurl";
            case "ts" -> "video/mp2t";
            default -> DEFAULT_CONTENT_TYPE;
        };
    }
}
