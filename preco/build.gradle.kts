plugins {
    // `api` em vez de `implementation`: a assinatura de CalculadoraPreco expõe
    // ItemPlanilha, então quem usa este módulo precisa enxergar o parser.
    `java-library`
}

dependencies {
    api(project(":parser"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)

    // O teste-oráculo confere o resultado contra as próprias colunas da
    // planilha, então precisa abrir o arquivo por conta própria.
    testImplementation(libs.poi.ooxml)
    testImplementation(testFixtures(project(":parser")))
}
