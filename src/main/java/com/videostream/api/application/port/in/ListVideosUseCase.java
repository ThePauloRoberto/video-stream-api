package com.videostream.api.application.port.in;

import com.videostream.api.domain.model.VideoSummary;

import java.util.List;

/**
 * Caso de uso: listar os vídeos disponíveis.
 */
public interface ListVideosUseCase {

    List<VideoSummary> listVideos(int limit);
}
