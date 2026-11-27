package com.videostream.api.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração do streaming HTTP.
 *
 * @param chunkSize    tamanho do bloco lido do S3 e descarregado no socket (padrão 1 MB)
 * @param maxRangeSize tamanho máximo de uma resposta {@code 206} (padrão 1 MB);
 *                     {@code 0} desabilita o limite e devolve a faixa completa pedida
 */
@Validated
@ConfigurationProperties(prefix = "video.streaming")
public record VideoStreamingProperties(@NotNull DataSize chunkSize, @NotNull DataSize maxRangeSize) {

    public static final DataSize DEFAULT_CHUNK_SIZE = DataSize.ofMegabytes(1);
    public static final DataSize DEFAULT_MAX_RANGE_SIZE = DataSize.ofMegabytes(1);

    public VideoStreamingProperties {
        chunkSize = chunkSize == null ? DEFAULT_CHUNK_SIZE : chunkSize;
        maxRangeSize = maxRangeSize == null ? DEFAULT_MAX_RANGE_SIZE : maxRangeSize;
        if (chunkSize.toBytes() <= 0 || chunkSize.toBytes() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "video.streaming.chunk-size deve estar entre 1 byte e 2 GB, mas era " + chunkSize);
        }
        if (maxRangeSize.toBytes() < 0) {
            throw new IllegalArgumentException(
                    "video.streaming.max-range-size não pode ser negativo, mas era " + maxRangeSize);
        }
    }

    /**
     * Tamanho do buffer de cópia, já validado para caber em um {@code int}.
     */
    public int chunkSizeBytes() {
        return (int) chunkSize.toBytes();
    }

    /**
     * Limite de bytes por resposta; {@code 0} significa sem limite.
     */
    public long maxRangeBytes() {
        return maxRangeSize.toBytes();
    }
}
