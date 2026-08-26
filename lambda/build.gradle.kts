plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":preco"))
    implementation(platform(libs.aws.bom))
    implementation(libs.aws.s3)
    implementation(libs.lambda.core)
    implementation(libs.lambda.events)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(testFixtures(project(":parser")))
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.localstack)
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<Test>().configureEach {
    // O Docker 29 recusa versões de API abaixo da 1.40 e o cliente que o
    // Testcontainers embute ainda negocia a 1.32.
    systemProperty(
        "api.version",
        providers.environmentVariable("DOCKER_API_VERSION").getOrElse("1.44"))
}

/** Zip que o Lambda espera: as classes na raiz e as dependências em lib/. */
val pacoteDoLambda by tasks.registering(Zip::class) {
    archiveFileName.set("importador.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(tasks.named("jar"))
    from(configurations.runtimeClasspath) { into("lib") }
}

tasks.named("build") { dependsOn(pacoteDoLambda) }
