package br.com.tabelaprecos.parser;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Uma linha de produto, já com a descrição interpretada e as colunas
 * numéricas lidas.
 *
 * <p>Os dois custos existem de propósito. {@code custoUsd} é o preço com
 * centavos que aparece na descrição; {@code custoUsdColuna} é o número da
 * coluna B, que a planilha trunca para o inteiro e usa em todas as fórmulas.
 * Em 153 das 905 linhas da tabela de 25/08 os dois divergem. Quem decide qual
 * vale é o motor de preço, não o parser.
 */
public record ItemPlanilha(
        Descricao descricao,
        BigDecimal custoUsdColuna,
        BigDecimal markup,
        BigDecimal freteBrl,
        String aba,
        LayoutAba layout,
        int linha) {

    public String sku() {
        return descricao.sku();
    }

    public BigDecimal custoUsd() {
        return descricao.custoUsd();
    }

    /** Verdadeiro quando a planilha descartou os centavos do custo. */
    public boolean centavosDescartados() {
        return custoUsdColuna != null && custoUsd().compareTo(custoUsdColuna) != 0;
    }

    public Optional<BigDecimal> markupOpcional() {
        return Optional.ofNullable(markup);
    }
}
