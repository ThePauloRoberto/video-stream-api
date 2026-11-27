package com.videostream.api.domain.port;

import com.videostream.api.domain.model.ByteRange;
import com.videostream.api.domain.model.VideoId;
import com.videostream.api.domain.model.VideoMetadata;
import com.videostream.api.domain.model.VideoSummary;

import java.io.InputStream;
import java.util.List;

/**
 * Porta de saída do domínio para armazenamento de vídeos.
 *
 * <p>É o único contrato que a aplicação conhece sobre o storage. A implementação
 * concreta (S3, GCS, filesystem, ...) vive em um adaptador, mantendo o domínio e a
 * aplicação independentes de SDKs e de infraestrutura.
 */
public interface VideoStoragePort {

    /**
     * Metadados do vídeo.
     *
     * @throws com.videostream.api.domain.exception.VideoNotFoundException se o vídeo não existir
     * @throws com.videostream.api.domain.exception.VideoStorageException em falhas de infraestrutura
     */
    VideoMetadata getMetadata(VideoId id);

    /**
     * Abre o stream do trecho solicitado. O chamador é responsável por fechar o stream.
     *
     * @throws com.videostream.api.domain.exception.VideoNotFoundException se o vídeo não existir
     * @throws com.videostream.api.domain.exception.VideoStorageException em falhas de infraestrutura
     */
    InputStream openStream(VideoId id, ByteRange range);

    /**
     * Lista os vídeos disponíveis, respeitando o limite informado.
     */
    List<VideoSummary> listVideos(int limit);

    /**
     * Armazena um novo vídeo no storage e devolve seus metadados.
     *
     * @param id          identificador lógico do vídeo
     * @param content     stream com os bytes do arquivo
     * @param sizeBytes   tamanho total do arquivo em bytes
     * @param contentType MIME type informado (pode ser {@code null})
     * @return metadados do vídeo recém-armazenado
     * @throws com.videostream.api.domain.exception.VideoStorageException em falhas de infraestrutura
     */
    VideoMetadata save(VideoId id, InputStream content, long sizeBytes, String contentType);
}
