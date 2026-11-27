package com.videostream.api.domain.exception;

/**
 * Range sintaticamente válido, porém não satisfazível para o tamanho do recurso.
 * Mapeada para HTTP 416 com {@code Content-Range: bytes * /tamanho}.
 */
public class InvalidRangeException extends RuntimeException {

    private final long totalSize;

    public InvalidRangeException(String rangeHeader, long totalSize) {
        super("Intervalo de bytes inválido ou não satisfazível: '%s' (recurso com %d bytes)"
                .formatted(rangeHeader, totalSize));
        this.totalSize = totalSize;
    }

    public long getTotalSize() {
        return totalSize;
    }
}
