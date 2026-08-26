plugins {
    java
    // Declarado aqui sem aplicar: só o módulo lambda é Kotlin. O parser e o
    // motor de preço ficam em Java para a API poder reaproveitá-los um dia
    // sem carregar a biblioteca padrão do Kotlin junto.
    alias(libs.plugins.kotlin) apply false
}

allprojects {
    group = "br.com.tabelaprecos"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}
