package br.com.tabelaprecos.parser;

/**
 * As abas da planilha usam dois arranjos de coluna diferentes.
 *
 * <p>{@link #COM_MARKUP}: A=texto, B=custo USD, C=markup, D=frete,
 * E=B×cotação, F=E×C, G=E+F+D.
 *
 * <p>{@link #SEMI_NOVOS}: A=texto, B=custo USD, C=frete, D=B×cotação, E=D+C.
 * Não há markup — a margem já está embutida no frete.
 */
public enum LayoutAba {
    COM_MARKUP,
    SEMI_NOVOS
}
