package br.com.tabelaprecos.lambda

import java.math.BigDecimal
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class JsonTest {

    private val json = Json()

    private fun item(descricao: String = "112818-5 APPLE FONE /UNI 103.00 |") = ItemDaCarga(
        sku = "112818-5",
        descricaoRaw = descricao,
        marca = "APPLE",
        tipo = "FONE",
        modelo = "AIRPODS 4",
        armazenamento = null,
        memoriaRam = null,
        cor = "WHITE",
        grade = null,
        categoria = "APPLE LACRADO 25-08",
        custoUsd = BigDecimal("103.00"),
        markup = BigDecimal("0.1500"),
        freteBrl = BigDecimal("30.00"),
        precoBaseBrl = BigDecimal("649.49"),
    )

    private fun carga(vararg itens: ItemDaCarga) = Carga(
        origem = "tabelas/a.xlsx",
        dataReferencia = LocalDate.of(2026, 8, 25),
        cotacao = BigDecimal("5.23"),
        linhasIgnoradas = 2,
        linhasComErro = 0,
        itens = itens.toList(),
    )

    @Test
    fun `escreve o contrato que a API espera`() {
        val escrito = json.escrever(carga(item()))

        assertThat(escrito)
            .contains("\"origem\":\"tabelas/a.xlsx\"")
            .contains("\"dataReferencia\":\"2026-08-25\"")
            .contains("\"cotacao\":5.23")
            .contains("\"linhasIgnoradas\":2")
            .contains("\"sku\":\"112818-5\"")
            .contains("\"precoBaseBrl\":649.49")
    }

    @Test
    @DisplayName("campo ausente vira null, e não a palavra null entre aspas")
    fun `nulo e nulo de verdade`() {
        val escrito = json.escrever(carga(item()))

        assertThat(escrito).contains("\"armazenamento\":null").doesNotContain("\"null\"")
    }

    @Test
    @DisplayName("aspas na descrição do fornecedor não podem quebrar o JSON")
    fun `escapa o que precisa`() {
        // A planilha tem polegada escrita com aspas: 6.7" BLACK.
        val escrito = json.escrever(carga(item("112818-5 CEL 6.7\" BLACK /UNI 103.00 |")))

        assertThat(escrito).contains("6.7\\\" BLACK")
        assertThat(json.texto(escrito, "sku")).isEqualTo("112818-5")
    }

    @Test
    fun `numero sai sem notacao cientifica`() {
        val gigante = item().copy(precoBaseBrl = BigDecimal("0.00000100"))

        assertThat(json.escrever(carga(gigante))).contains("0.00000100").doesNotContain("E-")
    }

    @Test
    fun `le os campos que a API devolve`() {
        assertThat(json.texto("""{"token":"abc.def","expiraEm":"2026-01-01"}""", "token"))
            .isEqualTo("abc.def")
        assertThat(json.texto("""{"valorBrl":5.4100,"atualizadoEm":"x"}""", "valorBrl"))
            .isEqualTo("5.4100")
    }

    @Test
    fun `reclama quando o campo esperado nao veio`() {
        assertThatThrownBy { json.texto("""{"erro":"sem token"}""", "token") }
            .isInstanceOf(FalhaDaApi::class.java)
            .hasMessageContaining("token")
    }
}
