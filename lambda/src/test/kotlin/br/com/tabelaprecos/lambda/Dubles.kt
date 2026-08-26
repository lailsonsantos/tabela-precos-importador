package br.com.tabelaprecos.lambda

import com.amazonaws.services.lambda.runtime.events.S3Event
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification
import java.io.InputStream
import java.math.BigDecimal

/** Evento igual ao que o S3 entrega, montado à mão. */
fun eventoDoS3(bucket: String, chave: String): S3Event {
    val entidade = S3EventNotification.S3Entity(
        "configuracao",
        S3EventNotification.S3BucketEntity(bucket, null, "arn:aws:s3:::$bucket"),
        S3EventNotification.S3ObjectEntity(chave, 1L, "etag", "versao", "sequencia"),
        "1.0",
    )
    val registro = S3EventNotification.S3EventNotificationRecord(
        "us-east-1",
        "ObjectCreated:Put",
        "aws:s3",
        "2026-08-26T12:00:00.000Z",
        "2.1",
        null,
        null,
        entidade,
        null,
    )
    return S3Event(listOf(registro))
}

fun fixture(): InputStream =
    checkNotNull(object {}.javaClass.getResourceAsStream("/tabela-exemplo.xlsx")) {
        "fixture tabela-exemplo.xlsx não encontrada"
    }

class ApiFalsa(
    private val cotacao: BigDecimal = BigDecimal("5.23"),
    private val falhaAoRegistrar: RuntimeException? = null,
) : ApiDeCatalogo {

    val recebidas = mutableListOf<Carga>()
    var consultasDeCotacao = 0
        private set

    override fun cotacaoAtual(): BigDecimal {
        consultasDeCotacao++
        return cotacao
    }

    override fun registrar(carga: Carga): String {
        falhaAoRegistrar?.let { throw it }
        recebidas += carga
        return """{"id":1,"itens":${carga.itens.size}}"""
    }
}
