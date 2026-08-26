package br.com.tabelaprecos.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * As descrições aqui são linhas reais da tabela, com os preços trocados pelos
 * mesmos valores fictícios da fixture. São de propósito as mais feias que
 * existem no arquivo: truncadas, com typo, com campo faltando.
 */
class DescricaoParserTest {

    static Stream<Arguments> linhasDeVerdade() {
        return Stream.of(
                Arguments.of(
                        "acessório Apple, marca antes do tipo",
                        "112818-5 APPLE FONE AIRPODS 4 MXP63LL/A WHITE /UNI 103.00 |",
                        "112818-5", TipoProduto.FONE, Marca.APPLE, "AIRPODS 4 MXP63LL/A",
                        null, null, "WHITE", "103.00"),
                Arguments.of(
                        "relógio Apple",
                        "127613-8 APPLE WATCH S11 GPS 42MM MEU04LW/A ALIMINUM LIGHT BLUSH R/UNI 271.00 |",
                        "127613-8", TipoProduto.RELOGIO, Marca.APPLE, "S11 GPS 42MM MEU04LW/A",
                        null, null, "BLUSH", "271.00"),
                Arguments.of(
                        "iPhone: armazenamento antes da RAM",
                        "96086-1 CEL APPLE IPHONE 13 PRO MAX 128GB/6 A2484 LL 6.7\" GOLD CP/UNI 561.00 |",
                        "96086-1", TipoProduto.CELULAR, Marca.APPLE, "IPHONE 13 PRO MAX",
                        "128GB", "6GB", "GOLD", "561.00"),
                Arguments.of(
                        "notebook: RAM antes do SSD",
                        "102866-9 NB APPLE M3 MAX MRX83LL/A 36GB/1TB/14\" - SILVER *2023* /UNI 742.00 |",
                        "102866-9", TipoProduto.NOTEBOOK, Marca.APPLE, "M3 MAX MRX83LL/A",
                        "1TB", "36GB", "SILVER", "742.00"),
                Arguments.of(
                        "notebook com capacidade abreviada",
                        "134775-3 NB APPLE M5 AIR MDHD4LL/A 24G/1T SSD/13.6\" STARLIGHT /UNI 388.00 |",
                        "134775-3", TipoProduto.NOTEBOOK, Marca.APPLE, "M5 AIR MDHD4LL/A",
                        "1TB", "24GB", "STARLIGHT", "388.00"),
                Arguments.of(
                        "Samsung: modelo para no código SM-",
                        "132550-8 CEL SAMSUNG A07 SM-A075F DS/64GB/4 6.7\" BLACK SLIM /UNI 618.00 |",
                        "132550-8", TipoProduto.CELULAR, Marca.SAMSUNG, "A07",
                        "64GB", "4GB", "BLACK", "618.00"),
                Arguments.of(
                        "5G é rede, não capacidade",
                        "137731-6 CEL SAMSUNG S25+ SM-S936W 5G DS/512GB/12 6.6\" CORAL RED S/UNI 322.00 |",
                        "137731-6", TipoProduto.CELULAR, Marca.SAMSUNG, "S25+",
                        "512GB", "12GB", "CORAL", "322.00"),
                Arguments.of(
                        "Motorola",
                        "139913-4 CEL MOTOROLA G06 XT-2535-2 DS/128GB/4 6.9\" BLUE (US) /UNI 233.00 |",
                        "139913-4", TipoProduto.CELULAR, Marca.MOTOROLA, "G06 XT-2535-2",
                        "128GB", "4GB", "BLUE", "233.00"),
                Arguments.of(
                        "tablet: RAM antes do armazenamento",
                        "134823-1 TABLET XIAOMI MI PAD 8 8GB/256GB 11.2\" WIFI BLUE BR /UNI 411.00 |",
                        "134823-1", TipoProduto.TABLET, Marca.XIAOMI, "MI PAD 8",
                        "256GB", "8GB", "BLUE", "411.00"),
                Arguments.of(
                        "tablet com RAM explícita",
                        "138399-7 TABLET XIAOMI POCO PAD C1 4RAM/64GB 9.7\" WIFI BLUE BR /UNI 155.00 |",
                        "138399-7", TipoProduto.TABLET, Marca.XIAOMI, "POCO PAD C1",
                        "64GB", "4GB", "BLUE", "155.00"),
                Arguments.of(
                        "descrição truncada, sem espaço antes do /UNI",
                        "133958-1 CEL XIAOMI POCO X8 PRO 5G DS/256GB/8 6.59\" 4K GREEN GLOBA/UNI 502.00 |",
                        "133958-1", TipoProduto.CELULAR, Marca.XIAOMI, "POCO X8 PRO",
                        "256GB", "8GB", "GREEN", "502.00"),
                Arguments.of(
                        "semi novo: separador é só / e o preço tem centavos",
                        "6999-1 SWAP IPHONE 13 128GB AZUL A (AMERICANO) / 611.00 |",
                        "6999-1", TipoProduto.SEMINOVO, Marca.APPLE, "IPHONE 13",
                        "128GB", null, "AZUL", "611.00"),
                Arguments.of(
                        "semi novo truncado no meio do (AMERICANO)",
                        "14767-5 SWAP IPHONE 14 PRO MAX 128GB ESIM A GOLD TELA TROCADA (AM/ 322.00 |",
                        "14767-5", TipoProduto.SEMINOVO, Marca.APPLE, "IPHONE 14 PRO MAX",
                        "128GB", null, "GOLD", "322.00"),
                Arguments.of(
                        "semi novo com typo em AMERICANO e sem parênteses",
                        "14329-5 SWAP IPHONE 15 128GB ESIM A- AZUL AMRICANO / 862.00 |",
                        "14329-5", TipoProduto.SEMINOVO, Marca.APPLE, "IPHONE 15",
                        "128GB", null, "AZUL", "862.00"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("linhasDeVerdade")
    void interpreta(
            String caso,
            String linha,
            String sku,
            TipoProduto tipo,
            Marca marca,
            String modelo,
            String armazenamento,
            String ram,
            String cor,
            String custo)
            throws Exception {

        Descricao d = DescricaoParser.parse(linha);

        assertThat(d.sku()).isEqualTo(sku);
        assertThat(d.tipo()).isEqualTo(tipo);
        assertThat(d.marca()).isEqualTo(marca);
        assertThat(d.modelo()).isEqualTo(modelo);
        assertThat(d.armazenamento()).isEqualTo(armazenamento);
        assertThat(d.memoriaRam()).isEqualTo(ram);
        assertThat(d.cor()).isEqualTo(cor);
        assertThat(d.custoUsd()).isEqualByComparingTo(new BigDecimal(custo));
    }

    @Nested
    @DisplayName("campos só dos semi novos")
    class SemiNovos {

        @Test
        void extrai_grade_e_tela_trocada() throws Exception {
            Descricao d = DescricaoParser.parse(
                    "14767-5 SWAP IPHONE 14 PRO MAX 128GB ESIM A GOLD TELA TROCADA (AM/ 322.00 |");

            assertThat(d.grade()).isEqualTo("A");
            assertThat(d.telaTrocada()).isTrue();
            assertThat(d.esim()).isTrue();
        }

        @Test
        void distingue_grade_A_de_grade_A_menos() throws Exception {
            assertThat(DescricaoParser.parse("14329-5 SWAP IPHONE 15 128GB ESIM A- AZUL AMRICANO / 862.00 |")
                    .grade())
                    .isEqualTo("A-");
        }

        @Test
        void nao_confunde_AMERICANO_com_grade() throws Exception {
            assertThat(DescricaoParser.parse("9999-9 SWAP IPHONE 13 128GB AZUL (AMERICANO) / 100.00 |")
                    .grade())
                    .isNull();
        }

        @Test
        void produto_lacrado_nao_tem_grade() throws Exception {
            assertThat(DescricaoParser.parse(
                            "132550-8 CEL SAMSUNG A07 SM-A075F DS/64GB/4 6.7\" BLACK SLIM /UNI 618.00 |")
                    .grade())
                    .isNull();
        }
    }

    @Nested
    @DisplayName("linhas que não são produto")
    class NaoSaoProduto {

        @ParameterizedTest
        @ValueSource(strings = {"MOTOROLA", "REALME ", "", "   ", "APPLE LACRADO 25-08"})
        void nao_tem_sku(String separador) {
            assertThat(DescricaoParser.temSku(separador)).isFalse();
        }

        @Test
        void linha_de_produto_tem_sku() {
            assertThat(DescricaoParser.temSku("6999-1 SWAP IPHONE 13 128GB AZUL A (AMERICANO) / 611.00 |"))
                    .isTrue();
        }

        @Test
        void recusa_separador_com_motivo() {
            assertThatThrownBy(() -> DescricaoParser.parse("MOTOROLA"))
                    .isInstanceOf(DescricaoInvalidaException.class)
                    .hasMessageContaining("SKU");
        }

        @Test
        void recusa_linha_com_sku_e_sem_preco() {
            assertThatThrownBy(() -> DescricaoParser.parse("132550-8 CEL SAMSUNG A07 SM-A075F BLACK SLIM |"))
                    .isInstanceOf(DescricaoInvalidaException.class)
                    .hasMessageContaining("preço");
        }
    }

    @Test
    void preserva_o_texto_original_inclusive_a_barra_do_fim() throws Exception {
        String linha = "6999-1 SWAP IPHONE 13 128GB AZUL A (AMERICANO) / 611.00 |";
        assertThat(DescricaoParser.parse(linha).textoOriginal()).isEqualTo(linha);
    }

    @Nested
    @DisplayName("cor, que o truncamento estraga")
    class Cor {

        @Test
        void completa_a_cor_cortada_quando_só_uma_serve() throws Exception {
            assertThat(DescricaoParser.parse(
                            "134082-2 CEL APPLE IPHONE 16 128GB/8 A3287 6.1\" PURP/UNI 100.00 |")
                    .cor())
                    .isEqualTo("PURPLE");
        }

        @Test
        void desiste_quando_o_corte_fica_no_meio_de_duas_cores() throws Exception {
            // "BLU" tanto pode virar BLUE quanto BLUSH, que existe nas pulseiras
            // do Apple Watch. Acontece em 5 linhas da tabela de 25/08.
            assertThat(DescricaoParser.parse(
                            "134082-2 CEL APPLE IPHONE 16 128GB/8 A3287 6.1\" BLU/UNI 100.00 |")
                    .cor())
                    .isNull();
        }

        @Test
        void nao_chuta_quando_o_pedaço_serve_a_mais_de_uma_cor() throws Exception {
            // "GR" pode virar GREEN, GREY ou GRAY — cor errada no catálogo é
            // troca de produto na entrega, então fica sem cor.
            assertThat(DescricaoParser.parse(
                            "134083-9 APPLE WATCH S11 GPS 42MM MEQW4LW/A ALIMINUM CASE SPACE GR/UNI 100.00 |")
                    .cor())
                    .isNull();
        }

        @Test
        void entende_a_abreviacao_do_fornecedor() throws Exception {
            assertThat(DescricaoParser.parse(
                            "131096-2 APPLE WATCH ULTRA 3 CEL+GPS 49MM MF1H4AF/A BLK W BLK-CHRC/UNI 100.00 |")
                    .cor())
                    .isEqualTo("BLACK");
        }

        @Test
        void acha_a_cor_grudada_no_parentese() throws Exception {
            assertThat(DescricaoParser.parse(
                            "8933-3 SWAP IPHONE 14 PRO MAX 256GB ESIM A- PURPLE(AMERICANO) / 100.00 |")
                    .cor())
                    .isEqualTo("PURPLE");
        }
    }

    @Test
    void guarda_os_centavos_que_a_coluna_B_descarta() throws Exception {
        Descricao d = DescricaoParser.parse(
                "121462-8 CEL XIAOMI REDMI 14C DS/128GB/4 6.74\" GREEN CH (US) 5099 /UNI 109.75 |");

        assertThat(d.custoUsd()).isEqualByComparingTo(new BigDecimal("109.75"));
    }
}
