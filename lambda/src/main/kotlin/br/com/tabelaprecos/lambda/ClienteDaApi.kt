package br.com.tabelaprecos.lambda

import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** O que a Lambda precisa da API. Interface para o teste não subir um Spring. */
interface ApiDeCatalogo {

    /**
     * @return quantos reais vale um dólar, segundo a API
     * @throws FalhaDaApi se a API recusar ou não responder
     */
    fun cotacaoAtual(): BigDecimal

    /**
     * Entrega a tabela do dia.
     *
     * @param carga os produtos já lidos e precificados
     * @return o corpo da resposta, com o resumo da importação
     * @throws FalhaDaApi em qualquer status fora da faixa 2xx. A exceção sobe
     *     de propósito: invocação que falha em silêncio nunca é reprocessada.
     */
    fun registrar(carga: Carga): String
}

/**
 * A API recusou ou não respondeu.
 *
 * @param mensagem traz o corpo da resposta junto, porque é ali que a API
 *     explica o que recusou — sem isso o log da função só diria "deu 400"
 */
class FalhaDaApi(mensagem: String) : RuntimeException(mensagem)

/**
 * Fala com a API por HTTP, autenticando com as mesmas credenciais de qualquer
 * administrador.
 *
 *
 * Escrever direto no banco seria mais curto e colocaria dois donos no mesmo
 * esquema: bastaria uma migration para o importador parar de funcionar sem
 * ninguém perceber. Além disso, gravar pela API mantém a Lambda fora da VPC —
 * que é o que evita o NAT Gateway de trinta dólares por mês.
 */
/**
 * @param base endereço da API, com ou sem barra no fim
 * @param email administrador de serviço que a função usa
 * @param senha senha desse administrador
 * @param http trocável no teste, que sobe um servidor local em vez de dublar
 *     o cliente — assim o cabeçalho e o corpo que saem no fio são exercitados
 */
class ClienteDaApi(
    base: String,
    private val email: String,
    private val senha: String,
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
) : ApiDeCatalogo {

    // A Function URL da AWS termina em barra; concatenar o caminho por cima
    // produziria "...//auth/login", que a API responde com 404.
    private val base = base.trimEnd('/')
    private val json = Json()
    private var token: String? = null

    companion object {

        /**
         * Monta o cliente a partir das variáveis de ambiente da função.
         *
         * @return o cliente pronto
         * @throws IllegalStateException nomeando a variável que faltou. A
         *     função falha na inicialização, e não no meio de uma importação.
         */
        fun doAmbiente() = ClienteDaApi(
            base = exigir("API_URL"),
            email = exigir("API_EMAIL"),
            senha = exigir("API_SENHA"),
        )

        private fun exigir(nome: String): String =
            System.getenv(nome)?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException(
                    "Variável de ambiente $nome não configurada na função"
                )
    }

    override fun cotacaoAtual(): BigDecimal {
        val resposta = autenticada("/admin/cotacao").GET().let(::enviar)
        return BigDecimal(json.texto(resposta, "valorBrl"))
    }

    override fun registrar(carga: Carga): String =
        autenticada("/admin/importacoes")
            .POST(HttpRequest.BodyPublishers.ofString(json.escrever(carga)))
            .header("Content-Type", "application/json")
            .let(::enviar)

    private fun entrar(): String = token ?: run {
        val corpo = """{"email":${json.aspas(email)},"senha":${json.aspas(senha)}}"""
        val resposta = enviar(
            HttpRequest.newBuilder(URI.create("$base/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo))
        )
        json.texto(resposta, "token").also { token = it }
    }

    private fun autenticada(caminho: String): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create("$base$caminho"))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer ${entrar()}")

    private fun enviar(pedido: HttpRequest.Builder): String {
        val resposta = http.send(pedido.build(), HttpResponse.BodyHandlers.ofString())
        if (resposta.statusCode() !in 200..299) {
            // O corpo entra na mensagem porque é onde a API explica o que
            // recusou; sem ele, o log da função só diria "deu 400".
            throw FalhaDaApi("API respondeu ${resposta.statusCode()}: ${resposta.body()}")
        }
        return resposta.body()
    }
}
