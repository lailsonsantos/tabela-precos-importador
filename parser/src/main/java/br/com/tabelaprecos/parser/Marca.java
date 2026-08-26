package br.com.tabelaprecos.parser;

/** Marcas presentes na tabela do fornecedor. */
public enum Marca {
    APPLE,
    SAMSUNG,
    XIAOMI,
    MOTOROLA,
    REALME,
    DESCONHECIDA;

    static Marca de(String token) {
        if (token == null) {
            return DESCONHECIDA;
        }
        for (Marca marca : values()) {
            if (marca != DESCONHECIDA && marca.name().equals(token)) {
                return marca;
            }
        }
        return DESCONHECIDA;
    }
}
