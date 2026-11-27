package com.videostream.api.application.port.in;

import com.videostream.api.domain.model.VideoMetadata;

import java.io.InputStream;

/**
 * Caso de uso: realizar o upload de um vídeo para o storage.
 */
public interface UploadVideoUseCase {

    /**
     * Salva um vídeo no armazenamento.
     *
     * @param videoId     nome/identificador lógico do vídeo (ex.: {@code demo.mp4})
     * @param content     stream com os bytes do vídeo
     * @param sizeBytes   tamanho total em bytes
     * @param contentType tipo MIME (ex.: {@code video/mp4})
     * @return metadados do vídeo armazenado
     */
    VideoMetadata upload(String videoId, InputStream content, long sizeBytes, String contentType);
}
