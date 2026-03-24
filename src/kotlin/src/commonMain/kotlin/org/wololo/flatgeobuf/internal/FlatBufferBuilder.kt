package org.wololo.flatgeobuf.internal

internal class FlatBufferBuilder(initialSize: Int = 1024) {
    private var buffer = ByteArray(initialSize)
    private var head = initialSize
    private var minAlign = 1
    private var vtable = IntArray(0)
    private var objectStart = 0

    fun createString(value: String): Int {
        val bytes = value.encodeToByteArray()
        prep(4, bytes.size + 1)
        pad(1)
        for (index in bytes.indices.reversed()) {
            putByte(bytes[index].toInt())
        }
        putInt(bytes.size)
        return offset()
    }

    fun createByteVector(value: ByteArray): Int {
        startVector(1, value.size, 1)
        for (index in value.indices.reversed()) {
            putByte(value[index].toInt())
        }
        return endVector(value.size)
    }

    fun createIntVector(value: IntArray): Int {
        startVector(4, value.size, 4)
        for (index in value.indices.reversed()) {
            putInt(value[index])
        }
        return endVector(value.size)
    }

    fun createLongVector(value: LongArray): Int {
        startVector(8, value.size, 8)
        for (index in value.indices.reversed()) {
            putLong(value[index])
        }
        return endVector(value.size)
    }

    fun createDoubleVector(value: DoubleArray): Int {
        startVector(8, value.size, 8)
        for (index in value.indices.reversed()) {
            putDouble(value[index])
        }
        return endVector(value.size)
    }

    fun createOffsetVector(value: IntArray): Int {
        startVector(4, value.size, 4)
        for (index in value.indices.reversed()) {
            putOffset(value[index])
        }
        return endVector(value.size)
    }

    fun startTable(numFields: Int) {
        vtable = IntArray(numFields)
        objectStart = offset()
    }

    fun addByte(slot: Int, value: Int, defaultValue: Int = 0) {
        if (value == defaultValue) return
        prep(1, 0)
        putByte(value)
        slot(slot)
    }

    fun addBoolean(slot: Int, value: Boolean, defaultValue: Boolean = false) {
        if (value == defaultValue) return
        prep(1, 0)
        putByte(if (value) 1 else 0)
        slot(slot)
    }

    fun addShort(slot: Int, value: Int, defaultValue: Int = 0) {
        if (value == defaultValue) return
        prep(2, 0)
        putShort(value.toShort())
        slot(slot)
    }

    fun addInt(slot: Int, value: Int, defaultValue: Int = 0) {
        if (value == defaultValue) return
        prep(4, 0)
        putInt(value)
        slot(slot)
    }

    fun addLong(slot: Int, value: Long, defaultValue: Long = 0) {
        if (value == defaultValue) return
        prep(8, 0)
        putLong(value)
        slot(slot)
    }

    fun addOffset(slot: Int, value: Int) {
        if (value == 0) return
        prep(4, 0)
        putOffset(value)
        slot(slot)
    }

    fun endTable(): Int {
        prep(4, 0)
        putInt(0)
        val tableOffset = offset()

        var trimmedSize = vtable.size
        while (trimmedSize > 0 && vtable[trimmedSize - 1] == 0) {
            trimmedSize--
        }

        for (index in trimmedSize - 1 downTo 0) {
            val fieldOffset = if (vtable[index] == 0) 0 else tableOffset - vtable[index]
            prep(2, 0)
            putShort(fieldOffset.toShort())
        }

        val objectSize = tableOffset - objectStart
        prep(2, 0)
        putShort(objectSize.toShort())
        prep(2, 0)
        putShort(((trimmedSize + 2) * 2).toShort())

        val vtableOffset = offset()
        writeIntAtOffset(tableOffset, vtableOffset - tableOffset)
        return tableOffset
    }

    fun finishSizePrefixed(rootTable: Int): ByteArray {
        prep(minAlign, 8)
        putOffset(rootTable)
        putInt(offset())
        return sizedByteArray()
    }

    private fun startVector(elementSize: Int, numElements: Int, alignment: Int) {
        prep(4, elementSize * numElements)
        prep(alignment, elementSize * numElements)
    }

    private fun endVector(numElements: Int): Int {
        putInt(numElements)
        return offset()
    }

    private fun slot(slot: Int) {
        vtable[slot] = offset()
    }

    private fun putOffset(value: Int) {
        putInt(offset() - value + 4)
    }

    private fun prep(size: Int, additionalBytes: Int) {
        if (size > minAlign) minAlign = size
        val alignSize = ((-(offset() + additionalBytes)) and (size - 1))
        while (head < alignSize + size + additionalBytes) {
            growBuffer()
        }
        pad(alignSize)
    }

    private fun pad(byteCount: Int) {
        repeat(byteCount) {
            buffer[--head] = 0
        }
    }

    private fun growBuffer() {
        val oldBuffer = buffer
        buffer = ByteArray(oldBuffer.size * 2)
        val newHead = buffer.size - oldBuffer.size
        oldBuffer.copyInto(buffer, destinationOffset = newHead)
        head += newHead
    }

    private fun putByte(value: Int) {
        buffer[--head] = value.toByte()
    }

    private fun putShort(value: Short) {
        head -= 2
        buffer.writeShortLe(head, value)
    }

    private fun putInt(value: Int) {
        head -= 4
        buffer.writeIntLe(head, value)
    }

    private fun putLong(value: Long) {
        head -= 8
        buffer.writeLongLe(head, value)
    }

    private fun putDouble(value: Double) {
        putLong(value.toBits())
    }

    private fun offset(): Int = buffer.size - head

    private fun sizedByteArray(): ByteArray = buffer.copyOfRange(head, buffer.size)

    private fun writeIntAtOffset(offset: Int, value: Int) {
        val absoluteIndex = buffer.size - offset
        buffer.writeIntLe(absoluteIndex, value)
    }
}
