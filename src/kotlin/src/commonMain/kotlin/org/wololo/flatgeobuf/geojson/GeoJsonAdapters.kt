package org.wololo.flatgeobuf.geojson

import org.wololo.flatgeobuf.AsyncFgbReader
import org.wololo.flatgeobuf.ColumnMeta
import org.wololo.flatgeobuf.ColumnType
import org.wololo.flatgeobuf.CrsMeta
import org.wololo.flatgeobuf.Envelope
import org.wololo.flatgeobuf.FgbFeature
import org.wololo.flatgeobuf.FgbReader
import org.wololo.flatgeobuf.FlatGeobufException
import org.wololo.flatgeobuf.GeometryData
import org.wololo.flatgeobuf.GeometryType
import org.wololo.flatgeobuf.HeaderMeta
import org.wololo.flatgeobuf.PropertyValue
import kotlin.math.roundToLong

public data class GeoJsonPosition(
    val longitude: Double,
    val latitude: Double,
    val altitude: Double? = null,
)

public sealed interface GeoJsonGeometry {
    public data class Point(val coordinates: GeoJsonPosition) : GeoJsonGeometry
    public data class MultiPoint(val coordinates: List<GeoJsonPosition>) : GeoJsonGeometry
    public data class LineString(val coordinates: List<GeoJsonPosition>) : GeoJsonGeometry
    public data class MultiLineString(val coordinates: List<List<GeoJsonPosition>>) : GeoJsonGeometry
    public data class Polygon(val coordinates: List<List<GeoJsonPosition>>) : GeoJsonGeometry
    public data class MultiPolygon(val coordinates: List<List<List<GeoJsonPosition>>>) : GeoJsonGeometry
    public data class GeometryCollection(val geometries: List<GeoJsonGeometry>) : GeoJsonGeometry
}

public sealed interface GeoJsonValue {
    public data object NullValue : GeoJsonValue
    public data class BooleanValue(val value: Boolean) : GeoJsonValue
    public data class NumberValue(val value: Double) : GeoJsonValue
    public data class StringValue(val value: String) : GeoJsonValue
    public data class ArrayValue(val value: List<GeoJsonValue>) : GeoJsonValue
    public data class ObjectValue(val value: Map<String, GeoJsonValue>) : GeoJsonValue
}

public sealed interface GeoJsonId {
    public data class StringValue(val value: String) : GeoJsonId
    public data class LongValue(val value: Long) : GeoJsonId
    public data class NumberValue(val value: Double) : GeoJsonId
}

public data class GeoJsonFeature(
    val geometry: GeoJsonGeometry?,
    val properties: Map<String, GeoJsonValue> = emptyMap(),
    val id: GeoJsonId? = null,
)

public data class GeoJsonFeatureCollection(
    val features: List<GeoJsonFeature>,
)

public fun FgbFeature.toGeoJson(): GeoJsonFeature =
    GeoJsonFeature(
        geometry = geometry?.toGeoJson(),
        properties = properties.mapValues { (_, value) -> value.toGeoJsonValue() },
        id = GeoJsonId.LongValue(id.toLong()),
    )

public fun GeoJsonFeature.toFgbFeature(
    columns: List<ColumnMeta> = inferColumns(),
    defaultId: Int = 0,
    defaultOffset: Int = 0,
): FgbFeature {
    val encodedProperties = linkedMapOf<String, PropertyValue>()
    columns.forEach { column ->
        val value = properties[column.name] ?: return@forEach
        if (value == GeoJsonValue.NullValue) return@forEach
        encodedProperties[column.name] = value.toPropertyValue(column.type)
    }
    return FgbFeature(
        id = id.toFeatureId(defaultId),
        offset = defaultOffset,
        geometry = geometry?.toGeometryData(),
        properties = encodedProperties,
        columns = columns,
    )
}

public fun GeoJsonFeature.inferColumns(): List<ColumnMeta> =
    properties.mapNotNull { (name, value) ->
        val type = value.inferColumnType() ?: return@mapNotNull null
        ColumnMeta(
            name = name,
            type = type,
            title = null,
            description = null,
            width = -1,
            precision = -1,
            scale = -1,
            nullable = true,
            unique = false,
            primaryKey = false,
            metadata = null,
        )
    }

public fun GeoJsonFeatureCollection.inferColumns(): List<ColumnMeta> {
    val inferred = linkedMapOf<String, ColumnType?>()
    features.forEach { feature ->
        feature.properties.forEach { (name, value) ->
            val nextType = value.inferColumnType() ?: return@forEach
            inferred[name] = mergeColumnTypes(name, inferred[name], nextType)
        }
    }
    return inferred.map { (name, type) ->
        ColumnMeta(
            name = name,
            type = type,
            title = null,
            description = null,
            width = -1,
            precision = -1,
            scale = -1,
            nullable = true,
            unique = false,
            primaryKey = false,
            metadata = null,
        )
    }
}

public fun GeoJsonFeatureCollection.inferHeader(
    name: String? = null,
    crs: CrsMeta? = null,
    title: String? = null,
    description: String? = null,
    metadata: String? = null,
): HeaderMeta {
    val geometryData = features.mapNotNull { it.geometry?.toGeometryData() }
    val geometryType = geometryData.map { it.type }.distinct().singleOrNull() ?: GeometryType.Unknown
    val envelope = geometryData.map(::computeEnvelope)
        .takeIf { it.isNotEmpty() }
        ?.reduce(::mergeEnvelopes)
    return HeaderMeta(
        name = name,
        envelope = envelope,
        geometryType = geometryType,
        hasZ = geometryData.any(::hasZDimension),
        hasM = false,
        hasT = false,
        hasTm = false,
        columns = inferColumns(),
        featuresCount = features.size.toLong(),
        indexNodeSize = 0,
        crs = crs,
        title = title,
        description = description,
        metadata = metadata,
        headerSize = 0,
        headerOffset = 0,
        indexOffset = 0,
        indexSize = 0,
        featureSectionOffset = 0,
    )
}

public fun GeoJsonFeatureCollection.toFgbFeatures(
    columns: List<ColumnMeta> = inferColumns(),
): List<FgbFeature> = features.mapIndexed { index, feature ->
    feature.toFgbFeature(columns = columns, defaultId = index)
}

public fun FgbReader.selectAllGeoJson(): Sequence<GeoJsonFeature> = selectAll().map(FgbFeature::toGeoJson)

public fun FgbReader.selectBboxGeoJson(
    minX: Double,
    minY: Double,
    maxX: Double,
    maxY: Double,
): Sequence<GeoJsonFeature> = selectBbox(minX, minY, maxX, maxY).map(FgbFeature::toGeoJson)

public suspend fun AsyncFgbReader.selectAllGeoJson(): List<GeoJsonFeature> =
    selectAll().map(FgbFeature::toGeoJson)

public suspend fun AsyncFgbReader.selectBboxGeoJson(
    minX: Double,
    minY: Double,
    maxX: Double,
    maxY: Double,
): List<GeoJsonFeature> = selectBbox(minX, minY, maxX, maxY).map(FgbFeature::toGeoJson)

public fun GeometryData.toGeoJson(): GeoJsonGeometry =
    when (type) {
        GeometryType.Point -> GeoJsonGeometry.Point(singlePosition())
        GeometryType.MultiPoint -> GeoJsonGeometry.MultiPoint(pointPositions())
        GeometryType.LineString,
        GeometryType.CircularString,
        GeometryType.Curve,
        -> GeoJsonGeometry.LineString(pointPositions())

        GeometryType.MultiLineString,
        GeometryType.CompoundCurve,
        GeometryType.MultiCurve,
        -> GeoJsonGeometry.MultiLineString(lineCoordinates())

        GeometryType.Polygon,
        GeometryType.CurvePolygon,
        GeometryType.Surface,
        GeometryType.Triangle,
        -> GeoJsonGeometry.Polygon(polygonCoordinates())

        GeometryType.MultiPolygon,
        GeometryType.MultiSurface,
        GeometryType.PolyhedralSurface,
        GeometryType.Tin,
        -> GeoJsonGeometry.MultiPolygon(multiPolygonCoordinates())

        GeometryType.GeometryCollection,
        GeometryType.Unknown,
        -> GeoJsonGeometry.GeometryCollection(parts.map { it.toGeoJson() })
    }

public fun GeoJsonGeometry.toGeometryData(): GeometryData =
    when (this) {
        is GeoJsonGeometry.Point -> GeometryData(
            type = GeometryType.Point,
            xy = doubleArrayOf(coordinates.longitude, coordinates.latitude),
            z = coordinates.altitude?.let { doubleArrayOf(it) },
        )

        is GeoJsonGeometry.MultiPoint -> {
            val flattened = flattenPositions(coordinates)
            GeometryData(
                type = GeometryType.MultiPoint,
                xy = flattened.first,
                z = flattened.second,
            )
        }

        is GeoJsonGeometry.LineString -> {
            val flattened = flattenPositions(coordinates)
            GeometryData(
                type = GeometryType.LineString,
                xy = flattened.first,
                z = flattened.second,
            )
        }

        is GeoJsonGeometry.MultiLineString -> GeometryData(
            type = GeometryType.MultiLineString,
            xy = doubleArrayOf(),
            parts = coordinates.map { GeoJsonGeometry.LineString(it).toGeometryData() },
        )

        is GeoJsonGeometry.Polygon -> {
            val flattened = flattenRings(coordinates)
            GeometryData(
                type = GeometryType.Polygon,
                xy = flattened.first,
                z = flattened.second,
                ends = flattened.third,
            )
        }

        is GeoJsonGeometry.MultiPolygon -> GeometryData(
            type = GeometryType.MultiPolygon,
            xy = doubleArrayOf(),
            parts = coordinates.map { GeoJsonGeometry.Polygon(it).toGeometryData() },
        )

        is GeoJsonGeometry.GeometryCollection -> GeometryData(
            type = GeometryType.GeometryCollection,
            xy = doubleArrayOf(),
            parts = geometries.map(GeoJsonGeometry::toGeometryData),
        )
    }

private fun GeometryData.singlePosition(): GeoJsonPosition =
    pointPositions().firstOrNull() ?: throw FlatGeobufException("Point geometry has no coordinates")

private fun GeometryData.pointPositions(): List<GeoJsonPosition> {
    val positions = ArrayList<GeoJsonPosition>(xy.size / 2)
    var xyIndex = 0
    var coordIndex = 0
    while (xyIndex + 1 < xy.size) {
        positions += GeoJsonPosition(
            longitude = xy[xyIndex],
            latitude = xy[xyIndex + 1],
            altitude = z?.getOrNull(coordIndex),
        )
        xyIndex += 2
        coordIndex++
    }
    return positions
}

private fun GeometryData.lineCoordinates(): List<List<GeoJsonPosition>> =
    when {
        parts.isNotEmpty() -> parts.flatMap { part ->
            when (part.type) {
                GeometryType.LineString,
                GeometryType.CircularString,
                GeometryType.Curve,
                GeometryType.MultiLineString,
                GeometryType.CompoundCurve,
                GeometryType.MultiCurve,
                -> part.lineCoordinates()

                else -> listOf(part.pointPositions())
            }
        }

        ends != null && ends.isNotEmpty() -> splitByEnds(pointPositions(), ends)
        else -> listOf(pointPositions())
    }

private fun GeometryData.polygonCoordinates(): List<List<GeoJsonPosition>> =
    when {
        parts.isNotEmpty() -> parts.flatMap { part ->
            when (part.type) {
                GeometryType.Polygon,
                GeometryType.CurvePolygon,
                GeometryType.Surface,
                GeometryType.Triangle,
                -> part.polygonCoordinates()

                else -> listOf(part.pointPositions())
            }
        }

        else -> {
            val positions = pointPositions()
            if (ends != null && ends.isNotEmpty()) splitByEnds(positions, ends) else listOf(positions)
        }
    }

private fun GeometryData.multiPolygonCoordinates(): List<List<List<GeoJsonPosition>>> =
    when {
        parts.isNotEmpty() -> parts.map { part ->
            when (part.type) {
                GeometryType.Polygon,
                GeometryType.CurvePolygon,
                GeometryType.Surface,
                GeometryType.Triangle,
                -> part.polygonCoordinates()

                GeometryType.MultiPolygon,
                GeometryType.MultiSurface,
                GeometryType.PolyhedralSurface,
                GeometryType.Tin,
                -> part.multiPolygonCoordinates().flatten()

                else -> listOf(part.pointPositions())
            }
        }

        else -> listOf(polygonCoordinates())
    }

private fun splitByEnds(
    coordinates: List<GeoJsonPosition>,
    ends: IntArray,
): List<List<GeoJsonPosition>> {
    if (coordinates.isEmpty()) return emptyList()
    val rings = mutableListOf<List<GeoJsonPosition>>()
    var start = 0
    ends.forEach { end ->
        require(end in start..coordinates.size) { "Illegal geometry end $end for ${coordinates.size} coordinates" }
        rings += coordinates.subList(start, end)
        start = end
    }
    if (start < coordinates.size) {
        rings += coordinates.subList(start, coordinates.size)
    }
    return rings
}

private fun flattenPositions(positions: List<GeoJsonPosition>): Pair<DoubleArray, DoubleArray?> {
    val xy = DoubleArray(positions.size * 2)
    val zValues = if (positions.any { it.altitude != null }) DoubleArray(positions.size) else null
    positions.forEachIndexed { index, position ->
        xy[index * 2] = position.longitude
        xy[(index * 2) + 1] = position.latitude
        if (zValues != null) {
            zValues[index] = position.altitude ?: Double.NaN
        }
    }
    return xy to zValues
}

private fun flattenRings(
    rings: List<List<GeoJsonPosition>>,
): Triple<DoubleArray, DoubleArray?, IntArray?> {
    val allPoints = rings.flatten()
    val (xy, z) = flattenPositions(allPoints)
    if (rings.size <= 1) return Triple(xy, z, null)
    val ends = IntArray(rings.size)
    var cursor = 0
    rings.forEachIndexed { index, ring ->
        cursor += ring.size
        ends[index] = cursor
    }
    return Triple(xy, z, ends)
}

private fun GeoJsonId?.toFeatureId(defaultId: Int): Int =
    when (this) {
        null -> defaultId
        is GeoJsonId.LongValue -> value.toInt()
        is GeoJsonId.NumberValue -> value.roundToLong().toInt()
        is GeoJsonId.StringValue -> defaultId
    }

private fun PropertyValue.toGeoJsonValue(): GeoJsonValue =
    when (this) {
        is PropertyValue.BoolValue -> GeoJsonValue.BooleanValue(value)
        is PropertyValue.ByteValue -> GeoJsonValue.NumberValue(value.toDouble())
        is PropertyValue.UByteValue -> GeoJsonValue.NumberValue(value.toDouble())
        is PropertyValue.ShortValue -> GeoJsonValue.NumberValue(value.toDouble())
        is PropertyValue.UShortValue -> GeoJsonValue.NumberValue(value.toDouble())
        is PropertyValue.IntValue -> GeoJsonValue.NumberValue(value.toDouble())
        is PropertyValue.UIntValue -> GeoJsonValue.NumberValue(value.toDouble())
        is PropertyValue.LongValue -> GeoJsonValue.NumberValue(value.toDouble())
        is PropertyValue.ULongValue -> GeoJsonValue.NumberValue(value.toDouble())
        is PropertyValue.FloatValue -> GeoJsonValue.NumberValue(value.toDouble())
        is PropertyValue.DoubleValue -> GeoJsonValue.NumberValue(value)
        is PropertyValue.StringValue -> GeoJsonValue.StringValue(value)
        is PropertyValue.JsonValue -> GeoJsonValue.StringValue(value)
        is PropertyValue.DateTimeValue -> GeoJsonValue.StringValue(value)
        is PropertyValue.BinaryValue -> GeoJsonValue.ArrayValue(value.map { GeoJsonValue.NumberValue(it.toUByte().toDouble()) })
    }

private fun GeoJsonValue.inferColumnType(): ColumnType? =
    when (this) {
        GeoJsonValue.NullValue -> null
        is GeoJsonValue.BooleanValue -> ColumnType.Bool
        is GeoJsonValue.NumberValue -> if (value.isWholeNumber() && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
            ColumnType.Int
        } else {
            ColumnType.Double
        }

        is GeoJsonValue.StringValue -> ColumnType.String
        is GeoJsonValue.ArrayValue -> if (value.all { it is GeoJsonValue.NumberValue && it.value in 0.0..255.0 && it.value.isWholeNumber() }) {
            ColumnType.Binary
        } else {
            ColumnType.Json
        }

        is GeoJsonValue.ObjectValue -> ColumnType.Json
    }

private fun GeoJsonValue.toPropertyValue(explicitType: ColumnType?): PropertyValue {
    val columnType = explicitType ?: inferColumnType()
        ?: throw FlatGeobufException("Cannot infer column type from null GeoJSON property")
    return when (columnType) {
        ColumnType.Bool -> PropertyValue.BoolValue(requireType<GeoJsonValue.BooleanValue>().value)
        ColumnType.Byte -> PropertyValue.ByteValue(requireInteger("Byte", Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong()).toByte())
        ColumnType.UByte -> PropertyValue.UByteValue(requireUnsignedInteger("UByte", UByte.MAX_VALUE.toLong()).toUByte())
        ColumnType.Short -> PropertyValue.ShortValue(requireInteger("Short", Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toShort())
        ColumnType.UShort -> PropertyValue.UShortValue(requireUnsignedInteger("UShort", UShort.MAX_VALUE.toLong()).toUShort())
        ColumnType.Int -> PropertyValue.IntValue(requireInteger("Int", Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt())
        ColumnType.UInt -> PropertyValue.UIntValue(requireUnsignedInteger("UInt", UInt.MAX_VALUE.toLong()).toUInt())
        ColumnType.Long -> PropertyValue.LongValue(requireInteger("Long", Long.MIN_VALUE, Long.MAX_VALUE))
        ColumnType.ULong -> PropertyValue.ULongValue(requireUnsignedInteger("ULong", Long.MAX_VALUE).toULong())
        ColumnType.Float -> PropertyValue.FloatValue(requireNumeric("Float").toFloat())
        ColumnType.Double -> PropertyValue.DoubleValue(requireNumeric("Double"))
        ColumnType.String -> PropertyValue.StringValue(requireString("String"))
        ColumnType.Json -> PropertyValue.JsonValue(toJsonString())
        ColumnType.DateTime -> PropertyValue.DateTimeValue(requireString("DateTime"))
        ColumnType.Binary -> PropertyValue.BinaryValue(toBinary())
    }
}

private inline fun <reified T : GeoJsonValue> GeoJsonValue.requireType(typeName: String = T::class.simpleName ?: "value"): T =
    this as? T ?: throw FlatGeobufException("Expected GeoJSON $typeName property, got ${this::class.simpleName}")

private fun GeoJsonValue.requireNumeric(typeName: String): Double =
    requireType<GeoJsonValue.NumberValue>(typeName).value

private fun GeoJsonValue.requireString(typeName: String): String =
    requireType<GeoJsonValue.StringValue>(typeName).value

private fun GeoJsonValue.requireInteger(typeName: String, min: Long, max: Long): Long {
    val number = requireNumeric(typeName)
    if (!number.isWholeNumber()) {
        throw FlatGeobufException("Expected integral GeoJSON $typeName property, got $number")
    }
    val longValue = number.roundToLong()
    if (longValue !in min..max) {
        throw FlatGeobufException("GeoJSON $typeName property $longValue outside [$min, $max]")
    }
    return longValue
}

private fun GeoJsonValue.requireUnsignedInteger(typeName: String, max: Long): Long =
    requireInteger(typeName, 0, max)

private fun GeoJsonValue.toBinary(): ByteArray =
    when (this) {
        is GeoJsonValue.ArrayValue -> ByteArray(value.size) { index ->
            val byteValue = value[index] as? GeoJsonValue.NumberValue
                ?: throw FlatGeobufException("Binary GeoJSON property must be an array of numbers")
            if (!byteValue.value.isWholeNumber() || byteValue.value !in 0.0..255.0) {
                throw FlatGeobufException("Binary GeoJSON property byte ${byteValue.value} outside [0, 255]")
            }
            byteValue.value.roundToLong().toByte()
        }

        else -> throw FlatGeobufException("Binary GeoJSON property must be an array of numbers")
    }

private fun GeoJsonValue.toJsonString(): String =
    when (this) {
        GeoJsonValue.NullValue -> "null"
        is GeoJsonValue.BooleanValue -> value.toString()
        is GeoJsonValue.NumberValue -> if (value.isWholeNumber()) value.roundToLong().toString() else value.toString()
        is GeoJsonValue.StringValue -> "\"" + value.escapeJson() + "\""
        is GeoJsonValue.ArrayValue -> value.joinToString(prefix = "[", postfix = "]") { it.toJsonString() }
        is GeoJsonValue.ObjectValue -> value.entries.joinToString(prefix = "{", postfix = "}") { (key, element) ->
            "\"" + key.escapeJson() + "\":" + element.toJsonString()
        }
    }

private fun String.escapeJson(): String = buildString(length) {
    forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (ch.code < 0x20) {
                    append("\\u")
                    append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
    }
}

private fun Double.isWholeNumber(): Boolean = isFinite() && this == roundToLong().toDouble()

private fun mergeColumnTypes(name: String, left: ColumnType?, right: ColumnType): ColumnType =
    when {
        left == null -> right
        left == right -> left
        left in numericColumnTypes && right in numericColumnTypes -> {
            if (left == ColumnType.Double || right == ColumnType.Double || left == ColumnType.Float || right == ColumnType.Float) {
                ColumnType.Double
            } else {
                ColumnType.Long
            }
        }

        left == ColumnType.Json || right == ColumnType.Json -> ColumnType.Json
        left == ColumnType.Binary && right == ColumnType.Binary -> ColumnType.Binary
        else -> throw FlatGeobufException("Incompatible inferred GeoJSON property types for $name: $left vs $right")
    }

private val numericColumnTypes: Set<ColumnType> = setOf(
    ColumnType.Byte,
    ColumnType.UByte,
    ColumnType.Short,
    ColumnType.UShort,
    ColumnType.Int,
    ColumnType.UInt,
    ColumnType.Long,
    ColumnType.ULong,
    ColumnType.Float,
    ColumnType.Double,
)

private fun computeEnvelope(geometry: GeometryData): Envelope {
    val bounds = BoundsAccumulator()
    appendBounds(bounds, geometry)
    return bounds.toEnvelope()
}

private fun appendBounds(bounds: BoundsAccumulator, geometry: GeometryData) {
    var index = 0
    while (index + 1 < geometry.xy.size) {
        bounds.include(geometry.xy[index], geometry.xy[index + 1])
        index += 2
    }
    geometry.parts.forEach { appendBounds(bounds, it) }
}

private fun mergeEnvelopes(left: Envelope, right: Envelope): Envelope =
    Envelope(
        minX = minOf(left.minX, right.minX),
        minY = minOf(left.minY, right.minY),
        maxX = maxOf(left.maxX, right.maxX),
        maxY = maxOf(left.maxY, right.maxY),
    )

private fun hasZDimension(geometry: GeometryData): Boolean =
    (geometry.z?.isNotEmpty() == true) || geometry.parts.any(::hasZDimension)

private class BoundsAccumulator {
    private var minX = Double.POSITIVE_INFINITY
    private var minY = Double.POSITIVE_INFINITY
    private var maxX = Double.NEGATIVE_INFINITY
    private var maxY = Double.NEGATIVE_INFINITY

    fun include(x: Double, y: Double) {
        minX = minOf(minX, x)
        minY = minOf(minY, y)
        maxX = maxOf(maxX, x)
        maxY = maxOf(maxY, y)
    }

    fun toEnvelope(): Envelope =
        if (minX.isInfinite()) {
            Envelope(0.0, 0.0, 0.0, 0.0)
        } else {
            Envelope(minX, minY, maxX, maxY)
        }
}
