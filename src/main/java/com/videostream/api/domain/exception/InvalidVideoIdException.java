package com.videostream.api.domain.exception;

/**
 * Identificador de vídeo fora do formato aceito. Mapeada para HTTP 400.
 */
public class InvalidVideoIdException extends RuntimeException {

    private final String videoId;

    public InvalidVideoIdException(String videoId) {
        super("Identificador de vídeo inválido: '%s'".formatted(videoId));
        this.videoId = videoId;
    }

    public String getVideoId() {
        return videoId;
    }
}
