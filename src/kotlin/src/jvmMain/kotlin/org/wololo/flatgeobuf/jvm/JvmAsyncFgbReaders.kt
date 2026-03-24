package org.wololo.flatgeobuf.jvm

import org.wololo.flatgeobuf.AsyncByteRangeSource
import org.wololo.flatgeobuf.AsyncFgbReader
import org.wololo.flatgeobuf.HeaderMeta
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

public class HttpRangeSource private constructor(
    private val client: HttpClient,
    private val uri: URI,
    private val headers: Map<String, String>,
    override var size: Long?,
) : AsyncByteRangeSource {
    override suspend fun read(offset: Long, length: Int): ByteArray {
        if (length == 0) return ByteArray(0)
        val request = HttpRequest.newBuilder(uri)
            .header("Range", "bytes=$offset-${offset + length - 1}")
            .GET()
            .apply {
                headers.forEach(::header)
            }
            .build()
        val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).await()
        updateSize(response)
        return when (response.statusCode()) {
            200 -> {
                if (offset != 0L) {
                    throw IllegalStateException("Server ignored byte range request for $uri")
                }
                response.body().copyOf(minOf(length, response.body().size))
            }

            206 -> response.body().copyOf(minOf(length, response.body().size))
            416 -> ByteArray(0)
            else -> throw IllegalStateException("HTTP ${response.statusCode()} while reading $uri")
        }
    }

    public companion object {
        public suspend fun open(
            url: String,
            headers: Map<String, String> = emptyMap(),
            client: HttpClient = HttpClient.newHttpClient(),
        ): HttpRangeSource {
            val uri = URI.create(url)
            val size = probeContentLength(client, uri, headers)
            return HttpRangeSource(client, uri, headers, size)
        }

        private suspend fun probeContentLength(
            client: HttpClient,
            uri: URI,
            headers: Map<String, String>,
        ): Long? {
            val request = HttpRequest.newBuilder(uri)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .apply {
                    headers.forEach(::header)
                }
                .build()
            val response = client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).await()
            if (response.statusCode() !in 200..299) return null
            return response.headers().firstValue("Content-Length").orElse(null)?.toLongOrNull()
        }
    }

    private fun updateSize(response: HttpResponse<*>) {
        if (size != null) return
        val contentRange = response.headers().firstValue("Content-Range").orElse(null)
        if (contentRange != null) {
            size = contentRange.substringAfter('/').toLongOrNull()
        }
        if (size == null) {
            size = response.headers().firstValue("Content-Length").orElse(null)?.toLongOrNull()
        }
    }
}

public suspend fun AsyncFgbReader.Companion.open(
    url: String,
    headers: Map<String, String> = emptyMap(),
    client: HttpClient = HttpClient.newHttpClient(),
): AsyncFgbReader = open(HttpRangeSource.open(url, headers, client))

public suspend fun AsyncFgbReader.Companion.readHeader(
    url: String,
    headers: Map<String, String> = emptyMap(),
    client: HttpClient = HttpClient.newHttpClient(),
): HeaderMeta = readHeader(HttpRangeSource.open(url, headers, client))

private suspend fun <T> CompletableFuture<T>.await(): T =
    suspendCoroutine { continuation ->
        whenComplete { value, error ->
            if (error != null) {
                continuation.resumeWithException(error)
            } else {
                continuation.resume(value)
            }
        }
    }
