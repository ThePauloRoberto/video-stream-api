package com.videostream.api.domain.exception;

/**
 * Vídeo inexistente no storage. Mapeada para HTTP 404 pelo adaptador web.
 */
public class VideoNotFoundException extends RuntimeException {

    private final String videoId;

    public VideoNotFoundException(String videoId) {
        super("Vídeo não encontrado: " + videoId);
        this.videoId = videoId;
    }

    public String getVideoId() {
        return videoId;
    }
}
