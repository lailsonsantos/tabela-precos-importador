package br.com.tabelaprecos.parser;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta a coluna A da planilha.
 *
 * <p>A gramática, deduzida das quatro abas:
 *
 * <pre>
 * 112818-5 APPLE FONE AIRPODS 4 MXP63LL/A WHITE /UNI 121.00 |
 * 96086-1 CEL APPLE IPHONE 13 PRO MAX 128GB/6 A2484 LL 6.7" GOLD CP/UNI 605.00 |
 * 6999-1 SWAP IPHONE 13 128GB AZUL A (AMERICANO) / 288.00 |
 * └─sku─┘ └tipo┘ └───────────── descrição ─────────────┘ └preço┘
 * </pre>
 *
 * <p>Três armadilhas do formato real: o texto termina em {@code |}; o
 * separador antes do preço pode ser {@code /UNI} ou só {@code /}, e vem
 * grudado na palavra anterior quando a linha foi truncada
 * ({@code ...WHITE.T/UNI 1110.00}); e o preço aqui tem centavos que a
 * coluna B descarta.
 */
public final class DescricaoParser {

    private static final Pattern PRECO =
            Pattern.compile("^(.*)/\\s*(?:UNI)?\\s*(\\d+(?:\\.\\d{1,2})?)\\s*$");
    private static final Pattern SKU = Pattern.compile("^(\\d{3,6}-\\d)\\s+(.+)$");
    private static final Pattern TELA = Pattern.compile("(\\d{1,2}(?:\\.\\d{1,2})?)\"");
    private static final Pattern ARMAZENAMENTO = Pattern.compile("(\\d{1,4})\\s*(GB|TB|G|T)(?![A-Z])");
    private static final Pattern RAM_APOS_ARMAZENAMENTO =
            Pattern.compile("\\d{1,4}\\s*(?:GB|TB)\\s*/\\s*(\\d{1,3})(?![\\d/])");
    private static final Pattern RAM_EXPLICITA = Pattern.compile("(\\d{1,3})\\s*RAM");

    /** Tokens que marcam o começo da parte técnica — o modelo termina antes deles. */
    private static final Pattern TECNICO = Pattern.compile(
            "^(DS(/.*)?|\\d+G|SM-.*|\\(.*|.*\"|.*\\d+(GB|TB)([/-].*)?|.*\\d+[GT]/.*)$");

    private static final Set<String> CORES = Set.of(
            "BLACK", "WHITE", "BLUE", "GREEN", "GREY", "GRAY", "SILVER", "GOLD", "PINK",
            "RED", "PURPLE", "ORANGE", "YELLOW", "VIOLET", "LAVENDER", "MIDNIGHT",
            "STARLIGHT", "TITANIUM", "NATURAL", "CORAL", "NAVY", "BORDEAUX", "BROWN",
            "CITRUS", "TEAL", "GRAPHITE", "LILY", "SAND", "CREAM", "INDIGO", "BLUSH",
            "AZUL", "PRETO", "BRANCO", "VERDE", "VERMELHO", "ROSA", "ROJO", "BLANCO",
            "AMARELO", "AMARELHO", "DOURADO", "PRATA", "LILAS", "MORADO");

    private static final Set<String> GRADES = Set.of("A", "A+", "A-", "B", "B+", "B-", "C");

    /** Abreviações que o fornecedor usa: "BLK W BLK-CHRC". */
    private static final Map<String, String> CORES_ABREVIADAS =
            Map.of("BLK", "BLACK", "GRY", "GREY", "SLV", "SILVER");

    private static final int MINIMO_PARA_ADIVINHAR_CORTE = 3;

    private DescricaoParser() {
    }

    /** Vocabulário fechado: {@link Descricao#cor()} só devolve valor daqui. */
    public static Set<String> coresConhecidas() {
        return CORES;
    }

    /** Uma linha só é produto se começar com SKU. O resto é separador de seção. */
    public static boolean temSku(String texto) {
        return texto != null && SKU.matcher(limpar(texto)).matches();
    }

    public static Descricao parse(String texto) throws DescricaoInvalidaException {
        if (texto == null || texto.isBlank()) {
            throw new DescricaoInvalidaException(texto, "linha vazia");
        }
        String limpo = limpar(texto);

        Matcher comSku = SKU.matcher(limpo);
        if (!comSku.matches()) {
            throw new DescricaoInvalidaException(texto, "não começa com SKU no formato 00000-0");
        }
        String sku = comSku.group(1);

        Matcher comPreco = PRECO.matcher(comSku.group(2));
        if (!comPreco.matches()) {
            throw new DescricaoInvalidaException(texto, "não termina com preço após / ou /UNI");
        }
        BigDecimal custoUsd = new BigDecimal(comPreco.group(2));
        String corpo = comPreco.group(1).trim();

        List<String> tokens = List.of(corpo.split("\\s+"));
        TipoProduto tipo = tipo(tokens);
        Marca marca = marca(tokens, tipo);

        return new Descricao(
                sku,
                tipo,
                marca,
                modelo(tokens, tipo),
                armazenamento(corpo, tipo),
                memoriaRam(corpo, tipo),
                tela(corpo),
                cor(tokens),
                grade(tokens, tipo),
                corpo.contains("TELA TROCADA"),
                tokens.contains("ESIM"),
                custoUsd,
                texto);
    }

    private static String limpar(String texto) {
        String semBarra = texto.trim();
        if (semBarra.endsWith("|")) {
            semBarra = semBarra.substring(0, semBarra.length() - 1);
        }
        return semBarra.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static TipoProduto tipo(List<String> tokens) {
        String primeiro = tokens.isEmpty() ? "" : tokens.get(0);
        return switch (primeiro) {
            case "CEL" -> TipoProduto.CELULAR;
            case "NB" -> TipoProduto.NOTEBOOK;
            case "TABLET" -> TipoProduto.TABLET;
            case "FONE" -> TipoProduto.FONE;
            case "SWAP" -> TipoProduto.SEMINOVO;
            // Nas linhas de acessório a marca vem antes do tipo: "APPLE FONE ...".
            case "APPLE" -> tokens.size() < 2 ? TipoProduto.DESCONHECIDO : switch (tokens.get(1)) {
                case "FONE" -> TipoProduto.FONE;
                case "WATCH" -> TipoProduto.RELOGIO;
                case "IPAD" -> TipoProduto.TABLET;
                default -> TipoProduto.ACESSORIO;
            };
            default -> TipoProduto.DESCONHECIDO;
        };
    }

    private static Marca marca(List<String> tokens, TipoProduto tipo) {
        if (tipo == TipoProduto.SEMINOVO) {
            return tokens.size() > 1 && tokens.get(1).equals("IPHONE") ? Marca.APPLE : Marca.DESCONHECIDA;
        }
        if (!tokens.isEmpty() && tokens.get(0).equals("APPLE")) {
            return Marca.APPLE;
        }
        return tokens.size() > 1 ? Marca.de(tokens.get(1)) : Marca.DESCONHECIDA;
    }

    /** Até quatro tokens depois da marca, parando no primeiro campo técnico. */
    private static String modelo(List<String> tokens, TipoProduto tipo) {
        // "CEL APPLE IPHONE 16" e "APPLE FONE AIRPODS 4" têm tipo e marca nos dois
        // primeiros tokens, em ordem trocada; "SWAP IPHONE 13" tem só o tipo.
        int inicio = tipo == TipoProduto.SEMINOVO ? 1 : 2;
        StringBuilder modelo = new StringBuilder();
        for (int i = inicio; i < tokens.size() && i < inicio + 4; i++) {
            String token = tokens.get(i);
            if (TECNICO.matcher(token).matches() || CORES.contains(token.replaceAll("[^A-Z]", ""))) {
                break;
            }
            if (!modelo.isEmpty()) {
                modelo.append(' ');
            }
            modelo.append(token);
        }
        return modelo.isEmpty() ? null : modelo.toString();
    }

    /**
     * A ordem das capacidades muda com o tipo, o que é a maior armadilha do
     * formato: celular escreve {@code 128GB/6} (armazenamento/RAM), enquanto
     * notebook e tablet escrevem {@code 36GB/1TB} (RAM/armazenamento). Tablet
     * às vezes é explícito: {@code 4RAM/64GB}.
     */
    private static String armazenamento(String corpo, TipoProduto tipo) {
        List<String> capacidades = capacidades(corpo);
        if (capacidades.isEmpty()) {
            return null;
        }
        if (ramPrimeiro(corpo, tipo) && capacidades.size() > 1) {
            return capacidades.get(1);
        }
        return capacidades.get(0);
    }

    private static String memoriaRam(String corpo, TipoProduto tipo) {
        Matcher explicita = RAM_EXPLICITA.matcher(corpo);
        if (explicita.find()) {
            return explicita.group(1) + "GB";
        }
        if (ramPrimeiro(corpo, tipo)) {
            List<String> capacidades = capacidades(corpo);
            return capacidades.isEmpty() ? null : capacidades.get(0);
        }
        Matcher ram = RAM_APOS_ARMAZENAMENTO.matcher(corpo);
        return ram.find() ? ram.group(1) + "GB" : null;
    }

    private static boolean ramPrimeiro(String corpo, TipoProduto tipo) {
        boolean doisEmUm = tipo == TipoProduto.NOTEBOOK || tipo == TipoProduto.TABLET;
        return doisEmUm && !RAM_EXPLICITA.matcher(corpo).find();
    }

    private static List<String> capacidades(String corpo) {
        Matcher m = ARMAZENAMENTO.matcher(corpo);
        List<String> achadas = new java.util.ArrayList<>();
        while (m.find()) {
            int valor = Integer.parseInt(m.group(1));
            String unidade = m.group(2);
            // "5G" e "4G" são rede, não capacidade. "1T" é 1 TB de SSD e vale.
            if (unidade.equals("G") && valor <= 5) {
                continue;
            }
            achadas.add(valor + (unidade.length() == 1 ? unidade + "B" : unidade));
        }
        return achadas;
    }

    private static String tela(String corpo) {
        Matcher m = TELA.matcher(corpo);
        return m.find() ? m.group(1) + "\"" : null;
    }

    /**
     * A cor é o último campo da linha e por isso é a maior vítima do
     * truncamento: sobra "BLU", "PURP", "SPACE GRA". Quando o pedaço final
     * completa uma única cor conhecida, vale; quando é ambíguo ("GR" serve a
     * GREEN, GREY e GRAY) devolve null. Chutar aqui é pior do que não saber:
     * cor errada no catálogo é troca de produto na entrega.
     */
    private static String cor(List<String> tokens) {
        for (String token : tokens) {
            // "PURPLE(AMERICANO)" e "S.BLACK" trazem a cor grudada em outra coisa.
            for (String pedaco : token.split("[^A-Z]+")) {
                if (CORES.contains(pedaco)) {
                    return pedaco;
                }
                if (CORES_ABREVIADAS.containsKey(pedaco)) {
                    return CORES_ABREVIADAS.get(pedaco);
                }
            }
        }
        return corCortadaNoFim(tokens);
    }

    private static String corCortadaNoFim(List<String> tokens) {
        if (tokens.isEmpty()) {
            return null;
        }
        String ultimo = tokens.get(tokens.size() - 1).replaceAll("[^A-Z]", "");
        if (ultimo.length() < MINIMO_PARA_ADIVINHAR_CORTE) {
            return null;
        }
        String unica = null;
        for (String cor : CORES) {
            if (cor.startsWith(ultimo)) {
                if (unica != null) {
                    return null; // mais de uma cor começa assim
                }
                unica = cor;
            }
        }
        return unica;
    }

    private static String grade(List<String> tokens, TipoProduto tipo) {
        if (tipo != TipoProduto.SEMINOVO) {
            return null;
        }
        for (String token : tokens) {
            if (GRADES.contains(token)) {
                return token;
            }
        }
        return null;
    }
}
