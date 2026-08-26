package br.com.tabelaprecos.parser;

/** A linha tem cara de produto (começa com SKU) mas não pôde ser interpretada. */
public class DescricaoInvalidaException extends Exception {

    private final String texto;

    public DescricaoInvalidaException(String texto, String motivo) {
        super(motivo);
        this.texto = texto;
    }

    public String texto() {
        return texto;
    }
}
