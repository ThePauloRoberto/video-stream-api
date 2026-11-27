package com.videostream.api.adapter.in.web;

import com.videostream.api.adapter.in.web.mapper.VideoWebMapper;
import com.videostream.api.config.CorsProperties;
import com.videostream.api.config.UseCaseConfiguration;
import com.videostream.api.config.VideoStreamingProperties;
import com.videostream.api.domain.model.ByteRange;
import com.videostream.api.domain.model.VideoId;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = VideoController.class)
@Import({UseCaseConfiguration.class, VideoWebMapper.class, VideoControllerTest.StorageTestConfiguration.class})
@EnableConfigurationProperties({VideoStreamingProperties.class, CorsProperties.class})
class VideoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryVideoStorageAdapter storage;

    @Test
    @DisplayName("POST /api/videos/upload salva o vídeo e devolve 201 Created com metadados")
    void uploadVideoSuccess() throws Exception {
        byte[] content = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "novo-video.mp4",
                "video/mp4",
                content);

        mockMvc.perform(multipart("/api/videos/upload").file(file))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/videos/novo-video.mp4/stream"))
                .andExpect(jsonPath("$.id").value("novo-video.mp4"))
                .andExpect(jsonPath("$.filename").value("novo-video.mp4"))
                .andExpect(jsonPath("$.contentType").value("video/mp4"))
                .andExpect(jsonPath("$.sizeBytes").value(content.length))
                .andExpect(jsonPath("$.streamUrl").value("/api/v1/videos/novo-video.mp4/stream"));

        try (InputStream saved = storage.openStream(VideoId.of("novo-video.mp4"), ByteRange.full(content.length))) {
            assertThat(saved.readAllBytes()).containsExactly(content);
        }
    }

    @Test
    @DisplayName("POST /api/videos/upload rejeita arquivo vazio com 400 Bad Request")
    void uploadEmptyFileReturnsBadRequest() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "vazio.mp4",
                "video/mp4",
                new byte[0]);

        mockMvc.perform(multipart("/api/videos/upload").file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    @DisplayName("POST /api/videos/upload rejeita nome com path traversal com 400 Bad Request")
    void uploadInvalidIdReturnsBadRequest() throws Exception {
        MockMultipartFile maliciousFile = new MockMultipartFile(
                "file",
                "../passwd",
                "video/mp4",
                new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/videos/upload").file(maliciousFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VIDEO_ID"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StorageTestConfiguration {

        @Bean
        InMemoryVideoStorageAdapter videoStoragePort() {
            return new InMemoryVideoStorageAdapter();
        }
    }
}
