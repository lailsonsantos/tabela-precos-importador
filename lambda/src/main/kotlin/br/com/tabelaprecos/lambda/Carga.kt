package br.com.tabelaprecos.lambda

import java.math.BigDecimal
import java.time.LocalDate

/**
 * O que a API recebe depois que a planilha foi lida.
 *
 *
 * Espelha o contrato de `POST /admin/importacoes`. Fica aqui, e não numa
 * biblioteca compartilhada entre os dois repositórios, porque um contrato HTTP
 * duplicado de propósito é mais barato de manter do que um artefato publicado
 * que precisa subir de versão a cada campo novo.
 *
 * @property origem a chave do objeto no S3, para o admin saber de onde veio
 * @property dataReferencia o dia que a tabela representa, lido do nome da aba
 * @property cotacao quantos reais valia um dólar quando o preço foi calculado
 * @property linhasIgnoradas cabeçalhos de seção e linhas em branco
 * @property linhasComErro linhas que pareciam produto e não puderam ser lidas
 * @property itens os produtos, já com o preço base resolvido
 */
data class Carga(
    val origem: String,
    val dataReferencia: LocalDate,
    val cotacao: BigDecimal,
    val linhasIgnoradas: Int,
    val linhasComErro: Int,
    val itens: List<ItemDaCarga>,
)

/**
 * Um produto dentro da carga.
 *
 * @property sku o código do fornecedor, chave de tudo
 * @property descricaoRaw a linha original da planilha; **contém o custo em
 *     dólar**, então nunca sai para o público — a API guarda separado
 * @property markup nulo nos semi novos, onde a margem já está no frete
 * @property precoBaseBrl o preço de tabela já calculado pelo importador
 */
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
