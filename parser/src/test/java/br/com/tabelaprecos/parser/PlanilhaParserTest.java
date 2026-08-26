package br.com.tabelaprecos.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Roda contra {@code tabela-exemplo.xlsx}, gerado por {@code tools/anonimizar.py}
 * a partir do arquivo real: mesma estrutura, mesmas 907 linhas, mesmos defeitos,
 * preços fictícios.
 */
class PlanilhaParserTest {

    private static final int LINHAS_NA_PLANILHA = 907;
    private static final int SEPARADORES = 2;
    private static final int PRODUTOS = LINHAS_NA_PLANILHA - SEPARADORES;
    private static final int ANO = 2026;

    private static ResultadoImportacao resultado;

    @BeforeAll
    static void lerAPlanilhaUmaVezSo() throws IOException {
        try (InputStream entrada = fixture()) {
            resultado = new PlanilhaParser().ler(entrada, ANO);
        }
    }

    private static InputStream fixture() {
        InputStream entrada = PlanilhaParserTest.class.getResourceAsStream("/tabela-exemplo.xlsx");
        assertThat(entrada).as("fixture tabela-exemplo.xlsx").isNotNull();
        return entrada;
    }

    @Test
    void le_todos_os_produtos_das_quatro_abas() {
        assertThat(resultado.total()).isEqualTo(PRODUTOS);
        assertThat(resultado.abas()).extracting(ResumoAba::nome)
                .containsExactly(
                        "APPLE LACRADO 25-08",
                        "XIAOMI E REALME 25-08",
                        "SEMI NOVOS 25-08",
                        "SAMSUNG E MOTOROLA 25-08");
    }

    @Test
    void nao_registra_nenhum_erro() {
        assertThat(resultado.erros()).isEmpty();
        assertThat(resultado.temErro()).isFalse();
    }

    @Test
    @DisplayName("cabeçalhos de seção viram descarte, não erro")
    void separadores_sao_ignorados() {
        assertThat(resultado.ignoradas())
                .hasSize(SEPARADORES)
                .extracting(LinhaIgnorada::conteudo)
                .containsExactlyInAnyOrder("REALME", "MOTOROLA");
        assertThat(resultado.ignoradas())
                .allSatisfy(linha -> assertThat(linha.motivo()).isEqualTo("cabeçalho de seção"));
    }

    @Test
    @DisplayName("a linha MOTOROLA tem #VALUE! em três colunas e mesmo assim não derruba a leitura")
    void celula_com_erro_de_formula_nao_quebra() {
        List<LinhaIgnorada> motorola = resultado.ignoradas().stream()
                .filter(linha -> linha.conteudo().equals("MOTOROLA"))
                .toList();

        assertThat(motorola).hasSize(1);
        assertThat(motorola.get(0).aba()).isEqualTo("SAMSUNG E MOTOROLA 25-08");
    }

    @Test
    void toda_linha_da_planilha_foi_classificada() {
        assertThat(resultado.total() + resultado.erros().size() + resultado.ignoradas().size())
                .isEqualTo(LINHAS_NA_PLANILHA);
    }

    @Nested
    @DisplayName("arranjo de colunas")
    class Layout {

        @Test
        void abas_de_lacrado_tem_markup() {
            assertThat(resultado.abas())
                    .filteredOn(aba -> !aba.nome().startsWith("SEMI NOVOS"))
                    .allSatisfy(aba -> assertThat(aba.layout()).isEqualTo(LayoutAba.COM_MARKUP));
        }

        @Test
        void aba_de_semi_novos_nao_tem_markup() {
            List<ItemPlanilha> semiNovos = resultado.itensDa("SEMI NOVOS 25-08");

            assertThat(semiNovos).isNotEmpty();
            assertThat(semiNovos).allSatisfy(item -> {
                assertThat(item.layout()).isEqualTo(LayoutAba.SEMI_NOVOS);
                assertThat(item.markup()).isNull();
                assertThat(item.freteBrl()).isGreaterThanOrEqualTo(new BigDecimal("300.00"));
            });
        }

        @Test
        void markup_das_abas_de_lacrado_fica_entre_7_e_15_por_cento() {
            assertThat(resultado.itens())
                    .filteredOn(item -> item.layout() == LayoutAba.COM_MARKUP)
                    .allSatisfy(item -> assertThat(item.markup())
                            .isBetween(new BigDecimal("0.0700"), new BigDecimal("0.1500")));
        }
    }

    @Nested
    @DisplayName("custo em dólar")
    class Custo {

        @Test
        @DisplayName("a coluna B é sempre o piso do preço que está na descrição")
        void coluna_B_descarta_os_centavos() {
            assertThat(resultado.itens()).allSatisfy(item -> assertThat(item.custoUsdColuna())
                    .isEqualByComparingTo(item.custoUsd().setScale(0, java.math.RoundingMode.FLOOR)));
        }

        @Test
        void a_divergencia_de_centavos_e_visivel_e_minoritaria() {
            long divergentes = resultado.itens().stream()
                    .filter(ItemPlanilha::centavosDescartados)
                    .count();

            assertThat(divergentes).isEqualTo(153);
        }

        @Test
        void nenhum_custo_e_zero_ou_negativo() {
            assertThat(resultado.itens())
                    .allSatisfy(item -> assertThat(item.custoUsd()).isPositive());
        }
    }

    @Nested
    @DisplayName("classificação dos produtos")
    class Classificacao {

        @Test
        void toda_linha_tem_sku_e_tipo_reconhecido() {
            assertThat(resultado.itens()).allSatisfy(item -> {
                assertThat(item.sku()).matches("\\d{3,6}-\\d");
                assertThat(item.descricao().tipo()).isNotEqualTo(TipoProduto.DESCONHECIDO);
            });
        }

        @Test
        void a_marca_e_reconhecida_em_toda_linha() {
            assertThat(resultado.itens())
                    .filteredOn(item -> item.descricao().marca() == Marca.DESCONHECIDA)
                    .isEmpty();
        }

        @Test
        void semi_novos_sao_todos_iphone_usado() {
            assertThat(resultado.itensDa("SEMI NOVOS 25-08")).allSatisfy(item -> {
                assertThat(item.descricao().tipo()).isEqualTo(TipoProduto.SEMINOVO);
                assertThat(item.descricao().marca()).isEqualTo(Marca.APPLE);
            });
        }

        @Test
        @DisplayName("a cor sai em 9 de cada 10 linhas; o resto é truncamento sem conserto")
        void a_cor_e_melhor_esforco() {
            long semCor = resultado.itens().stream()
                    .filter(item -> item.descricao().cor() == null)
                    .count();

            // Medido em 25/08: 96 linhas. Ou o texto foi cortado cedo demais
            // ("BL", "GR"), ou o produto não tem cor mesmo (AirPods, Pencil).
            assertThat(semCor).isLessThan(resultado.total() * 12 / 100);
        }

        @Test
        @DisplayName("toda cor devolvida vem do vocabulário conhecido")
        void a_cor_nunca_e_um_pedaço_solto_do_texto() {
            assertThat(resultado.itens())
                    .filteredOn(item -> item.descricao().cor() != null)
                    .allSatisfy(item -> assertThat(DescricaoParser.coresConhecidas())
                            .contains(item.descricao().cor()));
        }
    }

    @Test
    void a_data_de_referencia_vem_do_nome_da_aba() {
        assertThat(resultado.dataReferencia()).contains(LocalDate.of(ANO, 8, 25));
        assertThat(resultado.abas())
                .allSatisfy(aba -> assertThat(aba.dataReferencia()).isEqualTo(LocalDate.of(ANO, 8, 25)));
    }

    @Test
    void guarda_a_aba_e_a_linha_de_origem_de_cada_item() {
        ItemPlanilha primeiro = resultado.itens().get(0);

        assertThat(primeiro.aba()).isEqualTo("APPLE LACRADO 25-08");
        assertThat(primeiro.linha()).isEqualTo(2);
    }
}
