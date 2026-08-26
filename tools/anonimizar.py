#!/usr/bin/env python3
"""Gera a fixture de teste a partir da planilha real do fornecedor.

Troca todo preço de custo em dólar por um valor fictício e determinístico
(derivado do SKU), mantendo intactas a estrutura, as descrições, o markup,
o frete e — de propósito — as duas peculiaridades que os testes precisam:

  * os centavos de origem (para continuar existindo linha em que a
    coluna B é o piso do preço que aparece na descrição);
  * as linhas separadoras e as células com #VALUE!.

Uso:
    python3 tools/anonimizar.py "ENTRADA.xlsx" parser/src/testFixtures/resources/tabela-exemplo.xlsx
"""
import re
import shutil
import sys
import tempfile
import zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
Q = "{%s}" % NS
ET.register_namespace("", NS)

COTACAO = 5.23  # a mesma constante chumbada na planilha original
PRECO_FINAL = re.compile(r"^(.*/\s*(?:UNI)?\s*)(\d+)(\.\d+)?(\s*\|?\s*)$")
SKU = re.compile(r"^(\d{3,6}-\d)\b")


def preco_ficticio(sku: str) -> int:
    """Valor estável entre 50 e 949, mesmo SKU sempre no mesmo preço."""
    h = 0
    for ch in sku:
        h = (h * 31 + ord(ch)) & 0xFFFFFFFF
    return 50 + (h % 900)


def coluna(ref: str) -> int:
    n = 0
    for ch in re.match(r"([A-Z]+)", ref).group(1):
        n = n * 26 + ord(ch) - 64
    return n - 1


def valor(celula):
    v = celula.find(Q + "v")
    return v.text if v is not None else None


def escreve(celula, numero: float):
    v = celula.find(Q + "v")
    if v is None:
        v = ET.SubElement(celula, Q + "v")
    v.text = repr(round(numero, 10))


def main(entrada: Path, saida: Path) -> None:
    tmp = Path(tempfile.mkdtemp())
    with zipfile.ZipFile(entrada) as z:
        z.extractall(tmp)

    strings_doc = ET.parse(tmp / "xl/sharedStrings.xml")
    sis = strings_doc.getroot().findall(Q + "si")
    textos = ["".join(t.text or "" for t in si.iter(Q + "t")) for si in sis]

    trocados = 0
    for sheet in sorted((tmp / "xl/worksheets").glob("sheet*.xml")):
        doc = ET.parse(sheet)
        for linha in doc.getroot().iter(Q + "row"):
            celulas = {}
            for c in linha.findall(Q + "c"):
                celulas[coluna(c.get("r"))] = c

            a = celulas.get(0)
            if a is None or a.get("t") != "s":
                continue
            idx = int(valor(a))
            texto = textos[idx]

            sku = SKU.match(texto)
            m = PRECO_FINAL.match(texto)
            if not sku or not m:
                continue  # linha separadora: fica como está

            centavos = m.group(3) or ""
            novo_inteiro = preco_ficticio(sku.group(1))
            textos[idx] = f"{m.group(1)}{novo_inteiro}{centavos}{m.group(4)}"
            alvo = sis[idx].findall(".//" + Q + "t")[-1]
            alvo.text = textos[idx]
            alvo.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")

            # Coluna B é sempre o piso do preço da descrição.
            escreve(celulas[1], float(novo_inteiro))

            # Recalcula os valores em cache das colunas derivadas, para a
            # fixture continuar aritmeticamente coerente.
            c2 = celulas.get(2)
            bruto = float(novo_inteiro) * COTACAO
            if c2 is not None and float(valor(c2)) < 1:      # layout com markup
                markup = float(valor(c2))
                frete = float(valor(celulas[3]))
                escreve(celulas[4], bruto)
                escreve(celulas[5], bruto * markup)
                escreve(celulas[6], bruto + bruto * markup + frete)
            elif c2 is not None:                              # layout semi novos
                frete = float(valor(c2))
                escreve(celulas[3], bruto)
                escreve(celulas[4], bruto + frete)
            trocados += 1
        doc.write(sheet, xml_declaration=True, encoding="UTF-8")

    strings_doc.write(tmp / "xl/sharedStrings.xml", xml_declaration=True, encoding="UTF-8")

    saida.parent.mkdir(parents=True, exist_ok=True)
    if saida.exists():
        saida.unlink()
    with zipfile.ZipFile(saida, "w", zipfile.ZIP_DEFLATED) as z:
        for arquivo in sorted(tmp.rglob("*")):
            if arquivo.is_file():
                z.write(arquivo, arquivo.relative_to(tmp).as_posix())
    shutil.rmtree(tmp)
    print(f"{trocados} preços trocados -> {saida}")


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))
