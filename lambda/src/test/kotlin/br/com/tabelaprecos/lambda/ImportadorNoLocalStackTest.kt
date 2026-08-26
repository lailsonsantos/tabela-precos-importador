package br.com.tabelaprecos.lambda

import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest

/**
 * O caminho inteiro contra um S3 de verdade: a planilha vai para o bucket, o
 * evento chega como o S3 mandaria e a carga sai pronta para a API.
 *
 * <p>O que este teste pega e o dublê não pegaria: chave com espaço, endpoint
 * apontando para outro host e o objeto realmente existindo no bucket.
 */
class ImportadorNoLocalStackTest {

    companion object {

        private const val BUCKET = "tabelas-do-fornecedor"

        private val localstack = LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.8")
        ).withServices(LocalStackContainer.Service.S3)

        private lateinit var s3: S3Client

        @JvmStatic
        @BeforeAll
        fun subir() {
            localstack.start()
            s3 = S3Client.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.accessKey, localstack.secretKey)
                    )
                )
                .region(Region.of(localstack.region))
                .forcePathStyle(true)
                .build()
            s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build())
        }

        @JvmStatic
        @AfterAll
        fun descer() {
            s3.close()
            localstack.stop()
        }
    }

    private fun enviar(chave: String) {
        fixture().use { conteudo ->
            val bytes = conteudo.readBytes()
            s3.putObject(
                PutObjectRequest.builder().bucket(BUCKET).key(chave).build(),
                RequestBody.fromBytes(bytes),
            )
        }
    }

    private fun leitorApontandoParaOLocalStack(): LeitorDeObjeto {
        // As credenciais do LocalStack entram pelo ambiente do processo, do
        // mesmo jeito que a AWS injeta as da função.
        System.setProperty("aws.accessKeyId", localstack.accessKey)
        System.setProperty("aws.secretAccessKey", localstack.secretKey)
        val endpoint: URI = localstack.getEndpointOverride(LocalStackContainer.Service.S3)
        return LeitorDoS3(endpoint = endpoint.toString(), regiao = localstack.region)
    }

    @Test
    fun `le do bucket e monta a carga completa`() {
        val chave = "tabelas/2026-08-25/tabela.xlsx"
        enviar(chave)
        val api = ApiFalsa()

        val resumo = ImportadorHandler(leitorApontandoParaOLocalStack(), api)
            .handleRequest(eventoDoS3(BUCKET, chave), null)

        assertThat(api.recebidas).hasSize(1)
        val carga = api.recebidas.first()
        assertThat(carga.itens).hasSize(905)
        assertThat(carga.origem).isEqualTo(chave)
        assertThat(carga.dataReferencia.toString()).isEqualTo("2026-08-25")
        assertThat(resumo).contains("905 itens")
    }

    @Test
    @DisplayName("arquivo com espaço no nome, que é como o fornecedor manda")
    fun `chave com espaco funciona ponta a ponta`() {
        val chave = "tabelas/TABELA GERAL 25-08.xlsx"
        enviar(chave)
        val api = ApiFalsa()

        // O evento do S3 chega com o espaço escapado.
        ImportadorHandler(leitorApontandoParaOLocalStack(), api)
            .handleRequest(eventoDoS3(BUCKET, "tabelas/TABELA+GERAL+25-08.xlsx"), null)

        assertThat(api.recebidas.first().itens).hasSize(905)
    }

    @Test
    fun `objeto inexistente falha dizendo o que faltou`() {
        val api = ApiFalsa()

        assertThatThrownBy {
            ImportadorHandler(leitorApontandoParaOLocalStack(), api)
                .handleRequest(eventoDoS3(BUCKET, "tabelas/nao-existe.xlsx"), null)
        }.isInstanceOf(NoSuchKeyException::class.java)

        assertThat(api.recebidas).isEmpty()
    }
}
