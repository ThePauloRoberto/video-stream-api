package com.videostream.api.domain.model;

import com.videostream.api.domain.exception.InvalidVideoIdException;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Identificador de um vídeo. É a chave lógica usada pela API; o adaptador de storage
 * é quem sabe transformá-la na chave física do bucket.
 *
 * <p>A validação aqui é proposital: impede path traversal ({@code ../}) e caracteres
 * que quebrariam a composição da chave do objeto no S3.
 */
public record VideoId(String value) {

    public static final String PATTERN = "^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$";

    private static final Pattern VALID = Pattern.compile(PATTERN);

    public VideoId {
        Objects.requireNonNull(value, "videoId não pode ser nulo");
        String normalized = value.trim();
        if (!VALID.matcher(normalized).matches()) {
            throw new InvalidVideoIdException(value);
        }
        value = normalized;
    }

    public static VideoId of(String value) {
        return new VideoId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
