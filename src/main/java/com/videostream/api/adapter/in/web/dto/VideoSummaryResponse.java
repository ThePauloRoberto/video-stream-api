package com.videostream.api.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * DTO de saída para itens do catálogo.
 */
@Schema(description = "Resumo de um vídeo listado no catálogo")
public record VideoSummaryResponse(
        @Schema(description = "Identificador lógico do vídeo", example = "demo.mp4")
        String id,
        @Schema(description = "Tamanho total do vídeo em bytes", example = "3145728")
        long sizeBytes,
        @Schema(description = "Data/hora da última modificação no S3", example = "2026-11-27T02:37:30Z")
        Instant lastModified) {
}
