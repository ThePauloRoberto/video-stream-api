package com.videostream.api.adapter.out.s3;

import com.videostream.api.domain.exception.InvalidRangeException;
import com.videostream.api.domain.exception.VideoNotFoundException;
import com.videostream.api.domain.exception.VideoStorageException;
import com.videostream.api.domain.model.ByteRange;
import com.videostream.api.domain.model.VideoId;
import com.videostream.api.domain.model.VideoMetadata;
import com.videostream.api.domain.model.VideoSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3VideoStorageAdapterTest {

    private static final String BUCKET = "videos-bucket";
    private static final Instant LAST_MODIFIED = Instant.parse("2026-02-03T10:15:30Z");

    @Mock
    private S3Client s3Client;

    private S3VideoStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        S3StorageProperties properties = new S3StorageProperties(
                BUCKET, "us-east-1", "videos", null, true, false, null, null);
        adapter = new S3VideoStorageAdapter(s3Client, properties);
    }

    @Test
    void mapeiaMetadadosDoHeadObject() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength(1_234L)
                .contentType("video/mp4")
                .eTag("\"abc\"")
                .lastModified(LAST_MODIFIED)
                .build());

        VideoMetadata metadata = adapter.getMetadata(VideoId.of("trailer.mp4"));

        assertThat(metadata.id().value()).isEqualTo("trailer.mp4");
        assertThat(metadata.filename()).isEqualTo("trailer.mp4");
        assertThat(metadata.contentType()).isEqualTo("video/mp4");
        assertThat(metadata.sizeBytes()).isEqualTo(1_234L);
        assertThat(metadata.etag()).isEqualTo("\"abc\"");
        assertThat(metadata.lastModified()).isEqualTo(LAST_MODIFIED);

        ArgumentCaptor<HeadObjectRequest> captor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo("videos/trailer.mp4");
    }

    @Test
    void infereContentTypePelaExtensaoQuandoS3NaoInforma() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength(10L)
                .lastModified(LAST_MODIFIED)
                .build());

        assertThat(adapter.getMetadata(VideoId.of("filme.mp4")).contentType()).isEqualTo("video/mp4");
    }

    @Test
    void objetoInexistenteViraVideoNotFoundException() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("Not Found").build());

        assertThatThrownBy(() -> adapter.getMetadata(VideoId.of("nao-existe")))
                .isInstanceOf(VideoNotFoundException.class)
                .hasMessageContaining("nao-existe");
    }

    @Test
    void erroDoProvedorViraVideoStorageException() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(503).message("Slow Down").build());

        assertThatThrownBy(() -> adapter.getMetadata(VideoId.of("video-1")))
                .isInstanceOf(VideoStorageException.class);
    }

    @Test
    void enviaRangeNativoParaOS3() throws Exception {
        ResponseInputStream<GetObjectResponse> responseStream = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(new byte[]{1, 2, 3})));
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        ByteRange range = ByteRange.parse("bytes=100-199", 1_000, 1_024);
        try (InputStream stream = adapter.openStream(VideoId.of("video-1"), range)) {
            assertThat(stream.readAllBytes()).containsExactly(1, 2, 3);
        }

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(captor.capture());
        assertThat(captor.getValue().range()).isEqualTo("bytes=100-199");
        assertThat(captor.getValue().key()).isEqualTo("videos/video-1");
    }

    @Test
    void representacaoCompletaNaoEnviaRange() throws Exception {
        ResponseInputStream<GetObjectResponse> responseStream = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(new byte[0])));
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        try (InputStream ignored = adapter.openStream(VideoId.of("video-1"), ByteRange.full(1_000))) {
            // apenas fecha
        }

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(captor.capture());
        assertThat(captor.getValue().range()).isNull();
    }

    @Test
    void range416DoS3ViraInvalidRangeException() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(416).message("Range Not Satisfiable").build());

        ByteRange range = ByteRange.parse("bytes=0-9", 1_000, 1_024);
        assertThatThrownBy(() -> adapter.openStream(VideoId.of("video-1"), range))
                .isInstanceOf(InvalidRangeException.class);
    }

    @Test
    void listaApenasObjetosComIdValido() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(
                        S3Object.builder().key("videos/a.mp4").size(10L).lastModified(LAST_MODIFIED).build(),
                        S3Object.builder().key("videos/subdir/").size(0L).build(),
                        S3Object.builder().key("videos/nome invalido.mp4").size(5L).build())
                .build());

        List<VideoSummary> summaries = adapter.listVideos(10);

        assertThat(summaries).extracting(summary -> summary.id().value()).containsExactly("a.mp4");
    }

    @Test
    void falhaAoListarViraVideoStorageException() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenThrow(S3Exception.builder().statusCode(403).message("Access Denied").build());

        assertThatThrownBy(() -> adapter.listVideos(10)).isInstanceOf(VideoStorageException.class);
    }

    @Test
    void salvaVideoNoS3ComPutObject() {
        byte[] payload = new byte[]{10, 20, 30, 40};
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("\"etag-upload\"").build());

        VideoMetadata metadata = adapter.save(
                VideoId.of("upload.mp4"),
                new ByteArrayInputStream(payload),
                payload.length,
                "video/mp4");

        assertThat(metadata.id().value()).isEqualTo("upload.mp4");
        assertThat(metadata.filename()).isEqualTo("upload.mp4");
        assertThat(metadata.contentType()).isEqualTo("video/mp4");
        assertThat(metadata.sizeBytes()).isEqualTo(payload.length);
        assertThat(metadata.etag()).isEqualTo("\"etag-upload\"");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo("videos/upload.mp4");
        assertThat(captor.getValue().contentLength()).isEqualTo((long) payload.length);
        assertThat(captor.getValue().contentType()).isEqualTo("video/mp4");
    }

    @Test
    void falhaAoSalvarViraVideoStorageException() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("Internal Error").build());

        assertThatThrownBy(() -> adapter.save(
                VideoId.of("upload.mp4"),
                new ByteArrayInputStream(new byte[]{1}),
                1L,
                "video/mp4"))
                .isInstanceOf(VideoStorageException.class);
    }
}
