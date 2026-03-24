package org.wololo.flatgeobuf.internal

internal class FlatBufferTable(
    private val bytes: ByteArray,
    private val tableStart: Int,
) {
    private val vtableStart: Int = tableStart - bytes.readIntLe(tableStart)
    private val vtableLength: Int = bytes.readUShortLe(vtableStart)

    fun string(fieldOffset: Int): String? = field(fieldOffset)?.let(bytes::readString)

    fun int(fieldOffset: Int, defaultValue: Int = 0): Int =
        field(fieldOffset)?.let(bytes::readIntLe) ?: defaultValue

    fun long(fieldOffset: Int, defaultValue: Long = 0): Long =
        field(fieldOffset)?.let(bytes::readLongLe) ?: defaultValue

    fun ushort(fieldOffset: Int, defaultValue: Int = 0): Int =
        field(fieldOffset)?.let(bytes::readUShortLe) ?: defaultValue

    fun ubyte(fieldOffset: Int, defaultValue: Int = 0): Int =
        field(fieldOffset)?.let { bytes[it].toInt() and 0xff } ?: defaultValue

    fun bool(fieldOffset: Int, defaultValue: Boolean = false): Boolean =
        field(fieldOffset)?.let { bytes[it].toInt() != 0 } ?: defaultValue

    fun doubleVector(fieldOffset: Int): DoubleArray? {
        val field = field(fieldOffset) ?: return null
        val vectorStart = bytes.vectorStart(field)
        val length = bytes.readIntLe(vectorStart)
        val vectorDataStart = vectorStart + VECTOR_LENGTH_PREFIX_BYTES
        return DoubleArray(length) { index -> bytes.readDoubleLe(vectorDataStart + (index * 8)) }
    }

    fun intVector(fieldOffset: Int): IntArray? {
        val field = field(fieldOffset) ?: return null
        val vectorStart = bytes.vectorStart(field)
        val length = bytes.readIntLe(vectorStart)
        val vectorDataStart = vectorStart + VECTOR_LENGTH_PREFIX_BYTES
        return IntArray(length) { index -> bytes.readIntLe(vectorDataStart + (index * 4)) }
    }

    fun longVector(fieldOffset: Int): LongArray? {
        val field = field(fieldOffset) ?: return null
        val vectorStart = bytes.vectorStart(field)
        val length = bytes.readIntLe(vectorStart)
        val vectorDataStart = vectorStart + VECTOR_LENGTH_PREFIX_BYTES
        return LongArray(length) { index -> bytes.readLongLe(vectorDataStart + (index * 8)) }
    }

    fun byteVector(fieldOffset: Int): ByteArray? {
        val field = field(fieldOffset) ?: return null
        val vectorStart = bytes.vectorStart(field)
        val length = bytes.readIntLe(vectorStart)
        val vectorDataStart = vectorStart + VECTOR_LENGTH_PREFIX_BYTES
        return bytes.copyOfRange(vectorDataStart, vectorDataStart + length)
    }

    fun table(fieldOffset: Int): FlatBufferTable? =
        field(fieldOffset)?.let { field -> FlatBufferTable(bytes, field + bytes.readIntLe(field)) }

    fun tableVectorLength(fieldOffset: Int): Int =
        field(fieldOffset)?.let { bytes.readIntLe(bytes.vectorStart(it)) } ?: 0

    fun tableVector(fieldOffset: Int, index: Int): FlatBufferTable {
        val field = field(fieldOffset) ?: error("Missing table vector for field offset $fieldOffset")
        val vectorStart = bytes.vectorStart(field)
        val vectorLength = bytes.readIntLe(vectorStart)
        require(index in 0 until vectorLength) { "Vector index $index out of bounds for size $vectorLength" }
        val itemOffset = vectorStart + VECTOR_LENGTH_PREFIX_BYTES + (index * TABLE_POINTER_BYTES)
        return FlatBufferTable(bytes, itemOffset + bytes.readIntLe(itemOffset))
    }

    private fun field(fieldOffset: Int): Int? {
        if (fieldOffset >= vtableLength) return null
        val relativeOffset = bytes.readUShortLe(vtableStart + fieldOffset)
        return if (relativeOffset == 0) null else tableStart + relativeOffset
    }

    private companion object {
        private const val TABLE_POINTER_BYTES: Int = 4
        private const val VECTOR_LENGTH_PREFIX_BYTES: Int = 4
    }
}

internal fun ByteArray.vectorStart(fieldOffset: Int): Int = fieldOffset + readIntLe(fieldOffset)

private fun ByteArray.readString(fieldOffset: Int): String {
    val stringStart = fieldOffset + readIntLe(fieldOffset)
    val stringLength = readIntLe(stringStart)
    val bytesStart = stringStart + 4
    return decodeToString(startIndex = bytesStart, endIndex = bytesStart + stringLength)
}
