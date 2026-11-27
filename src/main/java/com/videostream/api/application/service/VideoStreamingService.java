package com.videostream.api.application.service;

import com.videostream.api.application.port.in.GetVideoMetadataUseCase;
import com.videostream.api.application.port.in.ListVideosUseCase;
import com.videostream.api.application.port.in.StreamVideoUseCase;
import com.videostream.api.domain.model.ByteRange;
import com.videostream.api.domain.model.VideoId;
import com.videostream.api.domain.model.VideoMetadata;
import com.videostream.api.domain.model.VideoStream;
import com.videostream.api.domain.model.VideoSummary;
import com.videostream.api.domain.port.VideoStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

/**
 * Orquestra os casos de uso de streaming e catálogo.
 *
 * <p>Sem anotações de framework: é instanciado em {@code UseCaseConfiguration}, o que
 * mantém a camada de aplicação testável com um simples {@code new}.
 */
public class VideoStreamingService implements StreamVideoUseCase, GetVideoMetadataUseCase, ListVideosUseCase {

    private static final Logger log = LoggerFactory.getLogger(VideoStreamingService.class);

    private final VideoStoragePort storage;
    private final long maxRangeBytes;

    public VideoStreamingService(VideoStoragePort storage, long maxRangeBytes) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.maxRangeBytes = maxRangeBytes;
    }

    @Override
    public VideoStream stream(String videoId, String rangeHeader) {
        VideoId id = VideoId.of(videoId);
        VideoMetadata metadata = storage.getMetadata(id);

        ByteRange range = ByteRange.parse(rangeHeader, metadata.sizeBytes(), maxRangeBytes);
        InputStream content;
        try {
            content = storage.openStream(id, range);
        } catch (RuntimeException ex) {
            log.warn("Falha ao abrir stream do vídeo {} no range {}: {}", id, range, ex.getMessage());
            throw ex;
        }

        log.debug("Streaming vídeo={} range={} bytes={}", id, range, range.length());
        return new VideoStream(
                id,
                metadata.filename(),
                metadata.contentType(),
                metadata.sizeBytes(),
                range,
                metadata.etag(),
                metadata.lastModified(),
                content);
    }

    @Override
    public VideoMetadata getMetadata(String videoId) {
        return storage.getMetadata(VideoId.of(videoId));
    }

    @Override
    public List<VideoSummary> listVideos(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit deve ser maior que zero: " + limit);
        }
        return storage.listVideos(limit);
    }
}
