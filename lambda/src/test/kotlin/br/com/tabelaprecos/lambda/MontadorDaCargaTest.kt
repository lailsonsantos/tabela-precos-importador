package br.com.tabelaprecos.lambda

import br.com.tabelaprecos.parser.PlanilhaParser
import br.com.tabelaprecos.parser.ResultadoImportacao
import br.com.tabelaprecos.preco.PoliticaDeCusto
import java.math.BigDecimal
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MontadorDaCargaTest {

    companion object {

        private lateinit var planilha: ResultadoImportacao

        @JvmStatic
        @BeforeAll
        fun lerAPlanilha() {
            planilha = fixture().use { PlanilhaParser().ler(it, 2026) }
        }
    }

    private val cotacao = BigDecimal("5.23")
    private val hoje = LocalDate.of(2026, 9, 1)

    @Test
    fun `leva todos os itens e as contagens de descarte`() {
        val carga = MontadorDaCarga().montar(planilha, "tabelas/x.xlsx", cotacao, hoje)

        assertThat(carga.itens).hasSize(905)
        assertThat(carga.linhasIgnoradas).isEqualTo(2)
        assertThat(carga.linhasComErro).isZero()
        assertThat(carga.origem).isEqualTo("tabelas/x.xlsx")
        assertThat(carga.cotacao).isEqualByComparingTo("5.23")
    }

    @Test
    @DisplayName("a data vem do nome da aba, não do dia em que a Lambda rodou")
    fun `usa a data de referencia da planilha`() {
        val carga = MontadorDaCarga().montar(planilha, "x.xlsx", cotacao, hoje)

        assertThat(carga.dataReferencia).isEqualTo(LocalDate.of(2026, 8, 25))
    }

    @Test
    fun `sem data na planilha vale hoje`() {
        val vazio = ResultadoImportacao(emptyList(), emptyList(), emptyList(), emptyList())

        val carga = MontadorDaCarga().montar(vazio, "x.xlsx", cotacao, hoje)

        assertThat(carga.dataReferencia).isEqualTo(hoje)
    }

    @Test
    fun `calcula o preco base com a cotacao recebida`() {
        val carga = MontadorDaCarga().montar(planilha, "x.xlsx", cotacao, hoje)
        val item = carga.itens.first { it.sku == "112818-5" }

        // 103 USD x 5,23 = 538,69; mais 15% e mais 30 de frete.
        assertThat(item.custoUsd).isEqualByComparingTo("103.00")
        assertThat(item.markup).isEqualByComparingTo("0.1500")
        assertThat(item.freteBrl).isEqualByComparingTo("30.00")
        assertThat(item.precoBaseBrl).isEqualByComparingTo("649.49")
    }

    @Test
    fun `cotacao maior sobe o preco de todo mundo`() {
        val comDolarCaro = MontadorDaCarga().montar(planilha, "x.xlsx", BigDecimal("6.00"), hoje)
        val comDolarBarato = MontadorDaCarga().montar(planilha, "x.xlsx", cotacao, hoje)

        assertThat(comDolarCaro.itens.first().precoBaseBrl)
            .isGreaterThan(comDolarBarato.itens.first().precoBaseBrl)
    }

    @Test
    @DisplayName("a política de custo muda o que vai para a API")
    fun `com centavos cobra mais que como a planilha`() {
        val comoAPlanilha = MontadorDaCarga().montar(planilha, "x.xlsx", cotacao, hoje)
        val comCentavos = MontadorDaCarga(PoliticaDeCusto.COM_CENTAVOS)
            .montar(planilha, "x.xlsx", cotacao, hoje)

        val diferentes = comoAPlanilha.itens.zip(comCentavos.itens)
            .count { (a, b) -> a.precoBaseBrl.compareTo(b.precoBaseBrl) != 0 }

        assertThat(diferentes).isEqualTo(153)
    }

    @Test
    fun `semi novos vao sem markup`() {
        val carga = MontadorDaCarga().montar(planilha, "x.xlsx", cotacao, hoje)
        val semiNovos = carga.itens.filter { it.categoria.startsWith("SEMI NOVOS") }

        assertThat(semiNovos).isNotEmpty
        assertThat(semiNovos).allSatisfy { assertThat(it.markup).isNull() }
    }

    @Test
    fun `a categoria e o nome da aba de origem`() {
        val carga = MontadorDaCarga().montar(planilha, "x.xlsx", cotacao, hoje)

        assertThat(carga.itens.map { it.categoria }.distinct())
            .containsExactlyInAnyOrder(
                "APPLE LACRADO 25-08",
                "XIAOMI E REALME 25-08",
                "SEMI NOVOS 25-08",
                "SAMSUNG E MOTOROLA 25-08",
            )
    }
}
