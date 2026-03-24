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
        assertEquals(3, header.envelope?.minX?.toInt())
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

    private fun fixtureBytes(name: String): ByteArray =
        Files.readAllBytes(Path.of("..", "..", "test", "data", name).normalize())
}
