package com.videostream.api.application.service;

import com.videostream.api.application.port.in.UploadVideoUseCase;
import com.videostream.api.domain.model.VideoId;
import com.videostream.api.domain.model.VideoMetadata;
import com.videostream.api.domain.port.VideoStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Objects;

/**
 * Serviço de aplicação responsável pelo caso de uso de upload de vídeos.
 *
 * <p>Seguindo Clean Architecture e Arquitetura Hexagonal, não possui dependências de
 * frameworks web (como {@code MultipartFile}) nem do AWS SDK: conversa exclusivamente
 * com o domínio e com a porta de saída {@link VideoStoragePort}.
 */
public class VideoService implements UploadVideoUseCase {

    private static final Logger log = LoggerFactory.getLogger(VideoService.class);

    private final VideoStoragePort storage;

    public VideoService(VideoStoragePort storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public VideoMetadata upload(String videoId, InputStream content, long sizeBytes, String contentType) {
        Objects.requireNonNull(content, "content não pode ser nulo");
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("O arquivo de vídeo não pode estar vazio (sizeBytes <= 0)");
        }

        VideoId id = VideoId.of(videoId == null ? "" : videoId);
        log.info("Iniciando upload do vídeo id={} sizeBytes={} contentType={}", id, sizeBytes, contentType);

        VideoMetadata metadata = storage.save(id, content, sizeBytes, contentType);
        log.info("Upload concluído com sucesso para o vídeo id={} etag={}", metadata.id(), metadata.etag());
        return metadata;
    }
}
