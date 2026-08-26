package br.com.tabelaprecos.parser;

/**
 * Linha descartada de propósito: cabeçalho de seção ("MOTOROLA", "REALME") ou
 * linha em branco. Não é erro, mas fica registrada para o admin conferir a
 * contagem depois do upload.
 */
public record LinhaIgnorada(String aba, int linha, String conteudo, String motivo) {
}
