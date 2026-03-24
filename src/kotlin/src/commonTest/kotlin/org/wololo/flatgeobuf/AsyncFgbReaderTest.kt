package org.wololo.flatgeobuf

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AsyncFgbReaderTest {
    @Test
    fun readsFeaturesFromInMemoryRangeSource() {
        val bytes = FgbWriter.write(
            header = sampleHeader(geometryType = GeometryType.Polygon),
            features = sampleFeatures(),
            includeIndex = false,
        )

        val reader = runSuspend { AsyncFgbReader.open(InMemoryRangeSource(bytes)) }
        val features = runSuspend { reader.selectAll() }

        assertEquals(2, features.size)
        assertEquals("west", (features.first().properties["name"] as PropertyValue.StringValue).value)
    }

    @Test
    fun selectsBboxFromInMemoryIndexedSource() {
        val bytes = FgbWriter.write(
            header = sampleHeader(geometryType = GeometryType.Polygon),
            features = sampleFeatures(),
            includeIndex = true,
        )

        val reader = runSuspend { AsyncFgbReader.open(InMemoryRangeSource(bytes)) }
        val matches = runSuspend { reader.selectBbox(10.5, 10.5, 10.8, 10.8) }

        assertEquals(1, matches.size)
        assertEquals("east", (matches.single().properties["name"] as PropertyValue.StringValue).value)
    }

    @Test
    fun rejectsBboxWhenSourceHasNoIndex() {
        val bytes = FgbWriter.write(
            header = sampleHeader(geometryType = GeometryType.Polygon),
            features = sampleFeatures(),
            includeIndex = false,
        )

        val reader = runSuspend { AsyncFgbReader.open(InMemoryRangeSource(bytes)) }

        assertFailsWith<FlatGeobufException> {
            runSuspend { reader.selectBbox(0.0, 0.0, 1.0, 1.0) }
        }
    }

    @Test
    fun rejectsTruncatedHeader() {
        assertFailsWith<FlatGeobufException> {
            runSuspend {
                AsyncFgbReader.open(
                    object : AsyncByteRangeSource {
                        override val size: Long? = 2

                        override suspend fun read(offset: Long, length: Int): ByteArray =
                            byteArrayOf(0x66, 0x67)
                    },
                )
            }
        }
    }

    private fun sampleHeader(geometryType: GeometryType): HeaderMeta =
        HeaderMeta(
            name = "demo",
            envelope = null,
            geometryType = geometryType,
            hasZ = false,
            hasM = false,
            hasT = false,
            hasTm = false,
            columns = sampleColumns(),
            featuresCount = 0,
            indexNodeSize = 0,
            crs = null,
            title = null,
            description = null,
            metadata = null,
            headerSize = 0,
            headerOffset = 0,
            indexOffset = 0,
            indexSize = 0,
            featureSectionOffset = 0,
        )

    private fun sampleColumns(): List<ColumnMeta> =
        listOf(
            ColumnMeta("name", ColumnType.String, null, null, -1, -1, -1, true, false, false, null),
            ColumnMeta("rank", ColumnType.Int, null, null, -1, -1, -1, true, false, false, null),
        )

    private fun sampleFeatures(): List<FgbFeature> =
        listOf(
            feature("west", 1, 0.0, 0.0, 1.0, 1.0),
            feature("east", 2, 10.0, 10.0, 11.0, 11.0),
        )

    private fun feature(
        name: String,
        rank: Int,
        minX: Double,
        minY: Double,
        maxX: Double,
        maxY: Double,
    ): FgbFeature =
        FgbFeature(
            id = 0,
            offset = 0,
            geometry = GeometryData(
                type = GeometryType.Polygon,
                xy = doubleArrayOf(
                    minX, minY,
                    minX, maxY,
                    maxX, maxY,
                    maxX, minY,
                    minX, minY,
                ),
            ),
            properties = mapOf(
                "name" to PropertyValue.StringValue(name),
                "rank" to PropertyValue.IntValue(rank),
            ),
            columns = sampleColumns(),
        )

    private class InMemoryRangeSource(
        private val bytes: ByteArray,
    ) : AsyncByteRangeSource {
        override val size: Long = bytes.size.toLong()

        override suspend fun read(offset: Long, length: Int): ByteArray {
            if (offset >= bytes.size) return ByteArray(0)
            val start = offset.toInt()
            val end = minOf(bytes.size, start + length)
            return bytes.copyOfRange(start, end)
        }
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        })
        return outcome!!.getOrThrow()
    }
}
