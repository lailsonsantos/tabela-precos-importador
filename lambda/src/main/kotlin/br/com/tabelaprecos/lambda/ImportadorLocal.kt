package br.com.tabelaprecos.lambda

import com.amazonaws.services.lambda.runtime.events.S3Event
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification

/**
 * Roda o importador na mão, sem AWS.
 *
 *
 * Serve para dois casos reais: desenvolver contra o LocalStack sem publicar
 * função nenhuma, e reprocessar um arquivo que já está no bucket quando a
 * invocação original falhou.
 *
 * <pre>
 * API_URL=http://localhost:8080 API_EMAIL=... API_SENHA=... \
 *   S3_ENDPOINT=http://localhost:4566 \
 *   ./gradlew :lambda:rodarLocal --args="tabelas tabelas/2026-08-26/abc.xlsx"
 * </pre>
 */
object ImportadorLocal {

    @JvmStatic
    fun main(argumentos: Array<String>) {
        if (argumentos.size != 2) {
            System.err.println("uso: ImportadorLocal <bucket> <chave>")
            kotlin.system.exitProcess(2)
        }
        val resumo = ImportadorHandler().handleRequest(evento(argumentos[0], argumentos[1]), null)
        println(resumo)
    }

    private fun evento(bucket: String, chave: String): S3Event {
        val entidade = S3EventNotification.S3Entity(
            "local",
            S3EventNotification.S3BucketEntity(bucket, null, "arn:aws:s3:::$bucket"),
            S3EventNotification.S3ObjectEntity(chave, 0L, null, null, null),
            "1.0",
        )
        return S3Event(
            listOf(
                S3EventNotification.S3EventNotificationRecord(
                    System.getenv("AWS_REGION") ?: "us-east-1",
                    "ObjectCreated:Put",
                    "aws:s3",
                    null,
                    "2.1",
                    null,
                    null,
                    entidade,
                    null,
                )
            )
        )
    }
}
