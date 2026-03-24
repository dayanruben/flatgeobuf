package org.wololo.flatgeobuf.jts

import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.io.WKTWriter
import org.wololo.flatgeobuf.GeometryData
import org.wololo.flatgeobuf.GeometryType
import kotlin.test.Test
import kotlin.test.assertEquals

class GeometryDataJtsTest {
    private val reader = WKTReader()
    private val writer = WKTWriter()

    @Test
    fun roundTripsPoint() {
        assertRoundTrip("POINT (1.2 -2.1)")
    }

    @Test
    fun roundTripsPolygonWithHole() {
        assertRoundTrip("POLYGON ((35 10, 45 45, 15 40, 10 20, 35 10), (20 30, 35 35, 30 20, 20 30))")
    }

    @Test
    fun roundTripsMultiPolygon() {
        assertRoundTrip("MULTIPOLYGON (((40 40, 20 45, 45 30, 40 40)), ((20 35, 10 30, 10 10, 30 5, 45 20, 20 35), (30 20, 20 15, 20 25, 30 20)))")
    }

    @Test
    fun mapsCircularStringToLineString() {
        val geometry = GeometryData(
            type = GeometryType.CircularString,
            xy = doubleArrayOf(0.0, 0.0, 1.0, 1.0, 2.0, 0.0),
        )

        assertEquals("LINESTRING (0 0, 1 1, 2 0)", writer.write(geometry.toJts()))
    }

    @Test
    fun mapsMultiCurveToMultiLineString() {
        val geometry = GeometryData(
            type = GeometryType.MultiCurve,
            xy = doubleArrayOf(),
            parts = listOf(
                GeometryData(GeometryType.LineString, doubleArrayOf(0.0, 0.0, 1.0, 0.0)),
                GeometryData(GeometryType.CircularString, doubleArrayOf(1.0, 0.0, 2.0, 1.0, 3.0, 0.0)),
            ),
        )

        assertEquals(
            "MULTILINESTRING ((0 0, 1 0), (1 0, 2 1, 3 0))",
            writer.write(geometry.toJts()),
        )
    }

    @Test
    fun mapsTinToMultiPolygon() {
        val geometry = GeometryData(
            type = GeometryType.Tin,
            xy = doubleArrayOf(),
            parts = listOf(
                GeometryData(
                    type = GeometryType.Triangle,
                    xy = doubleArrayOf(0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 0.0),
                ),
                GeometryData(
                    type = GeometryType.Triangle,
                    xy = doubleArrayOf(1.0, 0.0, 1.0, 1.0, 2.0, 0.0, 1.0, 0.0),
                ),
            ),
        )

        assertEquals(
            "MULTIPOLYGON (((0 0, 0 1, 1 0, 0 0)), ((1 0, 1 1, 2 0, 1 0)))",
            writer.write(geometry.toJts()),
        )
    }

    private fun assertRoundTrip(wkt: String) {
        val geometry = reader.read(wkt)
        val geometryData = geometry.toGeometryData()
        val roundTripped = geometryData.toJts()
        assertEquals(writer.write(geometry), writer.write(roundTripped))
    }
}
