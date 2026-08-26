package br.com.tabelaprecos.preco;

import java.math.BigDecimal;
import br.com.tabelaprecos.parser.ItemPlanilha;

/**
 * Qual dos dois custos em dólar vale.
 *
 * <p>A planilha guarda o preço duas vezes e os dois divergem em 153 das 905
 * linhas: a descrição diz {@code 109.75}, a coluna B diz {@code 109}. As
 * fórmulas do fornecedor usam a coluna B, então é ela que produz o preço que
 * ele vem praticando.
 */
public enum PoliticaDeCusto {

    /** O inteiro da coluna B. Reproduz o preço que o fornecedor já pratica. */
    COMO_A_PLANILHA {
        @Override
        public BigDecimal custoDe(ItemPlanilha item) {
            return item.custoUsdColuna();
        }
    },

    /** O preço com centavos que está na descrição. Cobre o custo de verdade. */
    COM_CENTAVOS {
        @Override
        public BigDecimal custoDe(ItemPlanilha item) {
            return item.custoUsd();
        }
    };

    public abstract BigDecimal custoDe(ItemPlanilha item);
}
