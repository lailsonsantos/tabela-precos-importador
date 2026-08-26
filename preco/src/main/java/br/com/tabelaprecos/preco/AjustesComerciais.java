package br.com.tabelaprecos.preco;

import java.math.BigDecimal;

/**
 * O que a planilha não tem e a tela de admin introduz.
 *
 * <p>Os três vêm como fração, no mesmo formato do markup do fornecedor:
 * {@code 0.05} é cinco por cento.
 */
public record AjustesComerciais(
        BigDecimal taxaCartaoPct, BigDecimal jurosPct, BigDecimal descontoPct) {

    public static final AjustesComerciais NENHUM =
            new AjustesComerciais(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    public AjustesComerciais {
        taxaCartaoPct = exigirFracao(taxaCartaoPct, "taxa de cartão");
        jurosPct = exigirFracao(jurosPct, "juros");
        descontoPct = exigirFracao(descontoPct, "desconto");
        if (descontoPct.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("desconto acima de 100%: " + descontoPct);
        }
    }

    public static AjustesComerciais desconto(String fracao) {
        return new AjustesComerciais(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(fracao));
    }

    public static AjustesComerciais cartao(String taxa, String juros) {
        return new AjustesComerciais(new BigDecimal(taxa), new BigDecimal(juros), BigDecimal.ZERO);
    }

    BigDecimal acrescimoTotal() {
        return taxaCartaoPct.add(jurosPct);
    }

    private static BigDecimal exigirFracao(BigDecimal valor, String nome) {
        if (valor == null) {
            throw new IllegalArgumentException(nome + " não pode ser nulo; use zero");
        }
        if (valor.signum() < 0) {
            throw new IllegalArgumentException(nome + " não pode ser negativo: " + valor);
        }
        return valor;
    }
}
