package com.videostream.api.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Item de catálogo: dados mínimos para o cliente descobrir vídeos disponíveis.
 */
public record VideoSummary(VideoId id, long sizeBytes, Instant lastModified) {

    public VideoSummary {
        Objects.requireNonNull(id, "id não pode ser nulo");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes não pode ser negativo: " + sizeBytes);
        }
    }
}
