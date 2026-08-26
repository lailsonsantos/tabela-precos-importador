package br.com.tabelaprecos.lambda

import java.io.InputStream
import java.net.URI
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest

/** De onde os bytes da planilha vêm. Interface para o teste não precisar de nuvem. */
fun interface LeitorDeObjeto {

    fun abrir(bucket: String, chave: String): InputStream
}

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
