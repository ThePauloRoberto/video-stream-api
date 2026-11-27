package com.videostream.api.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Metadados de um vídeo armazenado. {@code filename} é usado no
 * {@code Content-Disposition} e {@code etag}/{@code lastModified} em cache condicional.
 */
public record VideoMetadata(
        VideoId id,
        String filename,
        String contentType,
        long sizeBytes,
        String etag,
        Instant lastModified) {

    public VideoMetadata {
        Objects.requireNonNull(id, "id não pode ser nulo");
        Objects.requireNonNull(contentType, "contentType não pode ser nulo");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes não pode ser negativo: " + sizeBytes);
        }
        filename = (filename == null || filename.isBlank()) ? id.value() : filename;
    }
}
