package org.wololo.flatgeobuf.internal

import org.wololo.flatgeobuf.ColumnMeta
import org.wololo.flatgeobuf.ColumnType
import org.wololo.flatgeobuf.FgbFeature
import org.wololo.flatgeobuf.FlatGeobufException
import org.wololo.flatgeobuf.GeometryData
import org.wololo.flatgeobuf.GeometryType
import org.wololo.flatgeobuf.HeaderMeta
import org.wololo.flatgeobuf.PropertyValue

internal object FeatureDecoder {
    fun readFeature(
        bytes: ByteArray,
        featureOffset: Int,
        id: Int,
        header: HeaderMeta,
    ): FgbFeature {
        if (featureOffset + 8 > bytes.size) {
            throw FlatGeobufException("Feature at offset $featureOffset is truncated")
        }
        val featureSize = bytes.readIntLe(featureOffset)
        val totalSize = 4 + featureSize
        if (featureSize < 0 || featureOffset + totalSize > bytes.size) {
            throw FlatGeobufException("Feature at offset $featureOffset exceeds available bytes")
        }

        val table = FlatBufferTable(
            bytes = bytes,
            tableStart = featureOffset + 4 + bytes.readIntLe(featureOffset + 4),
        )

        val columns = if (header.columns.isNotEmpty()) {
            header.columns
        } else {
            SchemaReaders.readColumns(table, 8)
        }

        return FgbFeature(
            id = id,
            offset = featureOffset,
            geometry = table.table(4)?.let { readGeometry(it, header.geometryType) },
            properties = table.byteVector(6)?.let { readProperties(it, columns) } ?: emptyMap(),
            columns = columns,
        )
    }

    private fun readGeometry(
        table: FlatBufferTable,
        declaredType: GeometryType,
    ): GeometryData {
        val actualType = if (declaredType == GeometryType.Unknown) {
            GeometryType.fromWireValue(table.ubyte(16))
        } else {
            declaredType
        }
        return GeometryData(
            type = actualType,
            xy = table.doubleVector(6) ?: DoubleArray(0),
            z = table.doubleVector(8),
            m = table.doubleVector(10),
            t = table.doubleVector(12),
            tm = table.longVector(14),
            ends = table.intVector(4),
            parts = buildList {
                repeat(table.tableVectorLength(18)) { index ->
                    val part = table.tableVector(18, index)
                    add(readGeometry(part, GeometryType.fromWireValue(part.ubyte(16))))
                }
            },
        )
    }

    private fun readProperties(
        bytes: ByteArray,
        columns: List<ColumnMeta>,
    ): Map<String, PropertyValue> {
        val properties = linkedMapOf<String, PropertyValue>()
        var offset = 0
        while (offset < bytes.size) {
            if (offset + 2 > bytes.size) {
                throw FlatGeobufException("Property column index exceeds available bytes")
            }
            val columnIndex = bytes.readUShortLe(offset)
            offset += 2
            val column = columns.getOrNull(columnIndex)
                ?: throw FlatGeobufException("Property column index $columnIndex exceeds declared columns")
            val name = column.name
            val type = column.type ?: throw FlatGeobufException("Column $name has an unknown type")
            val (value, nextOffset) = readPropertyValue(bytes, offset, type, name)
            properties[name] = value
            offset = nextOffset
        }
        return properties
    }

    private fun readPropertyValue(
        bytes: ByteArray,
        offset: Int,
        type: ColumnType,
        name: String,
    ): Pair<PropertyValue, Int> {
        fun requireAvailable(size: Int) {
            if (offset + size > bytes.size) {
                throw FlatGeobufException("Property $name exceeds available bytes")
            }
        }

        return when (type) {
            ColumnType.Bool -> {
                requireAvailable(1)
                PropertyValue.BoolValue(bytes[offset].toInt() != 0) to (offset + 1)
            }
            ColumnType.Byte -> {
                requireAvailable(1)
                PropertyValue.ByteValue(bytes[offset]) to (offset + 1)
            }
            ColumnType.UByte -> {
                requireAvailable(1)
                PropertyValue.UByteValue(bytes[offset].toUByte()) to (offset + 1)
            }
            ColumnType.Short -> {
                requireAvailable(2)
                PropertyValue.ShortValue(bytes.readShortLe(offset)) to (offset + 2)
            }
            ColumnType.UShort -> {
                requireAvailable(2)
                PropertyValue.UShortValue(bytes.readUShortLe(offset).toUShort()) to (offset + 2)
            }
            ColumnType.Int -> {
                requireAvailable(4)
                PropertyValue.IntValue(bytes.readIntLe(offset)) to (offset + 4)
            }
            ColumnType.UInt -> {
                requireAvailable(4)
                PropertyValue.UIntValue(bytes.readIntLe(offset).toUInt()) to (offset + 4)
            }
            ColumnType.Long -> {
                requireAvailable(8)
                PropertyValue.LongValue(bytes.readLongLe(offset)) to (offset + 8)
            }
            ColumnType.ULong -> {
                requireAvailable(8)
                PropertyValue.ULongValue(bytes.readLongLe(offset).toULong()) to (offset + 8)
            }
            ColumnType.Float -> {
                requireAvailable(4)
                PropertyValue.FloatValue(Float.fromBits(bytes.readIntLe(offset))) to (offset + 4)
            }
            ColumnType.Double -> {
                requireAvailable(8)
                PropertyValue.DoubleValue(bytes.readDoubleLe(offset)) to (offset + 8)
            }
            ColumnType.String -> readStringLike(bytes, offset, name) { PropertyValue.StringValue(it) }
            ColumnType.Json -> readStringLike(bytes, offset, name) { PropertyValue.JsonValue(it) }
            ColumnType.DateTime -> readStringLike(bytes, offset, name) { PropertyValue.DateTimeValue(it) }
            ColumnType.Binary -> {
                val (value, nextOffset) = readSizedBytes(bytes, offset, name)
                PropertyValue.BinaryValue(value) to nextOffset
            }
        }
    }

    private fun readStringLike(
        bytes: ByteArray,
        offset: Int,
        name: String,
        valueFactory: (String) -> PropertyValue,
    ): Pair<PropertyValue, Int> {
        val (value, nextOffset) = readSizedBytes(bytes, offset, name)
        return valueFactory(value.decodeToString()) to nextOffset
    }

    private fun readSizedBytes(bytes: ByteArray, offset: Int, name: String): Pair<ByteArray, Int> {
        if (offset + 4 > bytes.size) {
            throw FlatGeobufException("Property $name length exceeds available bytes")
        }
        val length = bytes.readIntLe(offset)
        val start = offset + 4
        val end = start + length
        if (length < 0 || end > bytes.size) {
            throw FlatGeobufException("Property $name data exceeds available bytes")
        }
        return bytes.copyOfRange(start, end) to end
    }
}
