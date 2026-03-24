package org.wololo.flatgeobuf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FgbWriterJvmTest {
    @Test
    fun writesAndReadsDatasetWithoutIndexPreservingOrder() {
        val bytes = FgbWriter.write(
            header = sampleHeader(geometryType = GeometryType.Polygon),
            features = sampleFeatures(),
            includeIndex = false,
        )

        val reader = FgbReader.open(bytes)
        val features = reader.selectAll().toList()

        assertEquals(2, features.size)
        assertFalse(reader.header.hasIndex)
        assertEquals("west", (features[0].properties["name"] as PropertyValue.StringValue).value)
        assertEquals("east", (features[1].properties["name"] as PropertyValue.StringValue).value)
    }

    @Test
    fun writesAndReadsDatasetWithIndex() {
        val bytes = FgbWriter.write(
            header = sampleHeader(geometryType = GeometryType.Polygon),
            features = sampleFeatures(),
            includeIndex = true,
        )

        val reader = FgbReader.open(bytes)
        val features = reader.selectAll().toList()
        val bboxMatch = reader.selectBbox(10.5, 10.5, 10.8, 10.8).single()

        assertTrue(reader.header.hasIndex)
        assertEquals(2, features.size)
        assertEquals("east", (bboxMatch.properties["name"] as PropertyValue.StringValue).value)
    }

    @Test
    fun roundTripsUnknownGeometryTypeWithPerFeatureGeometry() {
        val points = listOf(
            FgbFeature(
                id = 0,
                offset = 0,
                geometry = GeometryData(
                    type = GeometryType.Point,
                    xy = doubleArrayOf(1.0, 2.0),
                ),
                properties = mapOf("name" to PropertyValue.StringValue("origin")),
                columns = sampleColumns(),
            ),
        )

        val bytes = FgbWriter.write(
            header = sampleHeader(geometryType = GeometryType.Unknown),
            features = points,
            includeIndex = true,
        )

        val feature = FgbReader.open(bytes).selectAll().single()

        assertEquals(GeometryType.Point, feature.geometry?.type)
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
