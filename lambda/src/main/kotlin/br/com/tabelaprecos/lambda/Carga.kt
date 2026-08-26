package br.com.tabelaprecos.lambda

import java.math.BigDecimal
import java.time.LocalDate

/**
 * O que a API recebe depois que a planilha foi lida.
 *
 * <p>Espelha o contrato de `POST /admin/importacoes`. Fica aqui, e não numa
 * biblioteca compartilhada entre os dois repositórios, porque um contrato HTTP
 * duplicado de propósito é mais barato de manter do que um artefato publicado
 * que precisa subir de versão a cada campo novo.
 */
data class Carga(
    val origem: String,
    val dataReferencia: LocalDate,
    val cotacao: BigDecimal,
    val linhasIgnoradas: Int,
    val linhasComErro: Int,
    val itens: List<ItemDaCarga>,
)

data class ItemDaCarga(
    val sku: String,
    val descricaoRaw: String,
    val marca: String,
    val tipo: String,
    val modelo: String?,
    val armazenamento: String?,
    val memoriaRam: String?,
    val cor: String?,
    val grade: String?,
    val categoria: String,
    val custoUsd: BigDecimal,
    val markup: BigDecimal?,
    val freteBrl: BigDecimal,
    val precoBaseBrl: BigDecimal,
)
