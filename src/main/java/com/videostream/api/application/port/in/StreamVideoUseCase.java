package com.videostream.api.application.port.in;

import com.videostream.api.domain.model.VideoStream;

/**
 * Caso de uso: entregar um trecho de vídeo respeitando o cabeçalho HTTP {@code Range}.
 */
public interface StreamVideoUseCase {

    /**
     * @param videoId     identificador lógico do vídeo
     * @param rangeHeader valor bruto de {@code Range} (pode ser {@code null})
     * @return stream posicionado no trecho solicitado; o chamador deve fechá-lo
     */
    VideoStream stream(String videoId, String rangeHeader);
}
