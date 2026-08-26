package br.com.tabelaprecos.preco;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.tabelaprecos.parser.ItemPlanilha;
import br.com.tabelaprecos.parser.LayoutAba;
import br.com.tabelaprecos.parser.PlanilhaParser;
import br.com.tabelaprecos.parser.ResultadoImportacao;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O teste que decide se o motor está certo: roda as 905 linhas da planilha e
 * compara com o total que o próprio fornecedor calculou na última coluna.
 *
 * <p>Um teste de unidade com seis casos escolhidos a dedo prova que a fórmula
 * está escrita; este prova que ela vale para a tabela inteira, inclusive nos
 * itens caros, onde um erro de arredondamento aparece em reais e não em
 * centavos.
 */
class OraculoDaPlanilhaTest {

    private static final BigDecimal COTACAO = new BigDecimal("5.23");

    /** Coluna G nas abas de lacrado; coluna E na de semi novos. */
    private static final int TOTAL_COM_MARKUP = 6;
    private static final int TOTAL_SEMI_NOVOS = 4;

    private static ResultadoImportacao planilha;
    private static Map<String, BigDecimal> totaisDoFornecedor;

    @BeforeAll
    static void lerAPlanilha() throws IOException {
        try (InputStream entrada = fixture()) {
            planilha = new PlanilhaParser().ler(entrada, 2026);
        }
        totaisDoFornecedor = lerTotais();
    }

    @Test
    @DisplayName("o preço base bate com a planilha nas 905 linhas")
    void reproduz_a_planilha_inteira() {
        CalculadoraPreco calculadora = new CalculadoraPreco(COTACAO);

        assertThat(planilha.itens()).isNotEmpty();
        assertThat(planilha.itens()).allSatisfy(item -> {
            BigDecimal esperado = totaisDoFornecedor.get(chave(item));
            assertThat(esperado).as("total do fornecedor para %s", chave(item)).isNotNull();

            BigDecimal calculado =
                    calculadora.calcular(item, PoliticaDeCusto.COMO_A_PLANILHA).precoBaseBrl();

            assertThat(calculado)
                    .as("%s (%s, linha %d)", item.sku(), item.aba(), item.linha())
                    .isEqualByComparingTo(esperado);
        });
    }

    @Test
    @DisplayName("usar o custo com centavos muda o preço em 153 linhas, sempre para cima")
    void a_politica_de_custo_muda_o_resultado() {
        CalculadoraPreco calculadora = new CalculadoraPreco(COTACAO);

        long diferentes = planilha.itens().stream()
                .filter(item -> {
                    BigDecimal comoAPlanilha =
                            calculadora.calcular(item, PoliticaDeCusto.COMO_A_PLANILHA).precoBaseBrl();
                    BigDecimal comCentavos =
                            calculadora.calcular(item, PoliticaDeCusto.COM_CENTAVOS).precoBaseBrl();
                    assertThat(comCentavos).isGreaterThanOrEqualTo(comoAPlanilha);
                    return comCentavos.compareTo(comoAPlanilha) != 0;
                })
                .count();

        assertThat(diferentes).isEqualTo(153);
    }

    @Test
    @DisplayName("os centavos descartados custam alguns reais por item, não centavos")
    void o_tamanho_do_prejuizo_de_arredondar_o_custo() {
        CalculadoraPreco calculadora = new CalculadoraPreco(COTACAO);

        BigDecimal maiorDiferenca = planilha.itens().stream()
                .filter(ItemPlanilha::centavosDescartados)
                .map(item -> calculadora.calcular(item, PoliticaDeCusto.COM_CENTAVOS).precoBaseBrl()
                        .subtract(calculadora.calcular(item, PoliticaDeCusto.COMO_A_PLANILHA).precoBaseBrl()))
                .max(BigDecimal::compareTo)
                .orElseThrow();

        // Até 99 centavos de dólar viram cerca de R$ 5 depois da cotação e do
        // markup. Não é erro de arredondamento: é margem que fica no caminho.
        assertThat(maiorDiferenca).isBetween(new BigDecimal("4.00"), new BigDecimal("6.00"));
    }

    private static String chave(ItemPlanilha item) {
        return item.aba() + "|" + item.linha();
    }

    private static InputStream fixture() {
        InputStream entrada = OraculoDaPlanilhaTest.class.getResourceAsStream("/tabela-exemplo.xlsx");
        assertThat(entrada).as("fixture tabela-exemplo.xlsx").isNotNull();
        return entrada;
    }

    /** Lê a última coluna de cada linha, que é o total que o fornecedor fechou. */
    private static Map<String, BigDecimal> lerTotais() throws IOException {
        Map<String, BigDecimal> totais = new HashMap<>();
        try (InputStream entrada = fixture();
                Workbook arquivo = new XSSFWorkbook(entrada)) {

            for (int i = 0; i < arquivo.getNumberOfSheets(); i++) {
                Sheet aba = arquivo.getSheetAt(i);
                boolean semiNovos = aba.getSheetName().startsWith("SEMI NOVOS");
                int coluna = semiNovos ? TOTAL_SEMI_NOVOS : TOTAL_COM_MARKUP;

                for (int l = 1; l <= aba.getLastRowNum(); l++) {
                    Row linha = aba.getRow(l);
                    if (linha == null) {
                        continue;
                    }
                    Cell celula = linha.getCell(coluna);
                    if (celula == null) {
                        continue;
                    }
                    // A planilha calcula por fórmula compartilhada: o total
                    // está no valor em cache, não no tipo da célula.
                    CellType tipo = celula.getCellType() == CellType.FORMULA
                            ? celula.getCachedFormulaResultType()
                            : celula.getCellType();
                    if (tipo != CellType.NUMERIC) {
                        continue;
                    }
                    totais.put(
                            aba.getSheetName() + "|" + (l + 1),
                            BigDecimal.valueOf(celula.getNumericCellValue())
                                    .setScale(2, RoundingMode.HALF_UP));
                }
            }
        }
        return totais;
    }

    @Test
    void o_oraculo_cobre_toda_a_planilha() {
        long comMarkup = planilha.itens().stream()
                .filter(item -> item.layout() == LayoutAba.COM_MARKUP)
                .count();

        assertThat(totaisDoFornecedor).hasSizeGreaterThanOrEqualTo(planilha.total());
        assertThat(comMarkup).isEqualTo(770);
    }
}
