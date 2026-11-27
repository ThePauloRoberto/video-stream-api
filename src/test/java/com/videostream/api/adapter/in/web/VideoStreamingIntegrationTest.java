package com.videostream.api.adapter.in.web;

import com.videostream.api.support.InMemoryVideoStorageAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste ponta a ponta do protocolo HTTP (Tomcat + async streaming), sem S3 e sem Docker.
 * Valida status, cabeçalhos e bytes de um cliente HTTP real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(VideoStreamingIntegrationTest.StorageTestConfiguration.class)
@TestPropertySource(properties = {
        "video.streaming.chunk-size=1KB",
        "video.streaming.max-range-size=1KB"
})
class VideoStreamingIntegrationTest {

    private static final byte[] VIDEO = videoBytes(3_072);

    @LocalServerPort
    private int port;

    @Autowired
    private HttpClient httpClient;

    @Test
    @DisplayName("GET sem Range devolve 200 e o arquivo inteiro")
    void fullStream() throws Exception {
        HttpResponse<byte[]> response = send(request("/api/v1/videos/video-1/stream").GET());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Accept-Ranges")).contains("bytes");
        assertThat(response.headers().firstValue("Content-Length")).contains("3072");
        assertThat(response.body()).containsExactly(VIDEO);
    }

    @Test
    @DisplayName("Range aberto devolve 206 com 1 KB")
    void firstChunk() throws Exception {
        HttpResponse<byte[]> response = send(
                request("/api/v1/videos/video-1/stream").header("Range", "bytes=0-").GET());

        assertThat(response.statusCode()).isEqualTo(206);
        assertThat(response.headers().firstValue("Content-Range")).contains("bytes 0-1023/3072");
        assertThat(response.headers().firstValue("Content-Length")).contains("1024");
        assertThat(response.body()).containsExactly(Arrays.copyOfRange(VIDEO, 0, 1024));
    }

    @Test
    @DisplayName("Range no meio devolve exatamente os bytes pedidos")
    void middleChunk() throws Exception {
        HttpResponse<byte[]> response = send(
                request("/api/v1/videos/video-1/stream").header("Range", "bytes=2048-3071").GET());

        assertThat(response.statusCode()).isEqualTo(206);
        assertThat(response.headers().firstValue("Content-Range")).contains("bytes 2048-3071/3072");
        assertThat(response.body()).containsExactly(Arrays.copyOfRange(VIDEO, 2048, 3072));
    }

    @Test
    @DisplayName("Range insatisfazível devolve 416 com Content-Range")
    void unsatisfiableRange() throws Exception {
        HttpResponse<byte[]> response = send(
                request("/api/v1/videos/video-1/stream").header("Range", "bytes=99999-").GET());

        assertThat(response.statusCode()).isEqualTo(416);
        assertThat(response.headers().firstValue("Content-Range")).contains("bytes */3072");
    }

    @Test
    @DisplayName("vídeo inexistente devolve 404 no contrato de erro")
    void notFound() throws Exception {
        HttpResponse<byte[]> response = send(request("/api/v1/videos/nao-existe/stream").GET());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(new String(response.body())).contains("VIDEO_NOT_FOUND");
    }

    @Test
    @DisplayName("OpenAPI /v3/api-docs e Swagger UI expõem os endpoints de upload e streaming")
    void openApiAndSwaggerUiAreAvailable() throws Exception {
        HttpResponse<byte[]> apiDocs = send(request("/v3/api-docs").GET());
        assertThat(apiDocs.statusCode()).isEqualTo(200);
        String json = new String(apiDocs.body());
        assertThat(json)
                .contains("\"/api/videos/upload\"")
                .contains("\"multipart/form-data\"")
                .contains("\"/api/v1/videos/{videoId}/stream\"");

        HttpResponse<byte[]> swaggerUi = send(request("/swagger-ui/index.html").GET());
        assertThat(swaggerUi.statusCode()).isEqualTo(200);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:%d%s".formatted(port, path)))
                .timeout(Duration.ofSeconds(5));
    }

    private HttpResponse<byte[]> send(HttpRequest.Builder request) throws Exception {
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
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
        @Primary
        InMemoryVideoStorageAdapter inMemoryVideoStorageAdapter() {
            return new InMemoryVideoStorageAdapter().put("video-1", VIDEO, "video/mp4");
        }

        @Bean
        HttpClient httpClient() {
            return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        }
    }
}
