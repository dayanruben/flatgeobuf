package org.wololo.flatgeobuf.jvm

import org.wololo.flatgeobuf.ColumnMeta
import org.wololo.flatgeobuf.ColumnType
import org.wololo.flatgeobuf.FgbFeature
import org.wololo.flatgeobuf.FgbReader
import org.wololo.flatgeobuf.FgbWriter
import org.wololo.flatgeobuf.GeometryData
import org.wololo.flatgeobuf.GeometryType
import org.wololo.flatgeobuf.HeaderMeta
import org.wololo.flatgeobuf.PropertyValue
import org.wololo.flatgeobuf.SequentialFgbWriter
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamingFgbWriterJvmTest {
    @Test
    fun streamsIndexedWritesToOutputStream() {
        val output = ByteArrayOutputStream()

        FgbWriter.writeTo(
            output = output,
            header = sampleHeader(geometryType = GeometryType.Polygon),
            features = sampleFeatures(),
            includeIndex = true,
        )

        val reader = FgbReader.open(output.toByteArray())
        val match = reader.selectBbox(10.5, 10.5, 10.8, 10.8).single()

        assertTrue(reader.header.hasIndex)
        assertEquals("east", (match.properties["name"] as PropertyValue.StringValue).value)
    }

    @Test
    fun writesSequentialFeatureStreamWithoutIndex() {
        val output = ByteArrayOutputStream()
        val header = sampleHeader(geometryType = GeometryType.Polygon).copy(featuresCount = 0, indexNodeSize = 0)

        val writer = SequentialFgbWriter.open(output, header)
        sampleFeatures().forEach(writer::write)
        writer.close()

        val reader = FgbReader.open(output.toByteArray())
        val features = reader.selectAll().toList()

        assertFalse(reader.header.hasIndex)
        assertEquals(2, features.size)
        assertEquals("west", (features[0].properties["name"] as PropertyValue.StringValue).value)
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
            ColumnMeta(
                name = "name",
                type = ColumnType.String,
                title = null,
                description = null,
                width = -1,
                precision = -1,
                scale = -1,
                nullable = true,
                unique = false,
                primaryKey = false,
                metadata = null,
            ),
            ColumnMeta(
                name = "rank",
                type = ColumnType.Int,
                title = null,
                description = null,
                width = -1,
                precision = -1,
                scale = -1,
                nullable = true,
                unique = false,
                primaryKey = false,
                metadata = null,
            ),
        )

    private fun sampleFeatures(): List<FgbFeature> =
        listOf(
            feature("west", 1, minX = 0.0, minY = 0.0, maxX = 1.0, maxY = 1.0),
            feature("east", 2, minX = 10.0, minY = 10.0, maxX = 11.0, maxY = 11.0),
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
}
