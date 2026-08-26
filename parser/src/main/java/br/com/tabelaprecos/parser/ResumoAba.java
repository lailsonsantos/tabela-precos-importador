package br.com.tabelaprecos.parser;

import java.time.LocalDate;
import java.util.Optional;

/** O que cada aba rendeu. */
public record ResumoAba(
        String nome,
        LayoutAba layout,
        LocalDate dataReferencia,
        int itens,
        int erros,
        int ignoradas) {

    public Optional<LocalDate> dataReferenciaOpcional() {
        return Optional.ofNullable(dataReferencia);
    }
}
