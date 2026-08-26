package br.com.tabelaprecos.lambda

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Exercita o cliente HTTP de verdade contra um servidor de mentira. Dublar o
 * HttpClient testaria o dublê; um servidor local testa o que sai no fio —
 * cabeçalho de autorização, corpo e tratamento de erro.
 */
class ClienteDaApiTest {

    private lateinit var servidor: HttpServer
    private val recebidos = mutableListOf<Pair<String, String>>()
    private val autorizacoes = mutableListOf<String?>()
    private var respostaDoRegistro = 201 to """{"id":1}"""

    @BeforeEach
    fun subirServidor() {
        servidor = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        servidor.createContext("/auth/login") { troca ->
            registrar(troca)
            responder(troca, 200, """{"token":"token-de-teste","expiraEm":"2099-01-01T00:00:00Z"}""")
        }
        servidor.createContext("/admin/cotacao") { troca ->
            registrar(troca)
            responder(troca, 200, """{"valorBrl":5.4100,"atualizadoEm":"2026-08-26T10:00:00Z"}""")
        }
        servidor.createContext("/admin/importacoes") { troca ->
            registrar(troca)
            responder(troca, respostaDoRegistro.first, respostaDoRegistro.second)
        }
        servidor.start()
    }

    @AfterEach
    fun derrubarServidor() = servidor.stop(0)

    private fun registrar(troca: HttpExchange) {
        recebidos += troca.requestURI.path to troca.requestBody.readBytes().decodeToString()
        autorizacoes += troca.requestHeaders.getFirst("Authorization")
    }

    private fun responder(troca: HttpExchange, status: Int, corpo: String) {
        val bytes = corpo.toByteArray()
        troca.responseHeaders.add("Content-Type", "application/json")
        troca.sendResponseHeaders(status, bytes.size.toLong())
        troca.responseBody.use { it.write(bytes) }
    }

    private fun cliente() = ClienteDaApi(
        base = "http://127.0.0.1:${servidor.address.port}",
        email = "importador@exemplo.com",
        senha = "senha-do-importador",
    )

    private fun carga() = Carga(
        origem = "tabelas/a.xlsx",
        dataReferencia = LocalDate.of(2026, 8, 25),
        cotacao = BigDecimal("5.41"),
        linhasIgnoradas = 2,
        linhasComErro = 0,
        itens = emptyList(),
    )

    @Test
    fun `entra e usa o token nas chamadas seguintes`() {
        val cliente = cliente()

        assertThat(cliente.cotacaoAtual()).isEqualByComparingTo("5.4100")

        assertThat(recebidos.map { it.first }).containsExactly("/auth/login", "/admin/cotacao")
        assertThat(autorizacoes.last()).isEqualTo("Bearer token-de-teste")
    }

    @Test
    @DisplayName("entra uma vez só, mesmo com várias chamadas")
    fun `reaproveita o token`() {
        val cliente = cliente()

        cliente.cotacaoAtual()
        cliente.registrar(carga())

        assertThat(recebidos.count { it.first == "/auth/login" }).isEqualTo(1)
    }

    @Test
    fun `manda a carga como json no corpo`() {
        cliente().registrar(carga())

        val corpo = recebidos.first { it.first == "/admin/importacoes" }.second
        assertThat(corpo)
            .contains("\"origem\":\"tabelas/a.xlsx\"")
            .contains("\"dataReferencia\":\"2026-08-25\"")
    }

    @Test
    @DisplayName("recusa da API vira erro com o corpo junto, senão o log só diria o número")
    fun `propaga a explicacao da api`() {
        respostaDoRegistro = 400 to """{"message":"cotacao precisa ser positiva"}"""

        assertThatThrownBy { cliente().registrar(carga()) }
            .isInstanceOf(FalhaDaApi::class.java)
            .hasMessageContaining("400")
            .hasMessageContaining("cotacao precisa ser positiva")
    }

    @Test
    fun `exige as variaveis de ambiente com nome na mensagem`() {
        assertThatThrownBy { ClienteDaApi.doAmbiente() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("API_URL")
    }
}
