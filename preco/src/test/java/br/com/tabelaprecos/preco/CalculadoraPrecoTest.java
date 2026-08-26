package br.com.tabelaprecos.preco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CalculadoraPrecoTest {

    /** A mesma constante que está chumbada em toda fórmula da planilha. */
    private static final BigDecimal COTACAO = new BigDecimal("5.23");

    private final CalculadoraPreco calculadora = new CalculadoraPreco(COTACAO);

    @Nested
    @DisplayName("o preço base, que é o número da planilha")
    class PrecoBase {

        @ParameterizedTest(name = "{0} USD, markup {1}, frete {2} -> R$ {3}")
        @CsvSource({
            // Linhas conferidas na TABELA GERAL 25-08, aba por aba.
            "121, 0.15, 30,  757.75",   // AirPods 4, acessório Apple
            "185, 0.15, 30, 1142.68",   // AirPods 4 com cancelamento de ruído
            "605, 0.07, 60, 3445.64",   // iPhone 13 Pro Max
            "2990, 0.10, 150, 17351.47", // MacBook M3 Max
            "325, 0.12, 80, 1983.72",   // tablet Xiaomi
            "106, 0.15, 50,  687.54"    // Samsung A07
        })
        void reproduz_a_conta_do_fornecedor(
                String custo, String markup, String frete, String esperado) {

            PrecoCalculado preco = calculadora.calcular(
                    new BigDecimal(custo),
                    new BigDecimal(markup),
                    new BigDecimal(frete),
                    AjustesComerciais.NENHUM);

            assertThat(preco.precoBaseBrl()).isEqualByComparingTo(new BigDecimal(esperado));
        }

        @Test
        @DisplayName("semi novo não tem markup: a margem já está no frete")
        void sem_markup_o_preco_e_base_mais_frete() {
            PrecoCalculado preco = calculadora.calcular(
                    new BigDecimal("288"), null, new BigDecimal("300"), AjustesComerciais.NENHUM);

            assertThat(preco.baseBrl()).isEqualByComparingTo("1506.24");
            assertThat(preco.markupBrl()).isEqualByComparingTo("0.00");
            assertThat(preco.precoBaseBrl()).isEqualByComparingTo("1806.24");
        }

        @Test
        void abre_a_conta_parcela_por_parcela() {
            PrecoCalculado preco = calculadora.calcular(
                    new BigDecimal("121"),
                    new BigDecimal("0.15"),
                    new BigDecimal("30"),
                    AjustesComerciais.NENHUM);

            assertThat(preco.custoUsd()).isEqualByComparingTo("121");
            assertThat(preco.cotacao()).isEqualByComparingTo("5.23");
            assertThat(preco.baseBrl()).isEqualByComparingTo("632.83");
            assertThat(preco.markupBrl()).isEqualByComparingTo("94.92");
            assertThat(preco.freteBrl()).isEqualByComparingTo("30.00");
            assertThat(preco.precoBaseBrl()).isEqualByComparingTo("757.75");
        }

        @Test
        @DisplayName("sem ajuste comercial, o preço final é o preço base")
        void sem_ajustes_o_final_e_o_base() {
            PrecoCalculado preco = calculadora.calcular(
                    new BigDecimal("605"),
                    new BigDecimal("0.07"),
                    new BigDecimal("60"),
                    AjustesComerciais.NENHUM);

            assertThat(preco.precoFinalBrl()).isEqualByComparingTo(preco.precoBaseBrl());
            assertThat(preco.descontoBrl()).isEqualByComparingTo("0.00");
            assertThat(preco.acrescimosBrl()).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("ajustes comerciais, que a planilha não tem")
    class Ajustes {

        @Test
        void desconto_sai_do_preco_base() {
            PrecoCalculado preco = calculadora.calcular(
                    new BigDecimal("121"),
                    new BigDecimal("0.15"),
                    new BigDecimal("30"),
                    AjustesComerciais.desconto("0.10"));

            assertThat(preco.precoBaseBrl()).isEqualByComparingTo("757.75");
            assertThat(preco.descontoBrl()).isEqualByComparingTo("75.78");
            assertThat(preco.precoFinalBrl()).isEqualByComparingTo("681.97");
            // O que a tela mostra fecha: 757,75 - 75,78 = 681,97.
            assertThat(preco.precoBaseBrl().subtract(preco.descontoBrl()))
                    .isEqualByComparingTo(preco.precoFinalBrl());
        }

        @Test
        @DisplayName("o parcelamento incide sobre o valor já negociado, não sobre o preço cheio")
        void acrescimo_vem_depois_do_desconto() {
            PrecoCalculado comAmbos = calculadora.calcular(
                    new BigDecimal("121"),
                    new BigDecimal("0.15"),
                    new BigDecimal("30"),
                    new AjustesComerciais(
                            new BigDecimal("0.04"), BigDecimal.ZERO, new BigDecimal("0.10")));

            // 757,75 - 10% = 681,97; 4% disso = 27,2788 -> 27,28.
            assertThat(comAmbos.acrescimosBrl()).isEqualByComparingTo("27.28");
            assertThat(comAmbos.precoFinalBrl()).isEqualByComparingTo("709.25");

            // Se a ordem fosse a inversa, o acréscimo sairia de 757.75 e daria
            // R$ 30.31 — quase três reais a mais no bolso do cliente.
            BigDecimal seFosseSobreOPrecoCheio =
                    comAmbos.precoBaseBrl().multiply(new BigDecimal("0.04"));
            assertThat(seFosseSobreOPrecoCheio).isGreaterThan(comAmbos.acrescimosBrl());
        }

        @Test
        void taxa_de_cartao_e_juros_somam() {
            PrecoCalculado preco = calculadora.calcular(
                    new BigDecimal("100"),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    AjustesComerciais.cartao("0.03", "0.02"));

            assertThat(preco.precoBaseBrl()).isEqualByComparingTo("523.00");
            assertThat(preco.acrescimosBrl()).isEqualByComparingTo("26.15");
            assertThat(preco.precoFinalBrl()).isEqualByComparingTo("549.15");
        }

        @Test
        void desconto_de_100_por_cento_zera_o_preco() {
            PrecoCalculado preco = calculadora.calcular(
                    new BigDecimal("121"),
                    new BigDecimal("0.15"),
                    new BigDecimal("30"),
                    AjustesComerciais.desconto("1"));

            assertThat(preco.precoFinalBrl()).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("entrada inválida para antes de virar preço")
    class Validacao {

        @Test
        void cotacao_zero_ou_negativa_e_recusada() {
            assertThatThrownBy(() -> new CalculadoraPreco(BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cotação");
            assertThatThrownBy(() -> new CalculadoraPreco(new BigDecimal("-5.23")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void desconto_acima_de_cem_por_cento_e_recusado() {
            assertThatThrownBy(() -> AjustesComerciais.desconto("1.5"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("100%");
        }

        @Test
        void percentual_negativo_e_recusado() {
            assertThatThrownBy(() -> AjustesComerciais.desconto("-0.1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negativo");
        }

        @Test
        void custo_negativo_e_recusado() {
            assertThatThrownBy(() -> calculadora.calcular(
                            new BigDecimal("-1"),
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            AjustesComerciais.NENHUM))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("custo");
        }

        @Test
        void frete_nulo_conta_como_zero() {
            PrecoCalculado preco = calculadora.calcular(
                    new BigDecimal("100"), BigDecimal.ZERO, null, AjustesComerciais.NENHUM);

            assertThat(preco.precoBaseBrl()).isEqualByComparingTo("523.00");
        }
    }

    @Nested
    @DisplayName("arredondamento")
    class Arredondamento {

        @Test
        void todo_valor_devolvido_tem_duas_casas() {
            PrecoCalculado preco = calculadora.calcular(
                    new BigDecimal("109.75"),
                    new BigDecimal("0.12"),
                    new BigDecimal("50"),
                    AjustesComerciais.desconto("0.07"));

            assertThat(preco.baseBrl().scale()).isEqualTo(2);
            assertThat(preco.markupBrl().scale()).isEqualTo(2);
            assertThat(preco.precoBaseBrl().scale()).isEqualTo(2);
            assertThat(preco.descontoBrl().scale()).isEqualTo(2);
            assertThat(preco.precoFinalBrl().scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("o meio centavo sobe")
        void meio_centavo_arredonda_para_cima() {
            // 0.5 USD × 5.23 = 2.615 -> 2.62
            PrecoCalculado preco = calculadora.calcular(
                    new BigDecimal("0.50"), BigDecimal.ZERO, BigDecimal.ZERO, AjustesComerciais.NENHUM);

            assertThat(preco.precoBaseBrl()).isEqualByComparingTo("2.62");
        }

        @Test
        @DisplayName("as parcelas arredondadas podem não somar o total, e o total é que vale")
        void o_total_nao_e_a_soma_dos_arredondados() {
            PrecoCalculado preco = calculadora.calcular(
                    new BigDecimal("121"),
                    new BigDecimal("0.15"),
                    new BigDecimal("30"),
                    AjustesComerciais.NENHUM);

            // 632.83 + 94.9245 + 30 = 757.7545, que vira 757.75. Somar as
            // parcelas já arredondadas daria 757.75 também, mas a garantia é
            // que o total sai da conta inteira, não da soma das partes.
            assertThat(preco.precoBaseBrl())
                    .isEqualByComparingTo(new BigDecimal("757.7545").setScale(2, java.math.RoundingMode.HALF_UP));
        }
    }
}
