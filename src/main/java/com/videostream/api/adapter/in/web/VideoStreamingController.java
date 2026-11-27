package com.videostream.api.adapter.in.web;

import com.videostream.api.adapter.in.web.dto.ApiErrorResponse;
import com.videostream.api.adapter.in.web.dto.VideoMetadataResponse;
import com.videostream.api.adapter.in.web.dto.VideoSummaryResponse;
import com.videostream.api.adapter.in.web.mapper.VideoWebMapper;
import com.videostream.api.adapter.in.web.support.ChunkedStreamCopier;
import com.videostream.api.application.port.in.GetVideoMetadataUseCase;
import com.videostream.api.application.port.in.ListVideosUseCase;
import com.videostream.api.application.port.in.StreamVideoUseCase;
import com.videostream.api.domain.model.ByteRange;
import com.videostream.api.domain.model.VideoMetadata;
import com.videostream.api.domain.model.VideoStream;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Adaptador de entrada HTTP.
 *
 * <p>Responsabilidades: validar parâmetros, traduzir HTTP Range em uma chamada ao caso
 * de uso e montar os cabeçalhos de uma resposta que suporta streaming parcial
 * ({@code 200} / {@code 206} / {@code 416}).
 *
 * <p>A validação de parâmetros usa o suporte nativo do Spring MVC 6.1+ (sem
 * {@code @Validated} na classe, que criaria um proxy AOP e mudaria o tipo de exceção).
 */
@RestController
@RequestMapping("/api/v1/videos")
@Tag(name = "2. Catálogo e Streaming de Vídeos", description = "Endpoints para listagem do catálogo, consulta de metadados e streaming parcial com HTTP Range Requests (chunks de 1 MB)")
public class VideoStreamingController {

    private static final Logger log = LoggerFactory.getLogger(VideoStreamingController.class);

    private static final String BYTES_UNIT = "bytes";
    private static final int DEFAULT_CATALOG_LIMIT = 50;
    private static final int MAX_CATALOG_LIMIT = 100;

    private final StreamVideoUseCase streamVideoUseCase;
    private final GetVideoMetadataUseCase getVideoMetadataUseCase;
    private final ListVideosUseCase listVideosUseCase;
    private final VideoWebMapper mapper;
    private final ChunkedStreamCopier copier;

    public VideoStreamingController(StreamVideoUseCase streamVideoUseCase,
                                    GetVideoMetadataUseCase getVideoMetadataUseCase,
                                    ListVideosUseCase listVideosUseCase,
                                    VideoWebMapper mapper,
                                    ChunkedStreamCopier copier) {
        this.streamVideoUseCase = streamVideoUseCase;
        this.getVideoMetadataUseCase = getVideoMetadataUseCase;
        this.listVideosUseCase = listVideosUseCase;
        this.mapper = mapper;
        this.copier = copier;
    }

    @Operation(
            summary = "Lista o catálogo de vídeos disponíveis",
            description = "Consulta o bucket S3 (ListObjectsV2) e retorna os vídeos armazenados respeitando o limite informado.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Catálogo retornado com sucesso",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = VideoSummaryResponse.class)))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Parâmetro limit fora do intervalo permitido (1 a 100)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "502",
                    description = "Falha ao consultar o bucket S3",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<VideoSummaryResponse> list(
            @Parameter(description = "Quantidade máxima de vídeos a listar (entre 1 e 100)", example = "50")
            @RequestParam(name = "limit", defaultValue = "" + DEFAULT_CATALOG_LIMIT)
            @Min(1) @Max(MAX_CATALOG_LIMIT) int limit) {
        return mapper.toResponse(listVideosUseCase.listVideos(limit));
    }

    @Operation(
            summary = "Consulta os metadados de um vídeo",
            description = "Executa um HeadObject no S3 para obter tamanho, Content-Type, ETag, Last-Modified e a URL de streaming sem transferir os bytes do vídeo.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Metadados do vídeo encontrados",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = VideoMetadataResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Identificador de vídeo com formato inválido",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Vídeo não encontrado no bucket",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "502",
                    description = "Falha ao consultar o S3",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping(value = "/{videoId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public VideoMetadataResponse metadata(
            @Parameter(description = "Identificador lógico do vídeo (ex.: demo.mp4)", example = "demo.mp4", required = true)
            @PathVariable("videoId") String videoId) {
        return mapper.toResponse(getVideoMetadataUseCase.getMetadata(videoId));
    }

    /**
     * Entrega o vídeo (inteiro ou em faixas de no máximo {@code video.streaming.max-range-size}).
     *
     * <ul>
     *   <li>sem {@code Range} => {@code 200} com a representação inteira;</li>
     *   <li>com {@code Range} satisfazível => {@code 206} + {@code Content-Range};</li>
     *   <li>com {@code Range} inválido => {@code 416} + {@code Content-Range} com o tamanho total.</li>
     * </ul>
     */
    @Operation(
            summary = "Streaming de vídeo com suporte a HTTP Range Requests (chunks de 1 MB)",
            description = """
                    Entrega o fluxo binário do vídeo diretamente do S3 em buffers de 1 MB:
                    - **Com cabeçalho `Range`** (ex.: `bytes=0-` ou `bytes=0-1048575`): retorna **HTTP 206 Partial Content** limitado ao chunk máximo configurado (1 MB por padrão) e o cabeçalho `Content-Range`.
                    - **Sem cabeçalho `Range`**: retorna **HTTP 200 OK** com o arquivo completo.
                    - **Com `Range` fora do tamanho do arquivo**: retorna **HTTP 416 Range Not Satisfiable**.""")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Streaming completo do vídeo (quando nenhum Range parcial é solicitado)",
                    headers = {
                            @Header(name = HttpHeaders.ACCEPT_RANGES, description = "Unidade aceita (bytes)", schema = @Schema(type = "string", example = "bytes")),
                            @Header(name = HttpHeaders.CONTENT_LENGTH, description = "Tamanho em bytes entregue na resposta", schema = @Schema(type = "integer", example = "3145728")),
                            @Header(name = HttpHeaders.ETAG, description = "ETag do objeto no S3", schema = @Schema(type = "string"))
                    },
                    content = @Content(mediaType = "video/mp4", schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(
                    responseCode = "206",
                    description = "Trecho parcial (chunk) do vídeo entregue com sucesso",
                    headers = {
                            @Header(name = HttpHeaders.ACCEPT_RANGES, description = "Unidade aceita (bytes)", schema = @Schema(type = "string", example = "bytes")),
                            @Header(name = HttpHeaders.CONTENT_RANGE, description = "Faixa de bytes entregue e tamanho total (bytes inicio-fim/total)", schema = @Schema(type = "string", example = "bytes 0-1048575/3145728")),
                            @Header(name = HttpHeaders.CONTENT_LENGTH, description = "Tamanho do chunk entregue em bytes", schema = @Schema(type = "integer", example = "1048576"))
                    },
                    content = @Content(mediaType = "video/mp4", schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Identificador de vídeo inválido",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Vídeo não encontrado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "416",
                    description = "Faixa de bytes solicitada no Range não é satisfazível para o tamanho do vídeo",
                    headers = @Header(name = HttpHeaders.CONTENT_RANGE, description = "Tamanho total do recurso (bytes */total)", schema = @Schema(type = "string", example = "bytes */3145728")),
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{videoId}/stream")
    public ResponseEntity<StreamingResponseBody> stream(
            @Parameter(description = "Identificador lógico do vídeo (ex.: demo.mp4)", example = "demo.mp4", required = true)
            @PathVariable("videoId") String videoId,
            @Parameter(description = "Faixa de bytes HTTP Range (ex.: bytes=0- para o 1º chunk de 1 MB, bytes=1048576-2097151, ou bytes=-102400)", example = "bytes=0-")
            @RequestHeader(name = HttpHeaders.RANGE, required = false) String rangeHeader,
            @Parameter(description = "Validador condicional If-Range (ETag forte ou data RFC 1123)")
            @RequestHeader(name = HttpHeaders.IF_RANGE, required = false) String ifRange) {

        String effectiveRange = resolveRange(videoId, rangeHeader, ifRange);
        VideoStream stream = streamVideoUseCase.stream(videoId, effectiveRange);

        try {
            return buildResponse(stream);
        } catch (RuntimeException ex) {
            closeQuietly(stream);
            throw ex;
        }
    }

    /**
     * Se o cliente enviou {@code If-Range} e o validador não confere, o Range deve ser
     * ignorado e a representação completa devolvida (RFC 9110, 13.1.5).
     */
    private String resolveRange(String videoId, String rangeHeader, String ifRange) {
        if (rangeHeader == null || rangeHeader.isBlank() || ifRange == null || ifRange.isBlank()) {
            return rangeHeader;
        }
        VideoMetadata metadata = getVideoMetadataUseCase.getMetadata(videoId);
        if (matchesIfRange(ifRange, metadata)) {
            return rangeHeader;
        }
        log.debug("If-Range '{}' não corresponde ao recurso {}; ignorando o Range", ifRange, videoId);
        return null;
    }

    private static boolean matchesIfRange(String ifRange, VideoMetadata metadata) {
        String candidate = ifRange.trim();
        if (candidate.startsWith("W/")) {
            // ETag fraco não é validador válido para If-Range.
            return false;
        }
        if (candidate.startsWith("\"")) {
            return metadata.etag() != null && candidate.equals(metadata.etag());
        }
        try {
            Instant ifRangeDate = ZonedDateTime.parse(candidate, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return metadata.lastModified() != null && !metadata.lastModified().isAfter(ifRangeDate);
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private ResponseEntity<StreamingResponseBody> buildResponse(VideoStream stream) {
        ByteRange range = stream.range();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT_RANGES, BYTES_UNIT);
        headers.setContentType(parseContentType(stream.contentType()));
        headers.setContentLength(stream.contentLength());
        if (stream.etag() != null) {
            headers.setETag(stream.etag());
        }
        if (stream.lastModified() != null) {
            headers.setLastModified(stream.lastModified().toEpochMilli());
        }
        headers.setContentDisposition(contentDisposition(stream.filename()));
        if (range.isPartial()) {
            headers.set(HttpHeaders.CONTENT_RANGE, range.contentRange());
        }

        HttpStatus status = range.isPartial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK;
        StreamingResponseBody body = outputStream -> writeBody(stream, outputStream);
        return new ResponseEntity<>(body, headers, status);
    }

    private void writeBody(VideoStream stream, OutputStream outputStream) throws IOException {
        try (stream) {
            copier.copy(stream.content(), outputStream);
        } catch (IOException ex) {
            if (isClientDisconnected(ex)) {
                log.debug("Cliente desconectou durante o streaming do vídeo {}: {}", stream.id(), ex.getMessage());
                return;
            }
            throw ex;
        }
    }

    private static MediaType parseContentType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /**
     * Nomes ASCII saem como {@code filename="demo.mp4"}; nomes com acento usam RFC 5987
     * ({@code filename*=UTF-8''...}), que é o formato entendido pelos browsers.
     */
    private static ContentDisposition contentDisposition(String filename) {
        ContentDisposition.Builder builder = ContentDisposition.inline();
        return filename.chars().allMatch(character -> character < 0x80)
                ? builder.filename(filename).build()
                : builder.filename(filename, StandardCharsets.UTF_8).build();
    }

    private static boolean isClientDisconnected(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            String simpleName = current.getClass().getSimpleName();
            if (simpleName.contains("ClientAbort") || simpleName.contains("AsyncRequestNotUsable")) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("broken pipe")
                        || normalized.contains("connection reset")
                        || normalized.contains("connection was aborted")
                        || normalized.contains("an established connection")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void closeQuietly(VideoStream stream) {
        try {
            stream.close();
        } catch (IOException ex) {
            log.debug("Falha ao fechar stream do vídeo {}: {}", stream.id(), ex.getMessage());
        }
    }
}
