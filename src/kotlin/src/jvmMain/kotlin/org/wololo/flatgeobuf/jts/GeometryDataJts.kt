package org.wololo.flatgeobuf.jts

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateXYZM
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.MultiLineString
import org.locationtech.jts.geom.MultiPoint
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon
import org.wololo.flatgeobuf.FgbFeature
import org.wololo.flatgeobuf.FlatGeobufException
import org.wololo.flatgeobuf.GeometryData
import org.wololo.flatgeobuf.GeometryType

public fun GeometryData.toJts(geometryFactory: GeometryFactory = GeometryFactory()): Geometry =
    when (type) {
        GeometryType.Unknown -> geometryFactory.createGeometryCollection(
            parts.map { it.toJts(geometryFactory) }.toTypedArray(),
        )

        GeometryType.Point -> {
            val coordinates = coordinates()
            if (coordinates.isEmpty()) geometryFactory.createPoint() else geometryFactory.createPoint(coordinates.first())
        }

        GeometryType.MultiPoint -> geometryFactory.createMultiPointFromCoords(coordinates().toTypedArray())
        GeometryType.LineString -> geometryFactory.createLineString(coordinates().toTypedArray())
        GeometryType.CircularString -> geometryFactory.createLineString(coordinates().toTypedArray())
        GeometryType.Curve -> geometryFactory.createLineString(coordinates().toTypedArray())
        GeometryType.MultiLineString -> geometryFactory.createMultiLineString(lineStrings(geometryFactory))
        GeometryType.CompoundCurve -> geometryFactory.createMultiLineString(curveParts(geometryFactory))
        GeometryType.MultiCurve -> geometryFactory.createMultiLineString(curveParts(geometryFactory))
        GeometryType.Polygon -> polygon(geometryFactory)
        GeometryType.CurvePolygon -> polygon(geometryFactory)
        GeometryType.Surface -> polygon(geometryFactory)
        GeometryType.Triangle -> polygon(geometryFactory)
        GeometryType.MultiPolygon -> geometryFactory.createMultiPolygon(
            parts.map { it.toJts(geometryFactory) as Polygon }.toTypedArray(),
        )
        GeometryType.MultiSurface -> geometryFactory.createMultiPolygon(surfaceParts(geometryFactory))
        GeometryType.PolyhedralSurface -> geometryFactory.createMultiPolygon(surfaceParts(geometryFactory))
        GeometryType.Tin -> geometryFactory.createMultiPolygon(surfaceParts(geometryFactory))

        GeometryType.GeometryCollection -> geometryFactory.createGeometryCollection(
            parts.map { it.toJts(geometryFactory) }.toTypedArray(),
        )
    }

public fun Geometry.toGeometryData(): GeometryData =
    when (this) {
        is Point -> GeometryData(
            type = GeometryType.Point,
            xy = flatXy(listOf(coordinate)),
            z = flatOrdinate(listOf(coordinate)) { current -> current?.z },
            m = flatOrdinate(listOf(coordinate)) { current -> current?.m },
        )

        is MultiPoint -> GeometryData(
            type = GeometryType.MultiPoint,
            xy = flatXy((0 until numGeometries).map { getGeometryN(it).coordinate }),
            z = flatOrdinate((0 until numGeometries).map { getGeometryN(it).coordinate }) { coordinate -> coordinate?.z },
            m = flatOrdinate((0 until numGeometries).map { getGeometryN(it).coordinate }) { coordinate -> coordinate?.m },
        )

        is LineString -> lineStringData(GeometryType.LineString)
        is MultiLineString -> multiLineStringData()
        is Polygon -> polygonData()
        is MultiPolygon -> GeometryData(
            type = GeometryType.MultiPolygon,
            xy = DoubleArray(0),
            parts = (0 until numGeometries).map { (getGeometryN(it) as Polygon).polygonData() },
        )

        is GeometryCollection -> GeometryData(
            type = GeometryType.GeometryCollection,
            xy = DoubleArray(0),
            parts = (0 until numGeometries).map { getGeometryN(it).toGeometryData() },
        )

        else -> throw FlatGeobufException("JTS adapter does not yet support ${geometryType}")
    }

public fun FgbFeature.geometryAsJts(geometryFactory: GeometryFactory = GeometryFactory()): Geometry? =
    geometry?.toJts(geometryFactory)

private fun GeometryData.coordinates(): List<Coordinate> {
    if (xy.isEmpty()) return emptyList()
    val coordinates = ArrayList<Coordinate>(xy.size / 2)
    var index = 0
    while (index + 1 < xy.size) {
        val pointIndex = index / 2
        val x = xy[index]
        val y = xy[index + 1]
        val zValue = z?.getOrNull(pointIndex)
        val mValue = m?.getOrNull(pointIndex)
        coordinates += coordinate(x, y, zValue, mValue)
        index += 2
    }
    return coordinates
}

private fun GeometryData.lineStrings(geometryFactory: GeometryFactory): Array<LineString> {
    val coordinates = coordinates()
    if (ends == null || ends.size < 2) {
        return arrayOf(geometryFactory.createLineString(coordinates.toTypedArray()))
    }
    val lineStrings = ArrayList<LineString>(ends.size)
    var start = 0
    ends.forEach { end ->
        lineStrings += geometryFactory.createLineString(coordinates.subList(start, end).toTypedArray())
        start = end
    }
    return lineStrings.toTypedArray()
}

private fun GeometryData.curveParts(geometryFactory: GeometryFactory): Array<LineString> =
    when {
        parts.isNotEmpty() -> parts.flatMap { part ->
            when (part.type) {
                GeometryType.LineString,
                GeometryType.CircularString,
                GeometryType.Curve,
                GeometryType.CompoundCurve,
                GeometryType.MultiLineString,
                GeometryType.MultiCurve,
                -> part.lineStrings(geometryFactory).asList()

                else -> listOf(geometryFactory.createLineString(part.coordinates().toTypedArray()))
            }
        }.toTypedArray()

        else -> lineStrings(geometryFactory)
    }

private fun GeometryData.surfaceParts(geometryFactory: GeometryFactory): Array<Polygon> =
    when {
        parts.isNotEmpty() -> parts.map { part ->
            when (part.type) {
                GeometryType.Polygon,
                GeometryType.CurvePolygon,
                GeometryType.Surface,
                GeometryType.Triangle,
                -> part.polygon(geometryFactory)

                else -> throw FlatGeobufException("Cannot map $type part ${part.type} to a JTS surface")
            }
        }.toTypedArray()

        else -> arrayOf(polygon(geometryFactory))
    }

private fun GeometryData.polygon(geometryFactory: GeometryFactory): Polygon {
    val coordinates = coordinates()
    if (coordinates.isEmpty()) return geometryFactory.createPolygon()
    if (ends == null || ends.isEmpty()) return geometryFactory.createPolygon(coordinates.toTypedArray())

    val rings = ArrayList<LinearRing>(ends.size)
    var start = 0
    ends.forEach { end ->
        rings += geometryFactory.createLinearRing(coordinates.subList(start, end).toTypedArray())
        start = end
    }
    return geometryFactory.createPolygon(rings.first(), rings.drop(1).toTypedArray())
}

private fun LineString.lineStringData(type: GeometryType): GeometryData {
    val coordinates = coordinates.toList()
    return GeometryData(
        type = type,
        xy = flatXy(coordinates),
        z = flatOrdinate(coordinates) { it?.z },
        m = flatOrdinate(coordinates) { it?.m },
    )
}

private fun MultiLineString.multiLineStringData(): GeometryData {
    val lineStrings = (0 until numGeometries).map { getGeometryN(it) as LineString }
    val coordinates = lineStrings.flatMap { it.coordinates.toList() }
    val ends = IntArray(lineStrings.size)
    var end = 0
    lineStrings.forEachIndexed { index, lineString ->
        end += lineString.numPoints
        ends[index] = end
    }
    return GeometryData(
        type = GeometryType.MultiLineString,
        xy = flatXy(coordinates),
        z = flatOrdinate(coordinates) { it?.z },
        m = flatOrdinate(coordinates) { it?.m },
        ends = if (ends.size > 1) ends else null,
    )
}

private fun Polygon.polygonData(): GeometryData {
    val rings = buildList {
        add(exteriorRing)
        repeat(numInteriorRing) { index -> add(getInteriorRingN(index)) }
    }
    val coordinates = rings.flatMap { it.coordinates.toList() }
    val ends = IntArray(rings.size)
    var end = 0
    rings.forEachIndexed { index, ring ->
        end += ring.numPoints
        ends[index] = end
    }
    return GeometryData(
        type = GeometryType.Polygon,
        xy = flatXy(coordinates),
        z = flatOrdinate(coordinates) { it?.z },
        m = flatOrdinate(coordinates) { it?.m },
        ends = if (ends.isNotEmpty()) ends else null,
    )
}

private fun flatXy(coordinates: List<Coordinate?>): DoubleArray {
    val values = ArrayList<Double>(coordinates.size * 2)
    coordinates.filterNotNull().forEach { coordinate ->
        values += coordinate.x
        values += coordinate.y
    }
    return values.toDoubleArray()
}

private fun flatOrdinate(
    coordinates: List<Coordinate?>,
    extractor: (Coordinate?) -> Double?,
): DoubleArray? {
    val values = coordinates.mapNotNull { coordinate ->
        extractor(coordinate)?.takeUnless(Double::isNaN)
    }
    return if (values.size == coordinates.filterNotNull().size && values.isNotEmpty()) {
        values.toDoubleArray()
    } else {
        null
    }
}

private fun coordinate(x: Double, y: Double, z: Double?, m: Double?): Coordinate =
    when {
        m != null -> CoordinateXYZM(x, y, z ?: Coordinate.NULL_ORDINATE, m)
        z != null -> Coordinate(x, y, z)
        else -> Coordinate(x, y)
    }
