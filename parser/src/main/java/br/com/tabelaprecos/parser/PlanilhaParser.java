package br.com.tabelaprecos.parser;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Lê o arquivo inteiro do fornecedor.
 *
 * <p>Regra que atravessa a classe: nenhuma linha ruim derruba a importação.
 * Célula com {@code #VALUE!}, cabeçalho de seção no meio da aba, coluna B com
 * texto — tudo vira registro em {@link ResultadoImportacao#erros()} ou
 * {@link ResultadoImportacao#ignoradas()} e a leitura segue.
 */
public final class PlanilhaParser {

    /** As abas trazem a data no nome: "APPLE LACRADO 25-08". */
    private static final Pattern DATA_NO_NOME = Pattern.compile("(\\d{2})-(\\d{2})\\s*$");

    private static final int COL_DESCRICAO = 0;
    private static final int COL_CUSTO_USD = 1;
    private static final int COL_MARKUP_OU_FRETE = 2;
    private static final int COL_FRETE = 3;

    /** Markup vive entre 0 e 1; frete de semi novo passa de 300. */
    private static final BigDecimal LIMITE_MARKUP = BigDecimal.ONE;

    public ResultadoImportacao ler(InputStream entrada) throws IOException {
        return ler(entrada, Year.now().getValue());
    }

    public ResultadoImportacao ler(InputStream entrada, int anoDeReferencia) throws IOException {
        List<ItemPlanilha> itens = new ArrayList<>();
        List<ErroLinha> erros = new ArrayList<>();
        List<LinhaIgnorada> ignoradas = new ArrayList<>();
        List<ResumoAba> abas = new ArrayList<>();

        try (Workbook planilha = new XSSFWorkbook(entrada)) {
            for (int i = 0; i < planilha.getNumberOfSheets(); i++) {
                Sheet aba = planilha.getSheetAt(i);
                LayoutAba layout = detectarLayout(aba);
                int itensAntes = itens.size();
                int errosAntes = erros.size();
                int ignoradasAntes = ignoradas.size();

                for (int l = 1; l <= aba.getLastRowNum(); l++) {
                    lerLinha(aba, aba.getRow(l), l, layout, itens, erros, ignoradas);
                }

                abas.add(new ResumoAba(
                        aba.getSheetName(),
                        layout,
                        dataDoNome(aba.getSheetName(), anoDeReferencia),
                        itens.size() - itensAntes,
                        erros.size() - errosAntes,
                        ignoradas.size() - ignoradasAntes));
            }
        }
        return new ResultadoImportacao(itens, erros, ignoradas, abas);
    }

    private void lerLinha(
            Sheet aba,
            Row linha,
            int indice,
            LayoutAba layout,
            List<ItemPlanilha> itens,
            List<ErroLinha> erros,
            List<LinhaIgnorada> ignoradas) {

        String nome = aba.getSheetName();
        int numeroHumano = indice + 1;

        String texto = linha == null ? "" : textoDe(linha.getCell(COL_DESCRICAO));
        if (texto.isBlank()) {
            ignoradas.add(new LinhaIgnorada(nome, numeroHumano, texto, "linha em branco"));
            return;
        }
        if (!DescricaoParser.temSku(texto)) {
            ignoradas.add(new LinhaIgnorada(nome, numeroHumano, texto.trim(), "cabeçalho de seção"));
            return;
        }

        Descricao descricao;
        try {
            descricao = DescricaoParser.parse(texto);
        } catch (DescricaoInvalidaException e) {
            erros.add(new ErroLinha(nome, numeroHumano, e.getMessage(), texto.trim()));
            return;
        }

        BigDecimal custo = numeroDe(linha.getCell(COL_CUSTO_USD));
        if (custo == null) {
            erros.add(new ErroLinha(nome, numeroHumano, "coluna B sem custo numérico", texto.trim()));
            return;
        }

        BigDecimal markup = null;
        BigDecimal frete;
        if (layout == LayoutAba.COM_MARKUP) {
            markup = arredondar(numeroDe(linha.getCell(COL_MARKUP_OU_FRETE)), 4);
            frete = arredondar(numeroDe(linha.getCell(COL_FRETE)), 2);
        } else {
            frete = arredondar(numeroDe(linha.getCell(COL_MARKUP_OU_FRETE)), 2);
        }
        if (frete == null) {
            erros.add(new ErroLinha(nome, numeroHumano, "linha sem frete", texto.trim()));
            return;
        }

        itens.add(new ItemPlanilha(
                descricao, arredondar(custo, 2), markup, frete, nome, layout, numeroHumano));
    }

    /**
     * Decide o arranjo de colunas pela terceira coluna: nas abas de lacrado ela
     * é o markup (menor que 1); na de semi novos é o frete (centenas de reais).
     */
    private LayoutAba detectarLayout(Sheet aba) {
        int comMarkup = 0;
        int semMarkup = 0;
        for (int l = 1; l <= aba.getLastRowNum() && comMarkup + semMarkup < 10; l++) {
            Row linha = aba.getRow(l);
            if (linha == null) {
                continue;
            }
            BigDecimal terceira = numeroDe(linha.getCell(COL_MARKUP_OU_FRETE));
            if (terceira == null || terceira.signum() == 0) {
                continue;
            }
            if (terceira.compareTo(LIMITE_MARKUP) < 0) {
                comMarkup++;
            } else {
                semMarkup++;
            }
        }
        return semMarkup > comMarkup ? LayoutAba.SEMI_NOVOS : LayoutAba.COM_MARKUP;
    }

    private static String textoDe(Cell celula) {
        if (celula == null) {
            return "";
        }
        return celula.getCellType() == CellType.STRING ? celula.getStringCellValue() : "";
    }

    /**
     * Devolve null para célula ausente, textual ou com erro de fórmula.
     *
     * <p>As colunas calculadas da planilha são fórmulas compartilhadas, e a
     * coluna de custo pode virar uma a qualquer momento. Nesse caso vale o
     * último valor que o Excel guardou em cache: recalcular exigiria avaliar a
     * fórmula, e o número que o fornecedor viu na tela é o que ele cotou.
     */
    private static BigDecimal numeroDe(Cell celula) {
        if (celula == null) {
            return null;
        }
        CellType tipo = celula.getCellType() == CellType.FORMULA
                ? celula.getCachedFormulaResultType()
                : celula.getCellType();
        if (tipo != CellType.NUMERIC) {
            return null;
        }
        return BigDecimal.valueOf(celula.getNumericCellValue());
    }

    private static BigDecimal arredondar(BigDecimal valor, int casas) {
        return valor == null ? null : valor.setScale(casas, RoundingMode.HALF_UP);
    }

    private static LocalDate dataDoNome(String nomeDaAba, int ano) {
        Matcher m = DATA_NO_NOME.matcher(nomeDaAba.trim());
        if (!m.find()) {
            return null;
        }
        try {
            return LocalDate.of(ano, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
