package br.com.tabelaprecos.parser;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Tudo que a leitura de um arquivo produziu. Itens, erros e descartes vêm
 * juntos: a tela de admin mostra "905 itens, 2 linhas ignoradas, 0 erros"
 * antes de o usuário confirmar a importação.
 */
public record ResultadoImportacao(
        List<ItemPlanilha> itens,
        List<ErroLinha> erros,
        List<LinhaIgnorada> ignoradas,
        List<ResumoAba> abas) {

    public ResultadoImportacao {
        itens = List.copyOf(itens);
        erros = List.copyOf(erros);
        ignoradas = List.copyOf(ignoradas);
        abas = List.copyOf(abas);
    }

    public int total() {
        return itens.size();
    }

    public boolean temErro() {
        return !erros.isEmpty();
    }

    public List<ItemPlanilha> itensDa(String aba) {
        return itens.stream().filter(item -> item.aba().equals(aba)).toList();
    }

    /** A data que a maioria das abas declara no próprio nome. */
    public Optional<LocalDate> dataReferencia() {
        return abas.stream()
                .map(ResumoAba::dataReferencia)
                .filter(java.util.Objects::nonNull)
                .findFirst();
    }
}
