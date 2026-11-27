package com.videostream.api.application.port.in;

import com.videostream.api.domain.model.VideoMetadata;

/**
 * Caso de uso: consultar metadados de um vídeo.
 */
public interface GetVideoMetadataUseCase {

    VideoMetadata getMetadata(String videoId);
}
