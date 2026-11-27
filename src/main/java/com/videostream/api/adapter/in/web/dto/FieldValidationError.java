package com.videostream.api.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Erro de validação de um campo/parâmetro específico.
 */
@Schema(description = "Detalhe de erro de validação de um parâmetro ou campo")
public record FieldValidationError(
        @Schema(description = "Nome do campo ou parâmetro inválido", example = "limit")
        String field,
        @Schema(description = "Mensagem de validação", example = "deve ser maior ou igual a 1")
        String message) {
}
