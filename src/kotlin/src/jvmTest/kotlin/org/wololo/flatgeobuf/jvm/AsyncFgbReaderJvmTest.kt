package org.wololo.flatgeobuf.jvm

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.wololo.flatgeobuf.AsyncFgbReader
import org.wololo.flatgeobuf.PropertyValue
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AsyncFgbReaderJvmTest {
    @Test
    fun readsHeaderOverHttpRangeRequests() = withRangeServer("countries.fgb") { url ->
        val reader = runSuspend { AsyncFgbReader.open(url) }

        assertEquals(179L, reader.header.featuresCount)
        assertTrue(reader.header.hasIndex)
    }

    @Test
    fun selectsBboxOverHttpRangeRequests() = withRangeServer("countries.fgb") { url ->
        val reader = runSuspend { AsyncFgbReader.open(url) }
        val matches = runSuspend {
            reader.selectBbox(
                minX = 134.0,
                minY = -25.5,
                maxX = 134.2,
                maxY = -25.3,
            )
        }

        assertEquals(1, matches.size)
        assertEquals("Australia", (matches.single().properties["name"] as PropertyValue.StringValue).value)
    }

    private fun withRangeServer(name: String, block: (String) -> Unit) {
        val bytes = Files.readAllBytes(Path.of("..", "..", "test", "data", name).normalize())
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange -> handleExchange(exchange, bytes) }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}/$name")
        } finally {
            server.stop(0)
        }
    }

    private fun handleExchange(exchange: HttpExchange, bytes: ByteArray) {
        exchange.responseHeaders.add("Accept-Ranges", "bytes")
        when (exchange.requestMethod) {
            "HEAD" -> {
                exchange.responseHeaders.add("Content-Length", bytes.size.toString())
                exchange.sendResponseHeaders(200, -1)
            }

            "GET" -> {
                val range = exchange.requestHeaders.getFirst("Range")
                if (range == null) {
                    exchange.responseHeaders.add("Content-Length", bytes.size.toString())
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                } else {
                    val (start, end) = parseRange(range, bytes.size)
                    val chunk = bytes.copyOfRange(start, end + 1)
                    exchange.responseHeaders.add("Content-Length", chunk.size.toString())
                    exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${bytes.size}")
                    exchange.sendResponseHeaders(206, chunk.size.toLong())
                    exchange.responseBody.use { it.write(chunk) }
                }
            }

            else -> exchange.sendResponseHeaders(405, -1)
        }
        exchange.close()
    }

    private fun parseRange(header: String, totalSize: Int): Pair<Int, Int> {
        val rangeValue = header.removePrefix("bytes=")
        val start = rangeValue.substringBefore('-').toInt()
        val end = rangeValue.substringAfter('-', "").takeIf { it.isNotBlank() }?.toInt() ?: (totalSize - 1)
        return start to minOf(end, totalSize - 1)
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        val latch = CountDownLatch(1)
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                outcome = result
                latch.countDown()
            }
        })
        latch.await()
        return outcome!!.getOrThrow()
    }
}
