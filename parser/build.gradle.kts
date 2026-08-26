plugins {
    `java-test-fixtures`
}

dependencies {
    implementation(libs.poi.ooxml)

    // A planilha de exemplo fica em src/testFixtures/resources para o módulo
    // preco poder rodar contra ela sem uma segunda cópia do arquivo.
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}
