package org.wololo.flatgeobuf

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SequentialFgbWriterTest {
    @Test
    fun writesFeaturesToCustomSink() {
        val sink = CollectingSink()
        val writer = SequentialFgbWriter.open(sink, sampleHeader(featuresCount = 0, indexNodeSize = 0))

        sampleFeatures().forEach(writer::write)
        writer.close()

        val features = FgbReader.open(sink.toByteArray()).selectAll().toList()
        assertEquals(2, features.size)
        assertEquals("east", (features.last().properties["name"] as PropertyValue.StringValue).value)
    }

    @Test
    fun rejectsIndexedSequentialHeader() {
        assertFailsWith<FlatGeobufException> {
            SequentialFgbWriter.open(CollectingSink(), sampleHeader(featuresCount = 2, indexNodeSize = 16))
        }
    }

    @Test
    fun rejectsFeatureWritesAfterClose() {
        val writer = SequentialFgbWriter.open(CollectingSink(), sampleHeader(featuresCount = 0, indexNodeSize = 0))
        writer.close()

        assertFailsWith<FlatGeobufException> {
            writer.write(sampleFeatures().first())
        }
    }

    @Test
    fun validatesDeclaredFeatureCountOnClose() {
        val writer = SequentialFgbWriter.open(CollectingSink(), sampleHeader(featuresCount = 1, indexNodeSize = 0))
        writer.write(sampleFeatures().first())
        writer.write(sampleFeatures().last())

        assertFailsWith<FlatGeobufException> {
            writer.close()
        }
    }

    @Test
    fun streamsFullWriteToCustomSink() {
        val sink = CollectingSink()
        val bytes = FgbWriter.write(
            header = sampleHeader(featuresCount = 0, indexNodeSize = 0),
            features = sampleFeatures(),
            includeIndex = true,
        )

        FgbWriter.writeTo(
            sink = sink,
            header = sampleHeader(featuresCount = 0, indexNodeSize = 0),
            features = sampleFeatures(),
            includeIndex = true,
        )

        assertContentEquals(bytes, sink.toByteArray())
    }

    private fun sampleHeader(featuresCount: Long, indexNodeSize: Int): HeaderMeta =
        HeaderMeta(
            name = "demo",
            envelope = null,
            geometryType = GeometryType.Polygon,
            hasZ = false,
            hasM = false,
            hasT = false,
            hasTm = false,
            columns = sampleColumns(),
            featuresCount = featuresCount,
            indexNodeSize = indexNodeSize,
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

    private class CollectingSink : FgbWriteSink {
        private val chunks = mutableListOf<ByteArray>()

        override fun write(bytes: ByteArray) {
            chunks += bytes
        }

        fun toByteArray(): ByteArray {
            val size = chunks.sumOf { it.size }
            val output = ByteArray(size)
            var offset = 0
            chunks.forEach { chunk ->
                chunk.copyInto(output, offset)
                offset += chunk.size
            }
            return output
        }
    }
}
