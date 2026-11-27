package com.videostream.api.adapter.in.web.exception;

import com.videostream.api.adapter.in.web.dto.ApiErrorResponse;
import com.videostream.api.adapter.in.web.dto.FieldValidationError;
import com.videostream.api.adapter.in.web.filter.CorrelationIdFilter;
import com.videostream.api.domain.exception.InvalidRangeException;
import com.videostream.api.domain.exception.InvalidVideoIdException;
import com.videostream.api.domain.exception.VideoNotFoundException;
import com.videostream.api.domain.exception.VideoStorageException;
import com.videostream.api.domain.model.ByteRange;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.validation.method.ParameterValidationResult;

import java.time.Instant;
import java.util.List;

/**
 * Tratamento global de exceções: traduz exceções de domínio/infra em um contrato único
 * de erro, sem vazar stack trace, nome de bucket ou credenciais.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(VideoNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleVideoNotFound(VideoNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error(HttpStatus.NOT_FOUND, "VIDEO_NOT_FOUND", ex.getMessage(), request, List.of()));
    }

    @ExceptionHandler(InvalidVideoIdException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidVideoId(InvalidVideoIdException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_ID", ex.getMessage(), request, List.of()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return badRequest("INVALID_ARGUMENT", ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingPart(MissingServletRequestPartException ex,
                                                              HttpServletRequest request) {
        List<FieldValidationError> fieldErrors = List.of(
                new FieldValidationError(ex.getRequestPartName(), ex.getMessage()));
        return badRequest("VALIDATION_ERROR", "Parte obrigatória ausente no upload multipart.", request, fieldErrors);
    }

    @ExceptionHandler(InvalidRangeException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRange(InvalidRangeException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, ByteRange.unsatisfiedContentRange(ex.getTotalSize()))
                .body(error(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "INVALID_RANGE",
                        ex.getMessage(), request, List.of()));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(HandlerMethodValidationException ex,
                                                                   HttpServletRequest request) {
        List<FieldValidationError> fieldErrors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(resolvableError -> new FieldValidationError(
                                parameterName(result),
                                resolvableError.getDefaultMessage())))
                .toList();
        return badRequest("VALIDATION_ERROR", "Parâmetros inválidos na requisição.", request, fieldErrors);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex,
                                                                 HttpServletRequest request) {
        List<FieldValidationError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldValidationError)
                .toList();
        return badRequest("VALIDATION_ERROR", "Dados inválidos na requisição.", request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                      HttpServletRequest request) {
        List<FieldValidationError> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldValidationError(
                        normalizeFieldName(violation.getPropertyPath() == null
                                ? null
                                : violation.getPropertyPath().toString()),
                        violation.getMessage()))
                .toList();
        return badRequest("VALIDATION_ERROR", "Parâmetros inválidos na requisição.", request, fieldErrors);
    }

    @ExceptionHandler(VideoStorageException.class)
    public ResponseEntity<ApiErrorResponse> handleStorageFailure(VideoStorageException ex, HttpServletRequest request) {
        log.error("Falha de storage ao atender {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(error(HttpStatus.BAD_GATEWAY, "STORAGE_FAILURE",
                        "Não foi possível acessar o armazenamento do vídeo.", request, List.of()));
    }

    /**
     * Cliente fechou a conexão no meio do streaming: não há o que responder e não é erro de servidor.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientAbort(AsyncRequestNotUsableException ex) {
        log.debug("Requisição assíncrona encerrada pelo cliente: {}", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Erro inesperado ao atender {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                        "Erro inesperado ao processar a requisição.", request, List.of()));
    }

    private FieldValidationError toFieldValidationError(FieldError fieldError) {
        return new FieldValidationError(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private static String parameterName(ParameterValidationResult result) {
        return normalizeFieldName(result.getMethodParameter().getParameterName());
    }

    /**
     * Em validação de métodos o caminho costuma vir prefixado (ex.: {@code list.limit});
     * a API expõe somente o nome do campo.
     */
    private static String normalizeFieldName(String name) {
        if (name == null || name.isBlank()) {
            return "parameter";
        }
        int lastDot = name.lastIndexOf('.');
        return lastDot >= 0 && lastDot < name.length() - 1 ? name.substring(lastDot + 1) : name;
    }

    private ResponseEntity<ApiErrorResponse> badRequest(String code, String message,
                                                        HttpServletRequest request,
                                                        List<FieldValidationError> fieldErrors) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error(HttpStatus.BAD_REQUEST, code, message, request, fieldErrors));
    }

    private static ApiErrorResponse error(HttpStatus status, String code, String message,
                                          HttpServletRequest request,
                                          List<FieldValidationError> fieldErrors) {
        return new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                MDC.get(CorrelationIdFilter.MDC_KEY),
                fieldErrors);
    }
}
