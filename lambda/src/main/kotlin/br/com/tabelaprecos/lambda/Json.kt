package br.com.tabelaprecos.lambda

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Serialização mínima, escrita à mão.
 *
 *
 * Jackson resolveria isso em uma linha e custaria uns oito megabytes no
 * pacote da função, que viram tempo de partida a frio em toda invocação. Para
 * um contrato de dois formatos de saída e dois campos de entrada, o preço não
 * se paga.
 */
class Json {

    fun escrever(carga: Carga): String = buildString {
        append('{')
        campo("origem", aspas(carga.origem))
        append(',')
        campo("dataReferencia", aspas(carga.dataReferencia.toString()))
        append(',')
        campo("cotacao", carga.cotacao.toPlainString())
        append(',')
        campo("linhasIgnoradas", carga.linhasIgnoradas.toString())
        append(',')
        campo("linhasComErro", carga.linhasComErro.toString())
        append(",\"itens\":[")
        carga.itens.forEachIndexed { indice, item ->
            if (indice > 0) append(',')
            append(escrever(item))
        }
        append("]}")
    }

    private fun escrever(item: ItemDaCarga): String = buildString {
        append('{')
        campo("sku", aspas(item.sku))
        append(',')
        campo("descricaoRaw", aspas(item.descricaoRaw))
        append(',')
        campo("marca", aspas(item.marca))
        append(',')
        campo("tipo", aspas(item.tipo))
        append(',')
        campo("modelo", aspas(item.modelo))
        append(',')
        campo("armazenamento", aspas(item.armazenamento))
        append(',')
        campo("memoriaRam", aspas(item.memoriaRam))
        append(',')
        campo("cor", aspas(item.cor))
        append(',')
        campo("grade", aspas(item.grade))
        append(',')
        campo("categoria", aspas(item.categoria))
        append(',')
        campo("custoUsd", numero(item.custoUsd))
        append(',')
        campo("markup", numero(item.markup))
        append(',')
        campo("freteBrl", numero(item.freteBrl))
        append(',')
        campo("precoBaseBrl", numero(item.precoBaseBrl))
        append('}')
    }

    /** Leitura rasa: serve para os dois campos que a Lambda lê de volta. */
    fun texto(json: String, campo: String): String {
        val achado = Regex("\"$campo\"\\s*:\\s*(?:\"([^\"]*)\"|([0-9.]+))").find(json)
            ?: throw FalhaDaApi("resposta da API sem o campo $campo: $json")
        return achado.groupValues[1].ifEmpty { achado.groupValues[2] }
    }

    fun aspas(valor: String?): String {
        if (valor == null) return "null"
        val escapado = buildString {
            for (caractere in valor) {
                when (caractere) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (caractere < ' ') append("\\u%04x".format(caractere.code))
                    else append(caractere)
                }
            }
        }
        return "\"$escapado\""
    }

    private fun numero(valor: BigDecimal?): String = valor?.toPlainString() ?: "null"

    private fun StringBuilder.campo(nome: String, valor: String) {
        append('"').append(nome).append("\":").append(valor)
    }
}
