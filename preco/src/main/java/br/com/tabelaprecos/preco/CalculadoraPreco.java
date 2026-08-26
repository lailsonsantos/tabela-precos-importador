package br.com.tabelaprecos.preco;

import br.com.tabelaprecos.parser.ItemPlanilha;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Transforma custo em dólar no preço que aparece para o cliente.
 *
 * <p>A ordem das operações é a decisão mais cara desta classe, porque é dela
 * que nasce divergência de centavo na frente do cliente:
 *
 * <ol>
 *   <li>base = custo em dólar × cotação
 *   <li>markup = base × percentual do fornecedor
 *   <li><b>preço base</b> = base + markup + frete — é o número da planilha
 *   <li>desconto = preço base × percentual de desconto
 *   <li>acréscimos = (preço base − desconto) × (taxa de cartão + juros)
 *   <li>preço final = preço base − desconto + acréscimos
 * </ol>
 *
 * <p>O desconto entra antes dos acréscimos de propósito: é a regra de varejo
 * que o cliente espera, em que o parcelamento incide sobre o valor já
 * negociado, e não sobre o preço cheio.
 *
 * <p>Toda conta é em {@link BigDecimal}. Os passos intermediários rodam com
 * dez casas e só o resultado de cada etapa é arredondado para centavo, com
 * {@link RoundingMode#HALF_UP}: arredondar no meio do caminho acumula erro que
 * aparece justamente nos itens caros.
 */
public final class CalculadoraPreco {

    private static final int CASAS_INTERNAS = 10;
    private static final int CENTAVOS = 2;

    private final BigDecimal cotacao;

    public CalculadoraPreco(BigDecimal cotacao) {
        if (cotacao == null || cotacao.signum() <= 0) {
            throw new IllegalArgumentException("cotação precisa ser positiva: " + cotacao);
        }
        this.cotacao = cotacao;
    }

    /** Só o preço da planilha, sem nenhum ajuste comercial. */
    public PrecoCalculado calcular(ItemPlanilha item, PoliticaDeCusto politica) {
        return calcular(item, politica, AjustesComerciais.NENHUM);
    }

    public PrecoCalculado calcular(
            ItemPlanilha item, PoliticaDeCusto politica, AjustesComerciais ajustes) {
        if (item == null) {
            throw new IllegalArgumentException("item não pode ser nulo");
        }
        return calcular(politica.custoDe(item), item.markup(), item.freteBrl(), ajustes);
    }

    /**
     * @param markup fração cobrada sobre a base; {@code null} nas linhas de
     *     semi novos, em que a margem já está embutida no frete
     */
    public PrecoCalculado calcular(
            BigDecimal custoUsd, BigDecimal markup, BigDecimal freteBrl, AjustesComerciais ajustes) {

        BigDecimal custo = exigirNaoNegativo(custoUsd, "custo em dólar");
        BigDecimal frete = exigirNaoNegativo(freteBrl == null ? BigDecimal.ZERO : freteBrl, "frete");
        BigDecimal percentualDeMarkup =
                exigirNaoNegativo(markup == null ? BigDecimal.ZERO : markup, "markup");

        BigDecimal base = escala(custo.multiply(cotacao));
        BigDecimal valorDoMarkup = escala(base.multiply(percentualDeMarkup));
        BigDecimal precoBase = base.add(valorDoMarkup).add(frete);

        // O preço base sai da conta inteira e só então vira centavo, porque
        // precisa bater com a planilha do fornecedor. Do desconto em diante a
        // regra é outra: cada parcela vira centavo antes de entrar na soma,
        // para o que aparece na tela fechar. Quem olha a vitrine confere a
        // conta de cabeça, e um centavo sobrando vira ligação do cliente.
        BigDecimal precoBaseEmCentavos = centavos(precoBase);
        BigDecimal desconto = centavos(precoBaseEmCentavos.multiply(ajustes.descontoPct()));
        BigDecimal comDesconto = precoBaseEmCentavos.subtract(desconto);
        BigDecimal acrescimos = centavos(comDesconto.multiply(ajustes.acrescimoTotal()));

        return new PrecoCalculado(
                custo,
                cotacao,
                centavos(base),
                centavos(valorDoMarkup),
                centavos(frete),
                precoBaseEmCentavos,
                desconto,
                acrescimos,
                comDesconto.add(acrescimos));
    }

    private static BigDecimal escala(BigDecimal valor) {
        return valor.setScale(CASAS_INTERNAS, RoundingMode.HALF_UP);
    }

    private static BigDecimal centavos(BigDecimal valor) {
        return valor.setScale(CENTAVOS, RoundingMode.HALF_UP);
    }

    private static BigDecimal exigirNaoNegativo(BigDecimal valor, String nome) {
        if (valor == null) {
            throw new IllegalArgumentException(nome + " não pode ser nulo");
        }
        if (valor.signum() < 0) {
            throw new IllegalArgumentException(nome + " não pode ser negativo: " + valor);
        }
        return valor;
    }
}
