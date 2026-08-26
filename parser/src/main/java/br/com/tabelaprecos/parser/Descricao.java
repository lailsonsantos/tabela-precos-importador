package br.com.tabelaprecos.parser;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Resultado da leitura da coluna A, que é texto cru colado de outro sistema.
 *
 * <p>O texto vem truncado em torno de 62 caracteres, então campos do fim da
 * linha — cor, principalmente — chegam cortados. Tudo que é incerto é
 * {@link Optional}: é melhor devolver vazio do que inventar.
 */
public record Descricao(
        String sku,
        TipoProduto tipo,
        Marca marca,
        String modelo,
        String armazenamento,
        String memoriaRam,
        String tela,
        String cor,
        String grade,
        boolean telaTrocada,
        boolean esim,
        BigDecimal custoUsd,
        String textoOriginal) {

    public Optional<String> armazenamentoOpcional() {
        return Optional.ofNullable(armazenamento);
    }

    public Optional<String> corOpcional() {
        return Optional.ofNullable(cor);
    }

    public Optional<String> gradeOpcional() {
        return Optional.ofNullable(grade);
    }
}
