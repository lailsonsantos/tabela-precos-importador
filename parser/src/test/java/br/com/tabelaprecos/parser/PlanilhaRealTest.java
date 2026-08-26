package br.com.tabelaprecos.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Conferência contra a planilha de verdade, que nunca entra no repositório.
 *
 * <pre>
 * TABELA_REAL="$HOME/Downloads/TABELA GERAL 25-08.xlsx" ./gradlew :parser:test
 * </pre>
 *
 * Sem a variável de ambiente o teste é pulado, então a pipeline continua verde
 * sem nunca ver o arquivo.
 */
@EnabledIfEnvironmentVariable(named = "TABELA_REAL", matches = ".+")
class PlanilhaRealTest {

    @Test
    void le_a_planilha_do_fornecedor_sem_erro() throws IOException {
        Path arquivo = Path.of(System.getenv("TABELA_REAL"));
        assertThat(arquivo).exists();

        ResultadoImportacao resultado;
        try (InputStream entrada = Files.newInputStream(arquivo)) {
            resultado = new PlanilhaParser().ler(entrada, 2026);
        }

        assertThat(resultado.erros()).isEmpty();
        assertThat(resultado.total()).isEqualTo(905);
        assertThat(resultado.itens())
                .allSatisfy(item -> assertThat(item.descricao().tipo())
                        .isNotEqualTo(TipoProduto.DESCONHECIDO));
    }
}
