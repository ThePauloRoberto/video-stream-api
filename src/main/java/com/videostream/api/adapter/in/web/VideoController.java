package com.videostream.api.adapter.in.web;

import com.videostream.api.adapter.in.web.dto.ApiErrorResponse;
import com.videostream.api.adapter.in.web.dto.VideoMetadataResponse;
import com.videostream.api.adapter.in.web.mapper.VideoWebMapper;
import com.videostream.api.application.port.in.UploadVideoUseCase;
import com.videostream.api.domain.exception.VideoStorageException;
import com.videostream.api.domain.model.VideoMetadata;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * Adaptador de entrada HTTP para upload de vídeos.
 *
 * <p>Converte o {@link MultipartFile} do Spring MVC em tipos puros ({@code InputStream},
 * tamanho e MIME type) antes de acionar a porta de entrada {@link UploadVideoUseCase}.
 */
@RestController
@Tag(name = "1. Upload de Vídeos", description = "Endpoint para envio (upload multipart) de arquivos de vídeo para o S3 / LocalStack")
public class VideoController {

    private final UploadVideoUseCase uploadVideoUseCase;
    private final VideoWebMapper mapper;

    public VideoController(UploadVideoUseCase uploadVideoUseCase, VideoWebMapper mapper) {
        this.uploadVideoUseCase = uploadVideoUseCase;
        this.mapper = mapper;
    }

    @Operation(
            summary = "Faz upload de um arquivo de vídeo para o S3 (LocalStack)",
            description = """
                    Recebe um arquivo de vídeo via `multipart/form-data` e o salva no bucket S3 configurado.
                    
                    No Swagger UI, clique em **Try it out**, escolha o arquivo no campo `file` e clique em **Execute**. \
                    Se o parâmetro opcional `id` não for preenchido, será utilizado o nome original do arquivo enviado.""")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Vídeo salvo com sucesso no bucket S3",
                    headers = @Header(
                            name = HttpHeaders.LOCATION,
                            description = "URL do endpoint de streaming para o vídeo enviado",
                            schema = @Schema(type = "string", example = "/api/v1/videos/demo.mp4/stream")),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = VideoMetadataResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Arquivo vazio, ausente ou identificador/nome de arquivo inválido",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "502",
                    description = "Falha de comunicação com o armazenamento S3 / LocalStack",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping(
            value = "/api/videos/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VideoMetadataResponse> upload(
            @Parameter(
                    description = "Arquivo de vídeo a ser enviado (ex.: .mp4, .webm, .mkv)",
                    required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(
                    description = "Identificador/nome opcional para o vídeo no S3 (ex.: meu-video.mp4). Se vazio, usa o nome original do arquivo.",
                    example = "demo.mp4")
            @RequestParam(name = "id", required = false) String customId) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("O arquivo enviado não pode estar vazio.");
        }

        String targetVideoId = (customId != null && !customId.isBlank())
                ? customId.trim()
                : normalizeOriginalFilename(file.getOriginalFilename());

        try (InputStream content = file.getInputStream()) {
            VideoMetadata metadata = uploadVideoUseCase.upload(
                    targetVideoId,
                    content,
                    file.getSize(),
                    file.getContentType());
            VideoMetadataResponse response = mapper.toResponse(metadata);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .location(URI.create(response.streamUrl()))
                    .body(response);
        } catch (IOException ex) {
            throw new VideoStorageException("Falha ao ler os bytes do arquivo enviado no upload.", ex);
        }
    }

    /**
     * Substitui espaços em nomes de arquivos comuns do sistema operacional por hífen,
     * mas preserva tentativas de path traversal ({@code ..}, {@code /}, {@code \})
     * para que o domínio as rejeite com {@code InvalidVideoIdException}.
     */
    private static String normalizeOriginalFilename(String filename) {
        if (filename == null) {
            return "";
        }
        String trimmed = filename.trim();
        if (trimmed.contains("..") || trimmed.contains("/") || trimmed.contains("\\")) {
            return trimmed;
        }
        return trimmed.replaceAll("\\s+", "-");
    }
}
