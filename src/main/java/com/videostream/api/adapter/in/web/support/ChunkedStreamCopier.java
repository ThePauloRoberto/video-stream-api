package com.videostream.api.adapter.in.web.support;

import com.videostream.api.config.VideoStreamingProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Copia o stream do storage para o stream da resposta em blocos de tamanho fixo.
 *
 * <p>Este é o ponto que garante memória constante: nunca carregamos o vídeo inteiro,
 * apenas um buffer de {@code video.streaming.chunk-size} (1 MB por padrão) por request.
 * O {@code flush()} a cada bloco faz o cliente receber o primeiro chunk sem esperar o
 * restante (importante para o início rápido da reprodução).
 */
@Component
public class ChunkedStreamCopier {

    private final VideoStreamingProperties properties;

    public ChunkedStreamCopier(VideoStreamingProperties properties) {
        this.properties = properties;
    }

    public void copy(InputStream source, OutputStream target) throws IOException {
        byte[] buffer = new byte[properties.chunkSizeBytes()];
        int read;
        while ((read = source.read(buffer)) != -1) {
            target.write(buffer, 0, read);
            target.flush();
        }
    }
}
