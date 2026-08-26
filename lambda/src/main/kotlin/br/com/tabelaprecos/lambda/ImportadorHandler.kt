package br.com.tabelaprecos.lambda

import br.com.tabelaprecos.parser.PlanilhaParser
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.S3Event
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

/**
 * Acordada pelo S3 quando uma planilha aparece no bucket.
 *
 *
 * Lê o arquivo, calcula a tabela do dia e entrega para a API. Não escreve
 * no banco: quem escreve é a API, dona do esquema.
 *
 * @param leitor de onde vêm os bytes da planilha
 * @param api para onde vai o resultado
 * @param montador transforma o que o parser leu no corpo que a API espera
 * @param hoje injetável para o teste não depender da data em que roda
 */
class ImportadorHandler(
    private val leitor: LeitorDeObjeto,
    private val api: ApiDeCatalogo,
    private val montador: MontadorDaCarga = MontadorDaCarga(),
    private val hoje: () -> LocalDate = LocalDate::now,
) : RequestHandler<S3Event, String> {

    /** Construtor que o runtime da AWS usa; monta tudo a partir do ambiente. */
    @Suppress("unused")
    constructor() : this(LeitorDoS3(), ClienteDaApi.doAmbiente())

    /**
     * Processa cada objeto do evento.
     *
     * @param evento pode trazer mais de um registro quando vários arquivos
     *     caem no bucket ao mesmo tempo
     * @param contexto nulo nos testes; na AWS traz o logger da função
     * @return um resumo por arquivo, que aparece no log da invocação
     * @throws FalhaDaApi se a API recusar, para a invocação ser reprocessada
     */
    override fun handleRequest(evento: S3Event, contexto: Context?): String {
        val resumos = evento.records.map { registro ->
            val bucket = registro.s3.bucket.name
            // O S3 entrega a chave com escape de URL: "TABELA+GERAL.xlsx" só
            // vira o nome real depois de decodificar, senão o getObject falha
            // com "no such key" para todo arquivo com espaço no nome. O evento
            // já traz a versão decodificada — decodificar de novo por cima
            // estragaria nome que contenha % ou + de verdade.
            val chave = registro.s3.`object`.urlDecodedKey
                ?: URLDecoder.decode(registro.s3.`object`.key, StandardCharsets.UTF_8)
            processar(bucket, chave, contexto)
        }
        return resumos.joinToString("; ")
    }

    private fun processar(bucket: String, chave: String, contexto: Context?): String {
        val cotacao = api.cotacaoAtual()
        val resultado = leitor.abrir(bucket, chave).use { PlanilhaParser().ler(it) }

        val carga = montador.montar(resultado, chave, cotacao, hoje())
        api.registrar(carga)

        val resumo = "$chave: ${carga.itens.size} itens, " +
            "${carga.linhasIgnoradas} ignoradas, ${carga.linhasComErro} com erro, " +
            "cotação $cotacao"
        contexto?.logger?.log(resumo)
        return resumo
    }
}
