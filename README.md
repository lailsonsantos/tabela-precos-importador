# tabela-precos-importador

Lê a tabela que o fornecedor manda em Excel e grava um retrato dos preços do
dia. É o dono da leitura do arquivo: a API nunca abre `.xlsx`, ela lê o que
este serviço gravou no banco.

Faz parte de um projeto de três repositórios:

| Repositório                                                                          | O que é                       |
|--------------------------------------------------------------------------------------|-------------------------------|
| [tabela-precos-importador](https://github.com/lailsonsantos/tabela-precos-importador) | este — parser Java + Lambda Kotlin |
| [tabela-precos-api](https://github.com/lailsonsantos/tabela-precos-api)               | Spring Boot, Java 21          |
| [tabela-precos-web](https://github.com/lailsonsantos/tabela-precos-web)               | React + Vite                  |

O plano completo, com as sete fases e o orçamento da AWS, está
[aqui](https://claude.ai/code/artifact/e09aab9f-49c6-4f8d-8192-8d5410a8fc24).

## Módulos

| Módulo   | Fase | O que é                                                |
|----------|------|--------------------------------------------------------|
| `parser` | 1    | Lê o `.xlsx` do fornecedor. Java 21 puro, sem framework |
| `preco`  | 2    | Motor de preço base — ainda não existe                  |
| `lambda` | 5    | Handler Kotlin disparado por evento do S3 — ainda não existe |

## Rodar os testes

```bash
./gradlew test
```

Para conferir também contra a planilha de verdade, que **não** está no
repositório:

```bash
TABELA_REAL="$HOME/Downloads/TABELA GERAL 25-08.xlsx" ./gradlew test
```

Sem a variável, esse teste é pulado e o resto continua rodando.

## Sobre os dados

A planilha real traz o preço de custo do fornecedor e por isso **nunca** entra
no Git — o `.gitignore` bloqueia `*.xlsx` por padrão.

Os testes usam `parser/src/testFixtures/resources/tabela-exemplo.xlsx`, gerado por:

```bash
python3 tools/anonimizar.py "$HOME/Downloads/TABELA GERAL 25-08.xlsx" \
  parser/src/testFixtures/resources/tabela-exemplo.xlsx
```

O script troca todo custo em dólar por um valor fictício estável e preserva de
propósito tudo que o parser precisa enfrentar: as 907 linhas, os dois arranjos
de coluna, as duas linhas separadoras, as células com `#VALUE!` e as 153 linhas
em que a coluna B derruba os centavos do preço da descrição.

## O que a planilha ensinou

- A coluna A é texto cru colado de outro sistema, truncado por volta de 62
  caracteres e terminando em `|`.
- A cotação do dólar (5,23) está chumbada em toda fórmula. Vira parâmetro.
- Celular escreve `128GB/6` (armazenamento/RAM); notebook e tablet escrevem
  `36GB/1TB` (RAM/armazenamento). A ordem é invertida.
- `5G` é rede, não capacidade. `1T` é 1 TB de SSD.
- A cor é o último campo e o truncamento come parte dela. Sai em 89% das
  linhas; no resto o parser devolve `null` em vez de chutar.
