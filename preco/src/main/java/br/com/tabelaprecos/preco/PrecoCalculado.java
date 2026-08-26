package br.com.tabelaprecos.preco;

import java.math.BigDecimal;

/**
 * A conta aberta, parcela por parcela.
 *
 * <p>Guardar as etapas e não só o total é o que permite a tela de admin
 * mostrar de onde saiu o preço, e é o que torna possível conferir uma
 * divergência com o cliente sem refazer a conta à mão.
 *
 * <p>{@code precoBaseBrl} é exatamente o número que a planilha do fornecedor
 * calcula. Tudo depois dele é decisão comercial e pode mudar sem reimportar
 * nada.
 */
public record PrecoCalculado(
        BigDecimal custoUsd,
        BigDecimal cotacao,
        BigDecimal baseBrl,
        BigDecimal markupBrl,
        BigDecimal freteBrl,
        BigDecimal precoBaseBrl,
        BigDecimal descontoBrl,
        BigDecimal acrescimosBrl,
        BigDecimal precoFinalBrl) {
}
