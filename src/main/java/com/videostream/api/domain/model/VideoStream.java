package com.videostream.api.domain.model;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Objects;

/**
 * Um trecho de vídeo pronto para ser escrito na resposta HTTP.
 *
 * <p>Carrega o stream já posicionado pelo adaptador de storage, junto com os metadados
 * necessários para montar os cabeçalhos. Precisa ser fechado para devolver a conexão
 * HTTP ao pool do cliente S3.
 */
public record VideoStream(
        VideoId id,
        String filename,
        String contentType,
        long totalSize,
        ByteRange range,
        String etag,
        Instant lastModified,
        InputStream content) implements AutoCloseable {

    public VideoStream {
        Objects.requireNonNull(id, "id não pode ser nulo");
        Objects.requireNonNull(contentType, "contentType não pode ser nulo");
        Objects.requireNonNull(range, "range não pode ser nulo");
        Objects.requireNonNull(content, "content não pode ser nulo");
        if (range.totalSize() != totalSize) {
            throw new IllegalArgumentException(
                    "range.totalSize() (%d) difere de totalSize (%d)".formatted(range.totalSize(), totalSize));
        }
    }

    /**
     * Bytes que serão escritos no corpo da resposta ({@code Content-Length}).
     */
    public long contentLength() {
        return range.length();
    }

    @Override
    public void close() throws IOException {
        content.close();
    }
}
