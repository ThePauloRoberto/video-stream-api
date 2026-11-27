package com.videostream.api.adapter.out.s3;

import com.videostream.api.domain.exception.VideoNotFoundException;
import com.videostream.api.domain.model.ByteRange;
import com.videostream.api.domain.model.VideoId;
import com.videostream.api.domain.model.VideoMetadata;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integração do adaptador S3 com um servidor S3-compatible real (stub HTTP em processo),
 * sem Docker: exercita o AWS SDK de verdade, incluindo assinatura, HEAD, GET com Range
 * e tradução de 404.
 */
class S3VideoStorageAdapterHttpTest {

    private static final byte[] VIDEO = videoBytes(3_072);
    private static final String BUCKET = "videos-bucket";
    private static final String KEY = "videos/demo.mp4";

    private static StubS3Server server;
    private static S3Client s3Client;
    private static S3VideoStorageAdapter adapter;

    @BeforeAll
    static void startServer() throws IOException {
        server = new StubS3Server(VIDEO);
        S3StorageProperties properties = new S3StorageProperties(
                BUCKET,
                "us-east-1",
                "videos",
                "http://localhost:" + server.port(),
                true,
                false,
                "test",
                "test");

        s3Client = new S3ClientConfiguration().s3Client(properties);
        adapter = new S3VideoStorageAdapter(s3Client, properties);
    }

    @AfterAll
    static void stopServer() throws IOException {
        if (s3Client != null) {
            s3Client.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("HEAD real: metadados chegam com tamanho, content-type e etag")
    void metadata() {
        VideoMetadata metadata = adapter.getMetadata(VideoId.of("demo.mp4"));

        assertThat(metadata.sizeBytes()).isEqualTo(VIDEO.length);
        assertThat(metadata.contentType()).isEqualTo("video/mp4");
        assertThat(metadata.etag()).isEqualTo("\"stub-etag\"");
        assertThat(metadata.filename()).isEqualTo("demo.mp4");
        assertThat(metadata.lastModified()).isNotNull();
    }

    @Test
    @DisplayName("GET real com Range: cabeçalho chega ao servidor e os bytes certos voltam")
    void rangedGet() throws IOException {
        ByteRange range = ByteRange.parse("bytes=1024-2047", VIDEO.length, 1_024);

        try (InputStream stream = adapter.openStream(VideoId.of("demo.mp4"), range)) {
            assertThat(stream.readAllBytes()).containsExactly(Arrays.copyOfRange(VIDEO, 1024, 2048));
        }
        assertThat(server.lastRange()).isEqualTo("bytes=1024-2047");
    }

    @Test
    @DisplayName("GET real sem Range: nenhum cabeçalho Range é enviado")
    void fullGet() throws IOException {
        try (InputStream stream = adapter.openStream(VideoId.of("demo.mp4"), ByteRange.full(VIDEO.length))) {
            assertThat(stream.readAllBytes()).containsExactly(VIDEO);
        }
        assertThat(server.lastRange()).isNull();
    }

    @Test
    @DisplayName("objeto inexistente em S3 real vira VideoNotFoundException")
    void notFound() {
        assertThatThrownBy(() -> adapter.getMetadata(VideoId.of("nao-existe.mp4")))
                .isInstanceOf(VideoNotFoundException.class);
    }

    @Test
    @DisplayName("PUT real com FileInputStream (sem mark/reset): upload funciona sem ResetException")
    void putWithNonMarkableStream() throws IOException {
        java.nio.file.Path temp = java.nio.file.Files.createTempFile("test-video-", ".mp4");
        try {
            java.nio.file.Files.write(temp, VIDEO);
            try (InputStream fileStream = java.nio.file.Files.newInputStream(temp)) {
                VideoMetadata saved = adapter.save(VideoId.of("demo.mp4"), fileStream, VIDEO.length, "video/mp4");
                assertThat(saved.sizeBytes()).isEqualTo(VIDEO.length);
                assertThat(saved.etag()).isEqualTo("\"stub-etag\"");
            }
        } finally {
            java.nio.file.Files.deleteIfExists(temp);
        }
    }

    private static byte[] videoBytes(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) (i % 251);
        }
        return bytes;
    }

    /**
     * Stub mínimo de S3 over HTTP/1.1, suficiente para HEAD e GET com Range.
     */
    private static final class StubS3Server implements AutoCloseable {

        private static final String LAST_MODIFIED = DateTimeFormatter.RFC_1123_DATE_TIME
                .format(Instant.parse("2026-03-04T05:06:07Z").atZone(ZoneOffset.UTC));

        private final byte[] content;
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final AtomicReference<String> lastRange = new AtomicReference<>();
        private volatile boolean running = true;

        StubS3Server(byte[] content) throws IOException {
            this.content = content;
            this.serverSocket = new ServerSocket(0);
            executor.submit(this::acceptLoop);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        String lastRange() {
            return lastRange.get();
        }

        private void acceptLoop() {
            while (running) {
                try (Socket socket = serverSocket.accept()) {
                    handle(socket);
                } catch (IOException ex) {
                    // socket fechado no shutdown
                }
            }
        }

        private void handle(Socket socket) throws IOException {
            socket.setSoTimeout(5_000);
            var reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                    socket.getInputStream(), StandardCharsets.ISO_8859_1));
            String requestLine = reader.readLine();
            if (requestLine == null) {
                return;
            }
            Map<String, String> headers = new LinkedHashMap<>();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
                }
            }

            OutputStream out = socket.getOutputStream();
            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String path = parts[1];

            if (!path.equals("/" + BUCKET + "/" + KEY)) {
                writeError(out, 404, "NoSuchKey", "The specified key does not exist.");
                return;
            }
            if ("HEAD".equals(method)) {
                writeHead(out);
            } else if ("GET".equals(method)) {
                String range = headers.get("range");
                lastRange.set(range);
                writeGet(out, range);
            } else if ("PUT".equals(method)) {
                int contentLength = Integer.parseInt(headers.getOrDefault("content-length", "0"));
                socket.getInputStream().readNBytes(contentLength);
                writeHead(out);
            } else {
                writeError(out, 405, "MethodNotAllowed", "Method not allowed");
            }
        }

        private void writeHead(OutputStream out) throws IOException {
            String response = """
                    HTTP/1.1 200 OK\r
                    Content-Type: video/mp4\r
                    Content-Length: %d\r
                    ETag: "stub-etag"\r
                    Last-Modified: %s\r
                    Accept-Ranges: bytes\r
                    Connection: close\r
                    \r
                    """.formatted(content.length, LAST_MODIFIED);
            out.write(response.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
        }

        private void writeGet(OutputStream out, String rangeHeader) throws IOException {
            if (rangeHeader == null) {
                String head = """
                        HTTP/1.1 200 OK\r
                        Content-Type: video/mp4\r
                        Content-Length: %d\r
                        ETag: "stub-etag"\r
                        Accept-Ranges: bytes\r
                        Connection: close\r
                        \r
                        """.formatted(content.length);
                out.write(head.getBytes(StandardCharsets.ISO_8859_1));
                out.write(content);
                out.flush();
                return;
            }

            long start;
            long end;
            try {
                String spec = rangeHeader.substring("bytes=".length());
                int dash = spec.indexOf('-');
                start = Long.parseLong(spec.substring(0, dash));
                String endPart = spec.substring(dash + 1);
                end = endPart.isEmpty() ? content.length - 1 : Long.parseLong(endPart);
                end = Math.min(end, content.length - 1);
            } catch (RuntimeException ex) {
                writeError(out, 416, "InvalidRange", "The requested range is not satisfiable");
                return;
            }
            if (start < 0 || start >= content.length || end < start) {
                writeError(out, 416, "InvalidRange", "The requested range is not satisfiable");
                return;
            }

            int length = (int) (end - start + 1);
            String head = """
                    HTTP/1.1 206 Partial Content\r
                    Content-Type: video/mp4\r
                    Content-Length: %d\r
                    Content-Range: bytes %d-%d/%d\r
                    ETag: "stub-etag"\r
                    Accept-Ranges: bytes\r
                    Connection: close\r
                    \r
                    """.formatted(length, start, end, content.length);
            out.write(head.getBytes(StandardCharsets.ISO_8859_1));
            out.write(content, (int) start, length);
            out.flush();
        }

        private void writeError(OutputStream out, int status, String code, String message) throws IOException {
            String body = "<Error><Code>%s</Code><Message>%s</Message></Error>".formatted(code, message);
            String head = """
                    HTTP/1.1 %d Error\r
                    Content-Type: application/xml\r
                    Content-Length: %d\r
                    Connection: close\r
                    \r
                    """.formatted(status, body.getBytes(StandardCharsets.UTF_8).length);
            out.write(head.getBytes(StandardCharsets.ISO_8859_1));
            out.write(body.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        @Override
        public void close() throws IOException {
            running = false;
            serverSocket.close();
            executor.shutdownNow();
        }
    }
}
