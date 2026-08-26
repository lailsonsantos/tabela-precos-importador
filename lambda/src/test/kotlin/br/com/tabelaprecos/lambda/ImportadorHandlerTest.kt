package br.com.tabelaprecos.lambda

import java.io.InputStream
import java.math.BigDecimal
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ImportadorHandlerTest {

    private val api = ApiFalsa()

    private fun handler(
        leitor: LeitorDeObjeto = LeitorDeObjeto { _, _ -> fixture() },
        deCatalogo: ApiDeCatalogo = api,
    ) = ImportadorHandler(leitor, deCatalogo, hoje = { LocalDate.of(2026, 9, 1) })

    @Test
    fun `le a planilha do evento e entrega para a api`() {
        val resumo = handler().handleRequest(
            eventoDoS3("tabelas", "tabelas/2026-08-25/tabela.xlsx"), null
        )

        assertThat(api.recebidas).hasSize(1)
        assertThat(api.recebidas.first().itens).hasSize(905)
        assertThat(resumo).contains("905 itens", "2 ignoradas", "0 com erro", "5.23")
    }

    @Test
    @DisplayName("a cotação vem da API, não de variável de ambiente da função")
    fun `consulta a cotacao antes de calcular`() {
        val comDolarCaro = ApiFalsa(cotacao = BigDecimal("6.10"))

        handler(deCatalogo = comDolarCaro).handleRequest(eventoDoS3("t", "a.xlsx"), null)

        assertThat(comDolarCaro.consultasDeCotacao).isEqualTo(1)
        assertThat(comDolarCaro.recebidas.first().cotacao).isEqualByComparingTo("6.10")
    }

    @Test
    fun `passa o bucket e a chave do evento para o leitor`() {
        var pedidos: Pair<String, String>? = null
        val espiao = LeitorDeObjeto { bucket, chave ->
            pedidos = bucket to chave
            fixture()
        }

        handler(leitor = espiao).handleRequest(
            eventoDoS3("tabelas-do-fornecedor", "tabelas/2026-08-25/abc.xlsx"), null
        )

        assertThat(pedidos).isEqualTo("tabelas-do-fornecedor" to "tabelas/2026-08-25/abc.xlsx")
    }

    @Test
    @DisplayName("chave com espaço chega decodificada, senão o getObject não acha o arquivo")
    fun `decodifica a chave do evento`() {
        var recebida: String? = null
        val espiao = LeitorDeObjeto { _, chave ->
            recebida = chave
            fixture()
        }

        handler(leitor = espiao).handleRequest(
            eventoDoS3("t", "tabelas/TABELA+GERAL+25-08.xlsx"), null
        )

        assertThat(recebida).isEqualTo("tabelas/TABELA GERAL 25-08.xlsx")
    }

    @Test
    fun `usa a chave como origem para o admin saber de onde veio`() {
        handler().handleRequest(eventoDoS3("t", "tabelas/2026-08-25/abc.xlsx"), null)

        assertThat(api.recebidas.first().origem).isEqualTo("tabelas/2026-08-25/abc.xlsx")
    }

    @Test
    @DisplayName("falha da API sobe: a função tem de aparecer como erro para ser reprocessada")
    fun `nao engole falha da api`() {
        val quebrada = ApiFalsa(falhaAoRegistrar = FalhaDaApi("API respondeu 500"))

        assertThatThrownBy {
            handler(deCatalogo = quebrada).handleRequest(eventoDoS3("t", "a.xlsx"), null)
        }
            .isInstanceOf(FalhaDaApi::class.java)
            .hasMessageContaining("500")
    }

    @Test
    fun `arquivo ilegivel falha em vez de mandar carga vazia`() {
        val lixo = LeitorDeObjeto { _, _ -> "isso nao e uma planilha".byteInputStream() }

        assertThatThrownBy { handler(leitor = lixo).handleRequest(eventoDoS3("t", "a.xlsx"), null) }
            .isInstanceOf(Exception::class.java)
        assertThat(api.recebidas).isEmpty()
    }

    @Test
    fun `fecha o objeto do S3 depois de ler`() {
        var fechado = false
        val observado = LeitorDeObjeto { _, _ ->
            object : InputStream() {
                private val original = fixture()
                override fun read() = original.read()
                override fun read(destino: ByteArray, inicio: Int, tamanho: Int) =
                    original.read(destino, inicio, tamanho)

                override fun close() {
                    fechado = true
                    original.close()
                }
            }
        }

        handler(leitor = observado).handleRequest(eventoDoS3("t", "a.xlsx"), null)

        assertThat(fechado).isTrue()
    }
}
