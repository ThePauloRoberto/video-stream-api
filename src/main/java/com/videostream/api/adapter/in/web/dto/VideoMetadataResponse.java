package com.videostream.api.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * DTO de saída com metadados do vídeo (nunca expõe a entidade de domínio ou a chave do bucket).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Metadados completos de um vídeo armazenado no S3")
public record VideoMetadataResponse(
        @Schema(description = "Identificador lógico do vídeo", example = "demo.mp4")
        String id,
        @Schema(description = "Nome do arquivo utilizado no Content-Disposition", example = "demo.mp4")
        String filename,
        @Schema(description = "Tipo MIME do conteúdo do vídeo", example = "video/mp4")
        String contentType,
        @Schema(description = "Tamanho total do vídeo em bytes", example = "3145728")
        long sizeBytes,
        @Schema(description = "ETag (hash) do objeto no S3", example = "\"9b2cf535f27731c974343645a3985328\"")
        String etag,
        @Schema(description = "Data/hora da última modificação no S3", example = "2026-11-27T02:37:30Z")
        Instant lastModified,
        @Schema(description = "Caminho relativo para iniciar o streaming do vídeo", example = "/api/v1/videos/demo.mp4/stream")
        String streamUrl) {
}
