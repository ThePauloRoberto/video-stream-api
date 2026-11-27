package com.videostream.api.adapter.in.web.mapper;

import com.videostream.api.adapter.in.web.dto.VideoMetadataResponse;
import com.videostream.api.adapter.in.web.dto.VideoSummaryResponse;
import com.videostream.api.domain.model.VideoId;
import com.videostream.api.domain.model.VideoMetadata;
import com.videostream.api.domain.model.VideoSummary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converte modelos de domínio em DTOs de resposta.
 */
@Component
public class VideoWebMapper {

    private static final String STREAM_PATH = "/api/v1/videos/%s/stream";

    public VideoMetadataResponse toResponse(VideoMetadata metadata) {
        return new VideoMetadataResponse(
                metadata.id().value(),
                metadata.filename(),
                metadata.contentType(),
                metadata.sizeBytes(),
                metadata.etag(),
                metadata.lastModified(),
                streamUrl(metadata.id()));
    }

    public VideoSummaryResponse toResponse(VideoSummary summary) {
        return new VideoSummaryResponse(summary.id().value(), summary.sizeBytes(), summary.lastModified());
    }

    public List<VideoSummaryResponse> toResponse(List<VideoSummary> summaries) {
        return summaries.stream().map(this::toResponse).toList();
    }

    private static String streamUrl(VideoId id) {
        return STREAM_PATH.formatted(id.value());
    }
}
