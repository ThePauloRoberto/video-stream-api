package com.videostream.api.application.service;

import com.videostream.api.domain.exception.InvalidRangeException;
import com.videostream.api.domain.exception.InvalidVideoIdException;
import com.videostream.api.domain.exception.VideoNotFoundException;
import com.videostream.api.domain.model.VideoMetadata;
import com.videostream.api.domain.model.VideoStream;
import com.videostream.api.domain.model.VideoSummary;
import com.videostream.api.support.InMemoryVideoStorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoStreamingServiceTest {

    private static final int MAX_RANGE_BYTES = 1024;
    private static final byte[] CONTENT = content(5_000);

    private InMemoryVideoStorageAdapter storage;
    private VideoStreamingService service;

    @BeforeEach
    void setUp() {
        storage = new InMemoryVideoStorageAdapter().put("video-1", CONTENT, "video/mp4");
        service = new VideoStreamingService(storage, MAX_RANGE_BYTES);
    }

    @Test
    void semRangeAbreRepresentacaoCompleta() throws IOException {
        try (VideoStream stream = service.stream("video-1", null)) {
            assertThat(stream.range().isWholeRepresentation()).isTrue();
            assertThat(stream.contentLength()).isEqualTo(CONTENT.length);
            assertThat(stream.contentType()).isEqualTo("video/mp4");
            assertThat(stream.content().readAllBytes()).containsExactly(CONTENT);
        }
    }

    @Test
    void aplicaRangeELimitaAoChunkConfigurado() throws IOException {
        try (VideoStream stream = service.stream("video-1", "bytes=2048-")) {
            assertThat(stream.range().start()).isEqualTo(2048);
            assertThat(stream.range().endInclusive()).isEqualTo(2048 + MAX_RANGE_BYTES - 1);
            assertThat(stream.contentLength()).isEqualTo(MAX_RANGE_BYTES);
            assertThat(stream.content().readAllBytes())
                    .containsExactly(Arrays.copyOfRange(CONTENT, 2048, 2048 + MAX_RANGE_BYTES));
        }
    }

    @Test
    void repassaRangeSatisfazivelSemCorteQuandoCabeNoChunk() throws IOException {
        try (VideoStream stream = service.stream("video-1", "bytes=10-19")) {
            assertThat(stream.range().contentRange()).isEqualTo("bytes 10-19/5000");
            assertThat(stream.content().readAllBytes()).containsExactly(Arrays.copyOfRange(CONTENT, 10, 20));
        }
    }

    @Test
    void rangeNaoSatisfazivelViraInvalidRange() {
        assertThatThrownBy(() -> service.stream("video-1", "bytes=99999-"))
                .isInstanceOf(InvalidRangeException.class);
    }

    @Test
    void idInvalidoFalhaAntesDeChamarOStorage() {
        assertThatThrownBy(() -> service.stream("../secret", null))
                .isInstanceOf(InvalidVideoIdException.class);
    }

    @Test
    void videoInexistentePropagaNotFound() {
        assertThatThrownBy(() -> service.stream("nao-existe", null))
                .isInstanceOf(VideoNotFoundException.class)
                .hasMessageContaining("nao-existe");
    }

    @Test
    void metadadosIncluemTamanhoEEtag() {
        VideoMetadata metadata = service.getMetadata("video-1");

        assertThat(metadata.id().value()).isEqualTo("video-1");
        assertThat(metadata.sizeBytes()).isEqualTo(CONTENT.length);
        assertThat(metadata.etag()).isNotBlank();
        assertThat(metadata.filename()).isEqualTo("video-1.mp4");
    }

    @Test
    void catalogoRespeitaLimite() {
        storage.put("video-2", content(10), "video/mp4");

        List<VideoSummary> summaries = service.listVideos(1);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.getFirst().id().value()).isEqualTo("video-1");
    }

    @Test
    void limiteInvalidoNoCatalogo() {
        assertThatThrownBy(() -> service.listVideos(0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] content(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) (i % 251);
        }
        return bytes;
    }
}
