package com.videostream.api.domain.model;

import com.videostream.api.domain.exception.InvalidVideoIdException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoIdTest {

    @ParameterizedTest
    @ValueSource(strings = {"abc", "video-1", "video_1", "a.b.c", "ABC123", "  abc  "})
    void aceitaIdsValidos(String value) {
        assertThat(VideoId.of(value).value()).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {"../etc/passwd", "a/b", "a b", "", "  ", "-abc", "id?x=1"})
    void rejeitaIdsInvalidos(String value) {
        assertThatThrownBy(() -> VideoId.of(value))
                .isInstanceOf(InvalidVideoIdException.class);
    }

    @Test
    void rejeitaIdLongoDemais() {
        assertThatThrownBy(() -> VideoId.of("a".repeat(129)))
                .isInstanceOf(InvalidVideoIdException.class);
    }

    @Test
    void normalizaEspacos() {
        assertThat(VideoId.of("  film.mp4 ").value()).isEqualTo("film.mp4");
    }

    @Test
    void nuloEhRejeitado() {
        assertThatThrownBy(() -> new VideoId(null)).isInstanceOf(NullPointerException.class);
    }
}
