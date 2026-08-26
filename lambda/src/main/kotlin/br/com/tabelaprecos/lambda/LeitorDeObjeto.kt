package br.com.tabelaprecos.lambda

import java.io.InputStream
import java.net.URI
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest

/**
 * De onde os bytes da planilha vêm.
 *
 * É uma interface funcional para o teste poder passar um lambda de três linhas
 * em vez de subir uma nuvem inteira só para conferir o que o handler faz com o
 * conteúdo.
 */
fun interface LeitorDeObjeto {

    /**
     * Abre o objeto para leitura.
     *
     * @param bucket nome do bucket, como veio no evento do S3
     * @param chave caminho do objeto, já decodificado
     * @return o conteúdo; quem chama é responsável por fechar
     * @throws software.amazon.awssdk.services.s3.model.NoSuchKeyException se o
     *     objeto não existe. Falhar aqui faz a invocação aparecer como erro e
     *     ser reprocessada, que é melhor do que mandar carga vazia.
     */
    fun abrir(bucket: String, chave: String): InputStream
}

/**
 * Lê do S3 de verdade.
 *
 * @param endpoint só preenchido para apontar ao LocalStack; na AWS fica nulo e
 *     o SDK descobre o endereço pela região
 * @param regiao na função, a própria AWS injeta `AWS_REGION` no ambiente
 */
class LeitorDoS3(
    endpoint: String? = System.getenv("S3_ENDPOINT"),
    regiao: String = System.getenv("AWS_REGION") ?: "us-east-1",
) : LeitorDeObjeto {

    private val s3: S3Client = S3Client.builder()
        .region(Region.of(regiao))
        .apply {
            // Só é preenchido para apontar ao LocalStack; na AWS fica vazio.
            if (!endpoint.isNullOrBlank()) {
                endpointOverride(URI.create(endpoint))
                forcePathStyle(true)
            }
        }
        .build()

    override fun abrir(bucket: String, chave: String): InputStream =
        s3.getObject(GetObjectRequest.builder().bucket(bucket).key(chave).build())
}
