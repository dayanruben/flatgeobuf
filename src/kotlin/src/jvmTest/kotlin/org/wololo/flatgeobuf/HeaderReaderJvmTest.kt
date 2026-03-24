package org.wololo.flatgeobuf

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HeaderReaderJvmTest {
    @Test
    fun readsCountriesHeaderMetadata() {
        val bytes = fixtureBytes("countries.fgb")

        val header = FlatGeobuf.readHeader(bytes)

        assertEquals("countries", header.name)
        assertEquals(GeometryType.MultiPolygon, header.geometryType)
        assertEquals(179L, header.featuresCount)
        assertTrue(header.hasIndex)
        assertEquals(16, header.indexNodeSize)
        assertNotNull(header.crs)
        assertEquals(4326, header.crs.code)
        assertEquals(-180, header.envelope?.minX?.toInt())
    }

    @Test
    fun searchesCountriesIndexUsingHeaderEnvelope() {
        val bytes = fixtureBytes("countries.fgb")
        val header = FlatGeobuf.readHeader(bytes)
        val envelope = requireNotNull(header.envelope)

        val hits = FlatGeobuf.searchIndex(
            bytes = bytes,
            minX = envelope.minX,
            minY = envelope.minY,
            maxX = envelope.maxX,
            maxY = envelope.maxY,
        )

        assertEquals(header.featuresCount.toInt(), hits.size)
    }

    @Test
    fun readsHeaderWhenFeatureCountIsUnknown() {
        val bytes = fixtureBytes("unknown_feature_count.fgb")

        val header = FlatGeobuf.readHeader(bytes)

        assertEquals(0L, header.featuresCount)
        assertTrue(!header.hasIndex)
    }

    @Test
    fun selectsAllFeaturesAndDecodesProperties() {
        val reader = FlatGeobuf.open(fixtureBytes("countries.fgb"))

        val features = reader.selectAll().toList()

        assertEquals(179, features.size)
        val firstWithName = features.firstOrNull {
            it.properties["name"] is PropertyValue.StringValue
        }
        assertNotNull(firstWithName)
        val name = (firstWithName.properties["name"] as PropertyValue.StringValue).value
        assertTrue(name.isNotBlank())
        assertNotNull(firstWithName.geometry)
        assertTrue(hasCoordinates(firstWithName.geometry))
    }

    @Test
    fun selectsBboxUsingIndexAndDecodesAustralia() {
        val reader = FlatGeobuf.open(fixtureBytes("countries.fgb"))

        val matches = reader.selectBbox(
            minX = 134.0,
            minY = -25.5,
            maxX = 134.2,
            maxY = -25.3,
        ).toList()

        assertEquals(1, matches.size)
        val name = (matches.single().properties["name"] as PropertyValue.StringValue).value
        assertEquals("Australia", name)
    }

    @Test
    fun selectsAllWhenFeatureCountIsUnknown() {
        val reader = FlatGeobuf.open(fixtureBytes("unknown_feature_count.fgb"))

        val features = reader.selectAll().toList()

        assertTrue(features.isNotEmpty())
    }

    private fun fixtureBytes(name: String): ByteArray =
        Files.readAllBytes(Path.of("..", "..", "test", "data", name).normalize())

    private fun hasCoordinates(geometry: GeometryData?): Boolean {
        if (geometry == null) return false
        if (geometry.xy.isNotEmpty()) return true
        return geometry.parts.any(::hasCoordinates)
    }
}
