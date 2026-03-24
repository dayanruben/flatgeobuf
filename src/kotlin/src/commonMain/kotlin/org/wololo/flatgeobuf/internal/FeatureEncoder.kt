package org.wololo.flatgeobuf.internal

import org.wololo.flatgeobuf.ColumnMeta
import org.wololo.flatgeobuf.ColumnType
import org.wololo.flatgeobuf.CrsMeta
import org.wololo.flatgeobuf.Envelope
import org.wololo.flatgeobuf.FlatGeobufException
import org.wololo.flatgeobuf.GeometryData
import org.wololo.flatgeobuf.GeometryType
import org.wololo.flatgeobuf.HeaderMeta
import org.wololo.flatgeobuf.PropertyValue

internal object FeatureEncoder {
    fun encodeHeader(header: HeaderMeta): ByteArray {
        val builder = FlatBufferBuilder()
        val nameOffset = header.name?.let(builder::createString) ?: 0
        val titleOffset = header.title?.let(builder::createString) ?: 0
        val descriptionOffset = header.description?.let(builder::createString) ?: 0
        val metadataOffset = header.metadata?.let(builder::createString) ?: 0
        val envelopeOffset = header.envelope?.let(::toDoubleArray)?.let(builder::createDoubleVector) ?: 0
        val columnOffsets = header.columns.map { encodeColumn(builder, it) }.toIntArray()
        val columnsOffset = if (columnOffsets.isNotEmpty()) builder.createOffsetVector(columnOffsets) else 0
        val crsOffset = header.crs?.let { encodeCrs(builder, it) } ?: 0

        builder.startTable(14)
        builder.addOffset(13, metadataOffset)
        builder.addOffset(12, descriptionOffset)
        builder.addOffset(11, titleOffset)
        builder.addOffset(10, crsOffset)
        builder.addShort(9, header.indexNodeSize, 16)
        builder.addLong(8, header.featuresCount)
        builder.addOffset(7, columnsOffset)
        builder.addBoolean(6, header.hasTm)
        builder.addBoolean(5, header.hasT)
        builder.addBoolean(4, header.hasM)
        builder.addBoolean(3, header.hasZ)
        builder.addByte(2, header.geometryType.wireValue)
        builder.addOffset(1, envelopeOffset)
        builder.addOffset(0, nameOffset)
        val tableOffset = builder.endTable()
        return builder.finishSizePrefixed(tableOffset)
    }

    fun encodeFeature(
        geometry: GeometryData?,
        properties: Map<String, PropertyValue>,
        columns: List<ColumnMeta>,
        writeFeatureColumns: Boolean,
    ): ByteArray {
        val builder = FlatBufferBuilder()
        val geometryOffset = geometry?.let { encodeGeometry(builder, it) } ?: 0
        val propertiesOffset = encodeProperties(builder, properties, columns)
        val featureColumnsOffsets = if (writeFeatureColumns) {
            columns.map { encodeColumn(builder, it) }.toIntArray()
        } else {
            IntArray(0)
        }
        val featureColumnsOffset = if (featureColumnsOffsets.isNotEmpty()) {
            builder.createOffsetVector(featureColumnsOffsets)
        } else {
            0
        }

        builder.startTable(3)
        builder.addOffset(2, featureColumnsOffset)
        builder.addOffset(1, propertiesOffset)
        builder.addOffset(0, geometryOffset)
        val tableOffset = builder.endTable()
        return builder.finishSizePrefixed(tableOffset)
    }

    private fun encodeGeometry(builder: FlatBufferBuilder, geometry: GeometryData): Int {
        val partsOffsets = geometry.parts.map { encodeGeometry(builder, it) }.toIntArray()
        val partsOffset = if (partsOffsets.isNotEmpty()) builder.createOffsetVector(partsOffsets) else 0
        val endsOffset = geometry.ends?.takeIf { it.isNotEmpty() }?.let(builder::createIntVector) ?: 0
        val xyOffset = geometry.xy.takeIf { it.isNotEmpty() }?.let(builder::createDoubleVector) ?: 0
        val zOffset = geometry.z?.takeIf { it.isNotEmpty() }?.let(builder::createDoubleVector) ?: 0
        val mOffset = geometry.m?.takeIf { it.isNotEmpty() }?.let(builder::createDoubleVector) ?: 0
        val tOffset = geometry.t?.takeIf { it.isNotEmpty() }?.let(builder::createDoubleVector) ?: 0
        val tmOffset = geometry.tm?.takeIf { it.isNotEmpty() }?.let(builder::createLongVector) ?: 0

        builder.startTable(8)
        builder.addOffset(7, partsOffset)
        builder.addByte(6, geometry.type.wireValue)
        builder.addOffset(5, tmOffset)
        builder.addOffset(4, tOffset)
        builder.addOffset(3, mOffset)
        builder.addOffset(2, zOffset)
        builder.addOffset(1, xyOffset)
        builder.addOffset(0, endsOffset)
        return builder.endTable()
    }

    private fun encodeProperties(
        builder: FlatBufferBuilder,
        properties: Map<String, PropertyValue>,
        columns: List<ColumnMeta>,
    ): Int {
        if (columns.isEmpty() || properties.isEmpty()) return 0
        val bytes = ByteArrayOutput()
        columns.forEachIndexed { index, column ->
            val value = properties[column.name] ?: return@forEachIndexed
            bytes.writeShort(index.toShort())
            when (value) {
                is PropertyValue.BoolValue -> bytes.writeByte(if (value.value) 1 else 0)
                is PropertyValue.ByteValue -> bytes.writeByte(value.value.toInt())
                is PropertyValue.UByteValue -> bytes.writeByte(value.value.toInt())
                is PropertyValue.ShortValue -> bytes.writeShort(value.value)
                is PropertyValue.UShortValue -> bytes.writeShort(value.value.toShort())
                is PropertyValue.IntValue -> bytes.writeInt(value.value)
                is PropertyValue.UIntValue -> bytes.writeInt(value.value.toInt())
                is PropertyValue.LongValue -> bytes.writeLong(value.value)
                is PropertyValue.ULongValue -> bytes.writeLong(value.value.toLong())
                is PropertyValue.FloatValue -> bytes.writeInt(value.value.toRawBits())
                is PropertyValue.DoubleValue -> bytes.writeLong(value.value.toBits())
                is PropertyValue.StringValue -> bytes.writeSizedBytes(value.value.encodeToByteArray())
                is PropertyValue.JsonValue -> bytes.writeSizedBytes(value.value.encodeToByteArray())
                is PropertyValue.DateTimeValue -> bytes.writeSizedBytes(value.value.encodeToByteArray())
                is PropertyValue.BinaryValue -> bytes.writeSizedBytes(value.value)
            }
            validateColumnType(column, value)
        }
        return builder.createByteVector(bytes.toByteArray())
    }

    private fun validateColumnType(column: ColumnMeta, value: PropertyValue) {
        val type = column.type ?: return
        val matches = when (value) {
            is PropertyValue.BoolValue -> type == ColumnType.Bool
            is PropertyValue.ByteValue -> type == ColumnType.Byte
            is PropertyValue.UByteValue -> type == ColumnType.UByte
            is PropertyValue.ShortValue -> type == ColumnType.Short
            is PropertyValue.UShortValue -> type == ColumnType.UShort
            is PropertyValue.IntValue -> type == ColumnType.Int
            is PropertyValue.UIntValue -> type == ColumnType.UInt
            is PropertyValue.LongValue -> type == ColumnType.Long
            is PropertyValue.ULongValue -> type == ColumnType.ULong
            is PropertyValue.FloatValue -> type == ColumnType.Float
            is PropertyValue.DoubleValue -> type == ColumnType.Double
            is PropertyValue.StringValue -> type == ColumnType.String
            is PropertyValue.JsonValue -> type == ColumnType.Json
            is PropertyValue.DateTimeValue -> type == ColumnType.DateTime
            is PropertyValue.BinaryValue -> type == ColumnType.Binary
        }
        if (!matches) {
            throw FlatGeobufException("Property ${column.name} does not match declared column type $type")
        }
    }

    private fun encodeColumn(builder: FlatBufferBuilder, column: ColumnMeta): Int {
        val nameOffset = builder.createString(column.name)
        val titleOffset = column.title?.let(builder::createString) ?: 0
        val descriptionOffset = column.description?.let(builder::createString) ?: 0
        val metadataOffset = column.metadata?.let(builder::createString) ?: 0

        builder.startTable(11)
        builder.addOffset(10, metadataOffset)
        builder.addBoolean(9, column.primaryKey)
        builder.addBoolean(8, column.unique)
        builder.addBoolean(7, column.nullable, defaultValue = true)
        builder.addInt(6, column.scale, defaultValue = -1)
        builder.addInt(5, column.precision, defaultValue = -1)
        builder.addInt(4, column.width, defaultValue = -1)
        builder.addOffset(3, descriptionOffset)
        builder.addOffset(2, titleOffset)
        builder.addByte(1, column.type?.wireValue ?: 0)
        builder.addOffset(0, nameOffset)
        return builder.endTable()
    }

    private fun encodeCrs(builder: FlatBufferBuilder, crs: CrsMeta): Int {
        val orgOffset = crs.org?.let(builder::createString) ?: 0
        val nameOffset = crs.name?.let(builder::createString) ?: 0
        val descriptionOffset = crs.description?.let(builder::createString) ?: 0
        val wktOffset = crs.wkt?.let(builder::createString) ?: 0
        val codeStringOffset = crs.codeString?.let(builder::createString) ?: 0

        builder.startTable(6)
        builder.addOffset(5, codeStringOffset)
        builder.addOffset(4, wktOffset)
        builder.addOffset(3, descriptionOffset)
        builder.addOffset(2, nameOffset)
        builder.addInt(1, crs.code)
        builder.addOffset(0, orgOffset)
        return builder.endTable()
    }

    private fun toDoubleArray(envelope: Envelope): DoubleArray =
        doubleArrayOf(envelope.minX, envelope.minY, envelope.maxX, envelope.maxY)

    private class ByteArrayOutput(initialSize: Int = 128) {
        private var buffer = ByteArray(initialSize)
        private var size = 0

        fun writeByte(value: Int) {
            ensureCapacity(1)
            buffer[size++] = value.toByte()
        }

        fun writeShort(value: Short) {
            ensureCapacity(2)
            buffer.writeShortLe(size, value)
            size += 2
        }

        fun writeInt(value: Int) {
            ensureCapacity(4)
            buffer.writeIntLe(size, value)
            size += 4
        }

        fun writeLong(value: Long) {
            ensureCapacity(8)
            buffer.writeLongLe(size, value)
            size += 8
        }

        fun writeSizedBytes(value: ByteArray) {
            writeInt(value.size)
            ensureCapacity(value.size)
            value.copyInto(buffer, destinationOffset = size)
            size += value.size
        }

        fun toByteArray(): ByteArray = buffer.copyOf(size)

        private fun ensureCapacity(additional: Int) {
            val required = size + additional
            if (required <= buffer.size) return
            var newSize = buffer.size
            while (newSize < required) {
                newSize *= 2
            }
            buffer = buffer.copyOf(newSize)
        }
    }
}
