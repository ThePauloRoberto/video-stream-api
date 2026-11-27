package com.videostream.api.support;

import com.videostream.api.domain.exception.VideoNotFoundException;
import com.videostream.api.domain.exception.VideoStorageException;
import com.videostream.api.domain.model.ByteRange;
import com.videostream.api.domain.model.VideoId;
import com.videostream.api.domain.model.VideoMetadata;
import com.videostream.api.domain.model.VideoSummary;
import com.videostream.api.domain.port.VideoStoragePort;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptador em memória usado nos testes: substitui o S3 sem nenhuma mágica de mock.
 */
public final class InMemoryVideoStorageAdapter implements VideoStoragePort {

    private static final Instant LAST_MODIFIED = Instant.parse("2026-01-02T03:04:05Z");

    private final Map<VideoId, StoredVideo> videos = new LinkedHashMap<>();

    public InMemoryVideoStorageAdapter put(String id, byte[] data, String contentType) {
        videos.put(VideoId.of(id), new StoredVideo(data.clone(), contentType));
        return this;
    }

    @Override
    public VideoMetadata getMetadata(VideoId id) {
        StoredVideo stored = require(id);
        String filename = id.value().contains(".") ? id.value() : id.value() + ".mp4";
        return new VideoMetadata(
                id,
                filename,
                stored.contentType(),
                stored.data().length,
                etag(stored.data()),
                LAST_MODIFIED);
    }

    @Override
    public InputStream openStream(VideoId id, ByteRange range) {
        StoredVideo stored = require(id);
        return new ByteArrayInputStream(stored.data(), (int) range.start(), (int) range.length());
    }

    @Override
    public List<VideoSummary> listVideos(int limit) {
        return videos.entrySet().stream()
                .limit(limit)
                .map(entry -> new VideoSummary(entry.getKey(), entry.getValue().data().length, LAST_MODIFIED))
                .toList();
    }

    @Override
    public VideoMetadata save(VideoId id, InputStream content, long sizeBytes, String contentType) {
        try {
            byte[] data = content.readAllBytes();
            String resolvedType = (contentType == null || contentType.isBlank()) ? "video/mp4" : contentType;
            put(id.value(), data, resolvedType);
            return getMetadata(id);
        } catch (IOException ex) {
            throw new VideoStorageException("Falha ao ler stream em memória para " + id.value(), ex);
        }
    }

    public int size() {
        return videos.size();
    }

    private StoredVideo require(VideoId id) {
        StoredVideo stored = videos.get(id);
        if (stored == null) {
            throw new VideoNotFoundException(id.value());
        }
        return stored;
    }

    private static String etag(byte[] data) {
        return "\"" + Integer.toHexString(Arrays.hashCode(data)) + "\"";
    }

    private record StoredVideo(byte[] data, String contentType) {
    }
}
