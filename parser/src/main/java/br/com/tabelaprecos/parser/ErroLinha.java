package br.com.tabelaprecos.parser;

/** Linha que parecia produto e não pôde ser lida. Nunca interrompe a importação. */
public record ErroLinha(String aba, int linha, String motivo, String conteudo) {
}
