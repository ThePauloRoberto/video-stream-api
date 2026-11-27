package com.videostream.api.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Contrato único de erro da API (application/problem+json não é usado para manter
 * o payload explícito e estável para os consumidores).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Contrato padronizado de erro da API")
public record ApiErrorResponse(
        @Schema(description = "Instante em que o erro ocorreu", example = "2026-11-27T02:40:00Z")
        Instant timestamp,
        @Schema(description = "Código de status HTTP", example = "400")
        int status,
        @Schema(description = "Razão do status HTTP", example = "Bad Request")
        String error,
        @Schema(description = "Código semântico do erro para tratamento pelo cliente", example = "INVALID_VIDEO_ID")
        String code,
        @Schema(description = "Mensagem amigável descrevendo o erro", example = "Identificador de vídeo inválido: '../passwd'")
        String message,
        @Schema(description = "Caminho da requisição que gerou o erro", example = "/api/videos/upload")
        String path,
        @Schema(description = "Identificador de correlação da requisição (X-Request-Id)", example = "954353eb-1d1a-4ba5-9f93-730976948b59")
        String correlationId,
        @Schema(description = "Lista de erros de validação de campos, quando aplicável")
        List<FieldValidationError> fieldErrors) {
}
