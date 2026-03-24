package org.wololo.flatgeobuf.internal

internal fun ByteArray.readShortLe(offset: Int): Short {
    requireRange(offset, 2)
    val b0 = this[offset].toInt() and 0xff
    val b1 = this[offset + 1].toInt() and 0xff
    return ((b1 shl 8) or b0).toShort()
}

internal fun ByteArray.readUShortLe(offset: Int): Int = readShortLe(offset).toInt() and 0xffff

internal fun ByteArray.readIntLe(offset: Int): Int {
    requireRange(offset, 4)
    val b0 = this[offset].toInt() and 0xff
    val b1 = this[offset + 1].toInt() and 0xff
    val b2 = this[offset + 2].toInt() and 0xff
    val b3 = this[offset + 3].toInt() and 0xff
    return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
}

internal fun ByteArray.readLongLe(offset: Int): Long {
    requireRange(offset, 8)
    var result = 0L
    for (index in 7 downTo 0) {
        result = (result shl 8) or (this[offset + index].toLong() and 0xffL)
    }
    return result
}

internal fun ByteArray.readDoubleLe(offset: Int): Double = Double.fromBits(readLongLe(offset))

internal fun ByteArray.writeShortLe(offset: Int, value: Short) {
    requireRange(offset, 2)
    this[offset] = (value.toInt() and 0xff).toByte()
    this[offset + 1] = ((value.toInt() ushr 8) and 0xff).toByte()
}

internal fun ByteArray.writeIntLe(offset: Int, value: Int) {
    requireRange(offset, 4)
    this[offset] = (value and 0xff).toByte()
    this[offset + 1] = ((value ushr 8) and 0xff).toByte()
    this[offset + 2] = ((value ushr 16) and 0xff).toByte()
    this[offset + 3] = ((value ushr 24) and 0xff).toByte()
}

internal fun ByteArray.writeLongLe(offset: Int, value: Long) {
    requireRange(offset, 8)
    for (index in 0 until 8) {
        this[offset + index] = ((value ushr (index * 8)) and 0xffL).toByte()
    }
}

internal fun ByteArray.writeDoubleLe(offset: Int, value: Double) {
    writeLongLe(offset, value.toBits())
}

private fun ByteArray.requireRange(offset: Int, length: Int) {
    if (offset < 0 || offset + length > size) {
        throw IndexOutOfBoundsException("Range [$offset, ${offset + length}) is outside the byte array of size $size")
    }
}
