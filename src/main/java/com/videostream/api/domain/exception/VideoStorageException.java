package com.videostream.api.domain.exception;

/**
 * Falha de infraestrutura ao conversar com o storage (indisponibilidade, timeout,
 * credencial inválida, erro 5xx do provedor). Mapeada para HTTP 502.
 *
 * <p>Não expõe detalhes internos na resposta HTTP; o detalhe fica no log.
 */
public class VideoStorageException extends RuntimeException {

    public VideoStorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public VideoStorageException(String message) {
        super(message);
    }
}
