package br.com.tabelaprecos.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.function.Consumer;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Casos que a planilha de 25/08 não tem, montados à mão: célula de custo como
 * fórmula, coluna numérica com texto, aba sem data no nome. São os defeitos
 * que aparecem no dia em que o fornecedor mexer no arquivo.
 */
class PlanilhaSinteticaTest {

    private static final String LINHA =
            "132550-8 CEL SAMSUNG A07 SM-A075F DS/64GB/4 6.7\" BLACK SLIM /UNI 106.00 |";

    @Test
    @DisplayName("custo escrito como fórmula é lido do valor em cache")
    void custo_em_formula() throws IOException {
        ResultadoImportacao r = ler("LACRADO 25-08", linha -> {
            linha.createCell(0).setCellValue(LINHA);
            linha.createCell(1).setCellFormula("53*2");
            linha.createCell(2).setCellValue(0.15);
            linha.createCell(3).setCellValue(50);
        }, true);

        assertThat(r.erros()).isEmpty();
        assertThat(r.itens()).singleElement()
                .satisfies(item -> assertThat(item.custoUsdColuna()).isEqualByComparingTo("106.00"));
    }

    @Test
    @DisplayName("texto onde deveria ter custo vira erro, não exceção")
    void custo_textual() throws IOException {
        ResultadoImportacao r = ler("LACRADO 25-08", linha -> {
            linha.createCell(0).setCellValue(LINHA);
            linha.createCell(1).setCellValue("A COMBINAR");
            linha.createCell(2).setCellValue(0.15);
            linha.createCell(3).setCellValue(50);
        }, false);

        assertThat(r.itens()).isEmpty();
        assertThat(r.erros()).singleElement().satisfies(erro -> {
            assertThat(erro.motivo()).contains("coluna B");
            assertThat(erro.linha()).isEqualTo(2);
            assertThat(erro.conteudo()).isEqualTo(LINHA);
        });
    }

    @Test
    void linha_sem_frete_vira_erro() throws IOException {
        ResultadoImportacao r = ler("LACRADO 25-08", linha -> {
            linha.createCell(0).setCellValue(LINHA);
            linha.createCell(1).setCellValue(106);
            linha.createCell(2).setCellValue(0.15);
        }, false);

        assertThat(r.erros()).singleElement()
                .satisfies(erro -> assertThat(erro.motivo()).contains("frete"));
    }

    @Test
    @DisplayName("aba sem data no nome não inventa data")
    void aba_sem_data() throws IOException {
        ResultadoImportacao r = ler("PROMOCAO", linha -> {
            linha.createCell(0).setCellValue(LINHA);
            linha.createCell(1).setCellValue(106);
            linha.createCell(2).setCellValue(0.15);
            linha.createCell(3).setCellValue(50);
        }, false);

        assertThat(r.dataReferencia()).isEmpty();
        assertThat(r.abas()).singleElement()
                .satisfies(aba -> assertThat(aba.dataReferenciaOpcional()).isEmpty());
    }

    @Test
    @DisplayName("uma aba com só uma linha ruim ainda produz resumo")
    void resumo_sempre_existe() throws IOException {
        ResultadoImportacao r = ler("LACRADO 25-08", linha -> linha.createCell(0).setCellValue("SAMSUNG"), false);

        assertThat(r.abas()).singleElement().satisfies(aba -> {
            assertThat(aba.itens()).isZero();
            assertThat(aba.ignoradas()).isEqualTo(1);
            assertThat(aba.erros()).isZero();
        });
    }

    private static ResultadoImportacao ler(
            String nomeDaAba, Consumer<Row> montarLinha, boolean avaliarFormulas) throws IOException {

        byte[] arquivo;
        try (Workbook planilha = new XSSFWorkbook();
                ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            Sheet aba = planilha.createSheet(nomeDaAba);
            aba.createRow(0).createCell(0).setCellValue("Coluna1");
            montarLinha.accept(aba.createRow(1));
            if (avaliarFormulas) {
                planilha.getCreationHelper().createFormulaEvaluator().evaluateAll();
            }
            planilha.write(saida);
            arquivo = saida.toByteArray();
        }
        try (ByteArrayInputStream entrada = new ByteArrayInputStream(arquivo)) {
            return new PlanilhaParser().ler(entrada, 2026);
        }
    }

    @Test
    void planilha_vazia_nao_quebra() throws IOException {
        ResultadoImportacao r = ler("VAZIA", linha -> {}, false);

        assertThat(r.total()).isZero();
        assertThat(r.erros()).isEmpty();
        assertThat(r.itens()).isEmpty();
        assertThat(r.dataReferencia()).isEmpty();
    }

    @Test
    void markup_e_frete_sao_lidos_com_a_escala_certa() throws IOException {
        ResultadoImportacao r = ler("LACRADO 25-08", linha -> {
            linha.createCell(0).setCellValue(LINHA);
            linha.createCell(1).setCellValue(106);
            // O Excel guarda 7% como 7.0000000000000007E-2.
            linha.createCell(2).setCellValue(0.07000000000000001);
            linha.createCell(3).setCellValue(50);
        }, false);

        assertThat(r.itens()).singleElement().satisfies(item -> {
            assertThat(item.markup()).isEqualByComparingTo(new BigDecimal("0.0700"));
            assertThat(item.markup().scale()).isEqualTo(4);
            assertThat(item.freteBrl()).isEqualByComparingTo("50.00");
        });
    }
}
