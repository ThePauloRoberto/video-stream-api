package com.videostream.api.domain.model;

import com.videostream.api.domain.exception.InvalidRangeException;

/**
 * Faixa de bytes já normalizada e limitada ao tamanho da representação.
 *
 * <p>Concentra as regras de HTTP Range Requests que são independentes de framework:
 * <ul>
 *   <li>interpretar {@code Range: bytes=inicio-fim}, {@code bytes=inicio-} e {@code bytes=-sufixo};</li>
 *   <li>aplicar o limite de bytes por resposta (padrão: 1 MB);</li>
 *   <li>gerar o valor do cabeçalho {@code Content-Range}.</li>
 * </ul>
 *
 * <p>Objeto de domínio puro: nenhuma dependência de Spring, servlet ou AWS SDK.
 */
public record ByteRange(long start, long endInclusive, long totalSize) {

    private static final String UNIT_NAME = "bytes";
    private static final String UNIT_PREFIX = UNIT_NAME + "=";

    public ByteRange {
        if (totalSize < 0) {
            throw new IllegalArgumentException("totalSize não pode ser negativo: " + totalSize);
        }
        if (totalSize == 0) {
            if (start != 0 || endInclusive != -1) {
                throw new IllegalArgumentException("Faixa inválida para representação vazia");
            }
        } else if (start < 0 || start >= totalSize || endInclusive < start || endInclusive >= totalSize) {
            throw new IllegalArgumentException(
                    "Faixa inválida [%d-%d] para representação de %d bytes".formatted(start, endInclusive, totalSize));
        }
    }

    /**
     * Representação inteira (usada quando o cliente não envia {@code Range}).
     */
    public static ByteRange full(long totalSize) {
        return totalSize == 0 ? new ByteRange(0, -1, 0) : new ByteRange(0, totalSize - 1, totalSize);
    }

    /**
     * Interpreta o cabeçalho {@code Range} e devolve uma faixa pronta para ser pedida ao storage.
     *
     * @param headerValue  valor bruto do cabeçalho (pode ser {@code null})
     * @param totalSize    tamanho total do objeto
     * @param maxRangeBytes limite de bytes por resposta; {@code <= 0} desabilita o limite
     * @throws InvalidRangeException quando a faixa é sinteticamente válida mas não satisfazível
     */
    public static ByteRange parse(String headerValue, long totalSize, long maxRangeBytes) {
        String header = headerValue == null ? "" : headerValue.trim();
        if (header.isEmpty() || !header.regionMatches(true, 0, UNIT_PREFIX, 0, UNIT_PREFIX.length())) {
            // Sem Range (ou unidade desconhecida, que o RFC 9110 manda ignorar) => representação inteira.
            return full(totalSize);
        }
        if (totalSize == 0) {
            throw new InvalidRangeException(header, totalSize);
        }

        String spec = header.substring(UNIT_PREFIX.length()).trim();
        int comma = spec.indexOf(',');
        if (comma >= 0) {
            // Multi-range: servimos apenas a primeira faixa (comportamento documentado da API).
            spec = spec.substring(0, comma).trim();
        }

        int dash = spec.indexOf('-');
        if (dash < 0) {
            throw new InvalidRangeException(header, totalSize);
        }
        String firstPart = spec.substring(0, dash).trim();
        String lastPart = spec.substring(dash + 1).trim();

        long start;
        long end;
        if (firstPart.isEmpty()) {
            // bytes=-N => últimos N bytes
            long suffixLength = parseOffset(lastPart, header, totalSize);
            if (suffixLength == 0) {
                throw new InvalidRangeException(header, totalSize);
            }
            start = Math.max(0, totalSize - suffixLength);
            end = totalSize - 1;
        } else {
            start = parseOffset(firstPart, header, totalSize);
            if (start >= totalSize) {
                throw new InvalidRangeException(header, totalSize);
            }
            if (lastPart.isEmpty()) {
                end = totalSize - 1;
            } else {
                end = parseOffset(lastPart, header, totalSize);
                if (end < start) {
                    throw new InvalidRangeException(header, totalSize);
                }
                end = Math.min(end, totalSize - 1);
            }
        }

        if (maxRangeBytes > 0) {
            long window = Math.min(maxRangeBytes - 1, Long.MAX_VALUE - start);
            end = Math.min(end, start + window);
        }
        return new ByteRange(start, end, totalSize);
    }

    private static long parseOffset(String value, String header, long totalSize) {
        if (value.isEmpty() || !value.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new InvalidRangeException(header, totalSize);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new InvalidRangeException(header, totalSize);
        }
    }

    /**
     * Quantidade de bytes desta faixa. {@code 0} para representação vazia.
     */
    public long length() {
        return endInclusive - start + 1;
    }

    public boolean isEmpty() {
        return length() == 0;
    }

    /**
     * {@code true} quando a resposta não cobre a representação inteira e deve usar HTTP 206.
     */
    public boolean isPartial() {
        return totalSize > 0 && (start > 0 || endInclusive < totalSize - 1);
    }

    /**
     * {@code true} quando a faixa cobre a representação inteira (não precisa de {@code Range} no S3).
     */
    public boolean isWholeRepresentation() {
        return totalSize == 0 || (start == 0 && endInclusive == totalSize - 1);
    }

    /**
     * Valor de {@code Content-Range} para respostas 206.
     */
    public String contentRange() {
        return "%s %d-%d/%d".formatted(UNIT_NAME, start, endInclusive, totalSize);
    }

    /**
     * Valor de {@code Content-Range} para respostas 416.
     */
    public static String unsatisfiedContentRange(long totalSize) {
        return "%s */%d".formatted(UNIT_NAME, totalSize);
    }

    @Override
    public String toString() {
        return "%s %d-%d/%d".formatted(UNIT_NAME, start, endInclusive, totalSize);
    }
}
