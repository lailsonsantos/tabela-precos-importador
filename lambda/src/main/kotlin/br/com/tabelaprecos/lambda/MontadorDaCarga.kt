package br.com.tabelaprecos.lambda

import br.com.tabelaprecos.parser.ResultadoImportacao
import br.com.tabelaprecos.preco.CalculadoraPreco
import br.com.tabelaprecos.preco.PoliticaDeCusto
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Transforma o que o parser leu no corpo que a API espera.
 *
 * A política de custo é decisão de negócio e vem de fora: o padrão reproduz o
 * preço que o fornecedor já pratica, descartando os centavos como a planilha
 * faz.
 *
 * @param politica qual dos dois custos da planilha vale
 */
class MontadorDaCarga(
    private val politica: PoliticaDeCusto = PoliticaDeCusto.COMO_A_PLANILHA,
) {

    /**
     * Monta o corpo que vai para a API.
     *
     * @param resultado o que o parser conseguiu ler da planilha
     * @param origem chave do objeto no S3, guardada para auditoria
     * @param cotacao quantos reais vale um dólar, consultada na API
     * @param hoje usada só quando a planilha não declara data no nome da aba
     * @return a carga completa, com o preço base de cada item já resolvido
     */
    fun montar(
        resultado: ResultadoImportacao,
        origem: String,
        cotacao: BigDecimal,
        hoje: LocalDate,
    ): Carga {
        val calculadora = CalculadoraPreco(cotacao)

        return Carga(
            origem = origem,
            // A data vem do nome da aba ("APPLE LACRADO 25-08"). Sem ela, vale
            // hoje: uma tabela sem data é melhor guardada como a de hoje do que
            // rejeitada, já que o retrato é por dia.
            dataReferencia = resultado.dataReferencia().orElse(hoje),
            cotacao = cotacao,
            linhasIgnoradas = resultado.ignoradas().size,
            linhasComErro = resultado.erros().size,
            itens = resultado.itens().map { item ->
                val descricao = item.descricao()
                ItemDaCarga(
                    sku = item.sku(),
                    descricaoRaw = descricao.textoOriginal(),
                    marca = descricao.marca().name,
                    tipo = descricao.tipo().name,
                    modelo = descricao.modelo(),
                    armazenamento = descricao.armazenamento(),
                    memoriaRam = descricao.memoriaRam(),
                    cor = descricao.cor(),
                    grade = descricao.grade(),
                    categoria = item.aba(),
                    custoUsd = politica.custoDe(item),
                    markup = item.markup(),
                    freteBrl = item.freteBrl(),
                    precoBaseBrl = calculadora.calcular(item, politica).precoBaseBrl(),
                )
            },
        )
    }
}
