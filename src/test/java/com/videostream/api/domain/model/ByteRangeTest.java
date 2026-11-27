package com.videostream.api.domain.model;

import com.videostream.api.domain.exception.InvalidRangeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ByteRangeTest {

    private static final long TOTAL = 5_000L;
    private static final long ONE_MB = 1_048_576L;

    @Nested
    @DisplayName("representação completa")
    class FullRepresentation {

        @Test
        void semHeaderDevolveTudo() {
            ByteRange range = ByteRange.parse(null, TOTAL, ONE_MB);

            assertThat(range.start()).isZero();
            assertThat(range.endInclusive()).isEqualTo(TOTAL - 1);
            assertThat(range.length()).isEqualTo(TOTAL);
            assertThat(range.isPartial()).isFalse();
            assertThat(range.isWholeRepresentation()).isTrue();
        }

        @Test
        void headerVazioDevolveTudo() {
            assertThat(ByteRange.parse("   ", TOTAL, ONE_MB)).isEqualTo(ByteRange.full(TOTAL));
        }

        @Test
        void unidadeDesconhecidaEhIgnorada() {
            // RFC 9110: unidade não suportada deve ser ignorada
            assertThat(ByteRange.parse("items=0-10", TOTAL, ONE_MB)).isEqualTo(ByteRange.full(TOTAL));
        }

        @Test
        void recursoVazioTemTamanhoZero() {
            ByteRange range = ByteRange.full(0);

            assertThat(range.length()).isZero();
            assertThat(range.isEmpty()).isTrue();
            assertThat(range.isPartial()).isFalse();
        }
    }

    @Nested
    @DisplayName("faixas explícitas")
    class ExplicitRanges {

        @Test
        void inicioEFim() {
            ByteRange range = ByteRange.parse("bytes=0-99", TOTAL, ONE_MB);

            assertThat(range.start()).isZero();
            assertThat(range.endInclusive()).isEqualTo(99);
            assertThat(range.length()).isEqualTo(100);
            assertThat(range.isPartial()).isTrue();
            assertThat(range.contentRange()).isEqualTo("bytes 0-99/5000");
        }

        @Test
        void inicioAbertoVaiAteOFim() {
            ByteRange range = ByteRange.parse("bytes=4000-", TOTAL, ONE_MB);

            assertThat(range.start()).isEqualTo(4000);
            assertThat(range.endInclusive()).isEqualTo(TOTAL - 1);
            assertThat(range.length()).isEqualTo(1000);
        }

        @Test
        void fimMaiorQueOTamanhoEhTruncado() {
            ByteRange range = ByteRange.parse("bytes=4000-999999", TOTAL, ONE_MB);

            assertThat(range.endInclusive()).isEqualTo(TOTAL - 1);
        }

        @Test
        void sufixoPegaOsUltimosBytes() {
            ByteRange range = ByteRange.parse("bytes=-500", TOTAL, ONE_MB);

            assertThat(range.start()).isEqualTo(4500);
            assertThat(range.endInclusive()).isEqualTo(4999);
        }

        @Test
        void sufixoMaiorQueORecursoPegaTudo() {
            ByteRange range = ByteRange.parse("bytes=-999999", TOTAL, ONE_MB);

            assertThat(range.start()).isZero();
            assertThat(range.endInclusive()).isEqualTo(TOTAL - 1);
        }

        @Test
        void multiRangeUsaApenasAPrimeiraFaixa() {
            ByteRange range = ByteRange.parse("bytes=0-9,20-29", TOTAL, ONE_MB);

            assertThat(range.start()).isZero();
            assertThat(range.endInclusive()).isEqualTo(9);
        }
    }

    @Nested
    @DisplayName("limite de chunk")
    class ChunkLimit {

        @Test
        void rangeAbertoEhLimitadoAoMaximo() {
            ByteRange range = ByteRange.parse("bytes=0-", TOTAL, 1024);

            assertThat(range.start()).isZero();
            assertThat(range.endInclusive()).isEqualTo(1023);
            assertThat(range.length()).isEqualTo(1024);
            assertThat(range.contentRange()).isEqualTo("bytes 0-1023/5000");
        }

        @Test
        void rangeExplicitoMaiorQueOMaximoEhLimitado() {
            ByteRange range = ByteRange.parse("bytes=100-4000", TOTAL, 1024);

            assertThat(range.start()).isEqualTo(100);
            assertThat(range.endInclusive()).isEqualTo(1123);
        }

        @Test
        void sufixoGrandeTambemEhLimitado() {
            ByteRange range = ByteRange.parse("bytes=-9999", TOTAL, 1024);

            assertThat(range.start()).isZero();
            assertThat(range.endInclusive()).isEqualTo(1023);
        }

        @Test
        void limiteZeroDesabilitaOCorte() {
            ByteRange range = ByteRange.parse("bytes=0-", TOTAL, 0);

            assertThat(range.length()).isEqualTo(TOTAL);
            assertThat(range.isWholeRepresentation()).isTrue();
        }

        @Test
        void limiteMaiorQueORecursoNaoAlteraNada() {
            ByteRange range = ByteRange.parse("bytes=0-", TOTAL, ONE_MB);

            assertThat(range.length()).isEqualTo(TOTAL);
        }
    }

    @Nested
    @DisplayName("faixas inválidas")
    class InvalidRanges {

        @Test
        void inicioAlemDoTamanho() {
            assertThatThrownBy(() -> ByteRange.parse("bytes=5000-", TOTAL, ONE_MB))
                    .isInstanceOf(InvalidRangeException.class)
                    .hasMessageContaining("5000");
        }

        @Test
        void inicioMaiorQueOFim() {
            assertThatThrownBy(() -> ByteRange.parse("bytes=100-50", TOTAL, ONE_MB))
                    .isInstanceOf(InvalidRangeException.class);
        }

        @Test
        void valoresNaoNumericos() {
            assertThatThrownBy(() -> ByteRange.parse("bytes=abc-def", TOTAL, ONE_MB))
                    .isInstanceOf(InvalidRangeException.class);
        }

        @Test
        void semHifen() {
            assertThatThrownBy(() -> ByteRange.parse("bytes=10", TOTAL, ONE_MB))
                    .isInstanceOf(InvalidRangeException.class);
        }

        @Test
        void sufixoZero() {
            assertThatThrownBy(() -> ByteRange.parse("bytes=-0", TOTAL, ONE_MB))
                    .isInstanceOf(InvalidRangeException.class);
        }

        @Test
        void rangeEmRecursoVazio() {
            assertThatThrownBy(() -> ByteRange.parse("bytes=0-0", 0, ONE_MB))
                    .isInstanceOf(InvalidRangeException.class);
        }

        @Test
        void expoeTamanhoTotalParaOHeaderContentRange() {
            assertThatThrownBy(() -> ByteRange.parse("bytes=999999-", TOTAL, ONE_MB))
                    .isInstanceOfSatisfying(InvalidRangeException.class,
                            ex -> assertThat(ex.getTotalSize()).isEqualTo(TOTAL));
        }

        @Test
        void construtorValidaLimites() {
            assertThatIllegalArgumentException().isThrownBy(() -> new ByteRange(10, 5, TOTAL));
            assertThatIllegalArgumentException().isThrownBy(() -> new ByteRange(0, TOTAL, TOTAL));
            assertThatIllegalArgumentException().isThrownBy(() -> new ByteRange(0, 10, -1));
        }

        @Test
        void contentRangeNaoSatisfazivel() {
            assertThat(ByteRange.unsatisfiedContentRange(TOTAL)).isEqualTo("bytes */5000");
        }
    }
}
