package org.wololo.flatgeobuf.geojson

import org.wololo.flatgeobuf.ColumnType
import org.wololo.flatgeobuf.FgbReader
import org.wololo.flatgeobuf.FgbWriter
import org.wololo.flatgeobuf.PropertyValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GeoJsonAdaptersTest {
    @Test
    fun infersHeaderAndRoundTripsGeoJsonFeatures() {
        val collection = GeoJsonFeatureCollection(
            features = listOf(
                GeoJsonFeature(
                    id = GeoJsonId.LongValue(7),
                    geometry = GeoJsonGeometry.Point(GeoJsonPosition(-43.2, -22.9, 12.0)),
                    properties = mapOf(
                        "name" to GeoJsonValue.StringValue("Rio"),
                        "rank" to GeoJsonValue.NumberValue(1.0),
                    ),
                ),
            ),
        )

        val header = collection.inferHeader(name = "cities")
        val bytes = FgbWriter.write(
            header = header,
            features = collection.toFgbFeatures(header.columns),
            includeIndex = false,
        )

        val roundTrip = FgbReader.open(bytes).selectAllGeoJson().single()

        assertEquals("cities", header.name)
        assertEquals(ColumnType.String, header.columns.first { it.name == "name" }.type)
        assertEquals(ColumnType.Int, header.columns.first { it.name == "rank" }.type)
        assertIs<GeoJsonGeometry.Point>(roundTrip.geometry)
        assertEquals("Rio", (roundTrip.properties["name"] as GeoJsonValue.StringValue).value)
        assertEquals(1.0, (roundTrip.properties["rank"] as GeoJsonValue.NumberValue).value)
    }

    @Test
    fun convertsPolygonWithHoleToAndFromGeoJson() {
        val feature = GeoJsonFeature(
            geometry = GeoJsonGeometry.Polygon(
                coordinates = listOf(
                    listOf(
                        GeoJsonPosition(0.0, 0.0),
                        GeoJsonPosition(0.0, 5.0),
                        GeoJsonPosition(5.0, 5.0),
                        GeoJsonPosition(5.0, 0.0),
                        GeoJsonPosition(0.0, 0.0),
                    ),
                    listOf(
                        GeoJsonPosition(1.0, 1.0),
                        GeoJsonPosition(1.0, 2.0),
                        GeoJsonPosition(2.0, 2.0),
                        GeoJsonPosition(2.0, 1.0),
                        GeoJsonPosition(1.0, 1.0),
                    ),
                ),
            ),
            properties = mapOf(
                "name" to GeoJsonValue.StringValue("parcel"),
                "payload" to GeoJsonValue.ArrayValue(
                    listOf(
                        GeoJsonValue.NumberValue(1.0),
                        GeoJsonValue.NumberValue(2.0),
                        GeoJsonValue.NumberValue(3.0),
                    ),
                ),
            ),
        )

        val fgbFeature = feature.toFgbFeature()
        val roundTrip = fgbFeature.toGeoJson()

        val polygon = assertIs<GeoJsonGeometry.Polygon>(roundTrip.geometry)
        assertEquals(2, polygon.coordinates.size)
        val payload = roundTrip.properties["payload"]
        assertIs<GeoJsonValue.ArrayValue>(payload)
        assertEquals(3, payload.value.size)
        assertTrue((fgbFeature.properties["payload"] as PropertyValue.BinaryValue).value.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun infersUnknownGeometryTypeForMixedCollection() {
        val collection = GeoJsonFeatureCollection(
            listOf(
                GeoJsonFeature(geometry = GeoJsonGeometry.Point(GeoJsonPosition(0.0, 0.0))),
                GeoJsonFeature(
                    geometry = GeoJsonGeometry.LineString(
                        listOf(
                            GeoJsonPosition(0.0, 0.0),
                            GeoJsonPosition(1.0, 1.0),
                        ),
                    ),
                ),
            ),
        )

        val header = collection.inferHeader()

        assertEquals(org.wololo.flatgeobuf.GeometryType.Unknown, header.geometryType)
    }

    @Test
    fun rejectsIncompatibleInferredPropertyTypes() {
        val collection = GeoJsonFeatureCollection(
            listOf(
                GeoJsonFeature(geometry = null, properties = mapOf("value" to GeoJsonValue.StringValue("x"))),
                GeoJsonFeature(geometry = null, properties = mapOf("value" to GeoJsonValue.BooleanValue(true))),
            ),
        )

        assertFailsWith<org.wololo.flatgeobuf.FlatGeobufException> {
            collection.inferColumns()
        }
    }
}
