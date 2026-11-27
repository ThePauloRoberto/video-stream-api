package com.videostream.api.adapter.in.web;

import com.videostream.api.adapter.in.web.mapper.VideoWebMapper;
import com.videostream.api.adapter.in.web.support.ChunkedStreamCopier;
import com.videostream.api.application.service.VideoStreamingService;
import com.videostream.api.config.CorsProperties;
import com.videostream.api.config.UseCaseConfiguration;
import com.videostream.api.config.VideoStreamingProperties;
import com.videostream.api.support.InMemoryVideoStorageAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = VideoStreamingController.class)
@Import({UseCaseConfiguration.class, VideoWebMapper.class, ChunkedStreamCopier.class,
        VideoStreamingControllerTest.StorageTestConfiguration.class})
@EnableConfigurationProperties({VideoStreamingProperties.class, CorsProperties.class})
@TestPropertySource(properties = {
        "video.streaming.chunk-size=256B",
        "video.streaming.max-range-size=1KB"
})
class VideoStreamingControllerTest {

    private static final byte[] VIDEO = videoBytes(3_072);

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /videos/{id} devolve metadados em DTO")
    void metadataEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/videos/video-1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.id").value("video-1"))
                .andExpect(jsonPath("$.sizeBytes").value(VIDEO.length))
                .andExpect(jsonPath("$.contentType").value("video/mp4"))
                .andExpect(jsonPath("$.streamUrl").value("/api/v1/videos/video-1/stream"));
    }

    @Test
    @DisplayName("GET /videos lista o catálogo")
    void catalogEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/videos").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("video-1"));
    }

    @Test
    @DisplayName("sem Range: 200 com o arquivo inteiro")
    void fullStreamWithoutRange() throws Exception {
        MvcResult result = dispatch(get("/api/v1/videos/video-1/stream"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo("3072");
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("inline; filename=\"video-1.mp4\"");
        assertThat(result.getResponse().getContentAsByteArray()).containsExactly(VIDEO);
    }

    @Test
    @DisplayName("If-Range com ETag correspondente mantém a resposta 206")
    void ifRangeMatchingEtag() throws Exception {
        MvcResult result = dispatch(get("/api/v1/videos/video-1/stream")
                .header(HttpHeaders.RANGE, "bytes=0-1023")
                .header(HttpHeaders.IF_RANGE, etag()));

        assertThat(result.getResponse().getStatus()).isEqualTo(206);
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 0-1023/3072");
    }

    @Test
    @DisplayName("If-Range divergente ignora o Range e devolve 200")
    void ifRangeMismatch() throws Exception {
        MvcResult result = dispatch(get("/api/v1/videos/video-1/stream")
                .header(HttpHeaders.RANGE, "bytes=0-1023")
                .header(HttpHeaders.IF_RANGE, "\"etag-antigo\""));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsByteArray()).containsExactly(VIDEO);
    }

    @Test
    @DisplayName("If-Range com data posterior ao Last-Modified mantém a resposta 206")
    void ifRangeWithDate() throws Exception {
        MvcResult result = dispatch(get("/api/v1/videos/video-1/stream")
                .header(HttpHeaders.RANGE, "bytes=0-1023")
                .header(HttpHeaders.IF_RANGE, "Fri, 01 Jan 2027 00:00:00 GMT"));

        assertThat(result.getResponse().getStatus()).isEqualTo(206);
    }

    @Test
    @DisplayName("Range aberto: 206 limitado a 1 KB (chunk configurado)")
    void rangeRequestReturnsFirstChunk() throws Exception {
        MvcResult result = dispatch(get("/api/v1/videos/video-1/stream")
                .header(HttpHeaders.RANGE, "bytes=0-"));

        assertThat(result.getResponse().getStatus()).isEqualTo(206);
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 0-1023/3072");
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo("1024");
        assertThat(result.getResponse().getContentAsByteArray())
                .containsExactly(Arrays.copyOfRange(VIDEO, 0, 1024));
    }

    @Test
    @DisplayName("Range no meio do arquivo: 206 com os bytes corretos")
    void rangeRequestReturnsRequestedSlice() throws Exception {
        MvcResult result = dispatch(get("/api/v1/videos/video-1/stream")
                .header(HttpHeaders.RANGE, "bytes=1024-2047"));

        assertThat(result.getResponse().getStatus()).isEqualTo(206);
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 1024-2047/3072");
        assertThat(result.getResponse().getContentAsByteArray())
                .containsExactly(Arrays.copyOfRange(VIDEO, 1024, 2048));
    }

    @Test
    @DisplayName("Range de sufixo: últimos 100 bytes")
    void suffixRange() throws Exception {
        MvcResult result = dispatch(get("/api/v1/videos/video-1/stream")
                .header(HttpHeaders.RANGE, "bytes=-100"));

        assertThat(result.getResponse().getStatus()).isEqualTo(206);
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 2972-3071/3072");
        assertThat(result.getResponse().getContentAsByteArray())
                .containsExactly(Arrays.copyOfRange(VIDEO, 2972, 3072));
    }

    @Test
    @DisplayName("Range não satisfazível: 416 com Content-Range bytes */tamanho")
    void unsatisfiableRange() throws Exception {
        mockMvc.perform(get("/api/v1/videos/video-1/stream")
                        .header(HttpHeaders.RANGE, "bytes=999999-"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */3072"))
                .andExpect(jsonPath("$.code").value("INVALID_RANGE"));
    }

    @Test
    @DisplayName("vídeo inexistente: 404 com contrato de erro")
    void videoNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/videos/nao-existe/stream"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VIDEO_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/videos/nao-existe/stream"));
    }

    @Test
    @DisplayName("id inválido: 400 antes de tocar no storage")
    void invalidVideoId() throws Exception {
        mockMvc.perform(get("/api/v1/videos/" + "a".repeat(200) + "/stream"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VIDEO_ID"));
    }

    @Test
    @DisplayName("parâmetro limit inválido: 400 com erro de validação")
    void invalidLimit() throws Exception {
        mockMvc.perform(get("/api/v1/videos").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("limit"));
    }

    @Test
    @DisplayName("correlation id é ecoado e aparece no erro")
    void correlationIdIsPropagated() throws Exception {
        mockMvc.perform(get("/api/v1/videos/nao-existe")
                        .header("X-Request-Id", "req-123"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", "req-123"))
                .andExpect(jsonPath("$.correlationId").value("req-123"));
    }

    private MvcResult dispatch(MockHttpServletRequestBuilder requestBuilder) throws Exception {
        MvcResult result = mockMvc.perform(requestBuilder)
                .andExpect(request().asyncStarted())
                .andReturn();
        result.getAsyncResult(5_000);
        return mockMvc.perform(asyncDispatch(result)).andReturn();
    }

    private static String etag() {
        return "\"" + Integer.toHexString(Arrays.hashCode(VIDEO)) + "\"";
    }

    private static byte[] videoBytes(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) (i % 251);
        }
        return bytes;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StorageTestConfiguration {

        @Bean
        InMemoryVideoStorageAdapter videoStoragePort() {
            return new InMemoryVideoStorageAdapter().put("video-1", VIDEO, "video/mp4");
        }
    }
}
