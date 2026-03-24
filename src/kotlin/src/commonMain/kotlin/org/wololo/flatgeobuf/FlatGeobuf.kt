package org.wololo.flatgeobuf

import org.wololo.flatgeobuf.index.PackedRTree
import org.wololo.flatgeobuf.index.SearchResultItem
import org.wololo.flatgeobuf.internal.HeaderReader

public object FlatGeobuf {
    public const val VERSION: Int = 3
    public val MAGIC_BYTES: ByteArray = byteArrayOf(0x66, 0x67, 0x62, 0x03, 0x66, 0x67, 0x62, 0x00)

    public fun isFlatGeobuf(bytes: ByteArray): Boolean {
        if (bytes.size < MAGIC_BYTES.size) return false
        return bytes[0] == MAGIC_BYTES[0] &&
            bytes[1] == MAGIC_BYTES[1] &&
            bytes[2] == MAGIC_BYTES[2] &&
            bytes[4] == MAGIC_BYTES[4] &&
            bytes[5] == MAGIC_BYTES[5] &&
            bytes[6] == MAGIC_BYTES[6] &&
            bytes[3].toInt().and(0xff) <= VERSION
    }

    public fun readHeader(bytes: ByteArray): HeaderMeta = HeaderReader.read(bytes)

    public suspend fun readHeader(source: AsyncByteRangeSource): HeaderMeta = AsyncFgbReader.readHeader(source)

    public fun open(bytes: ByteArray): FgbReader = FgbReader.open(bytes)

    public suspend fun open(source: AsyncByteRangeSource): AsyncFgbReader = AsyncFgbReader.open(source)

    public fun searchIndex(
        bytes: ByteArray,
        minX: Double,
        minY: Double,
        maxX: Double,
        maxY: Double,
    ): List<SearchResultItem> {
        val header = readHeader(bytes)
        if (!header.hasIndex) {
            throw FlatGeobufException("FlatGeobuf does not contain a searchable index")
        }
        if (header.featuresCount > Int.MAX_VALUE) {
            throw FlatGeobufException("Feature counts above ${Int.MAX_VALUE} are not yet supported")
        }
        val tree = PackedRTree.fromData(
            bytes = bytes,
            dataOffset = header.indexOffset,
            numItems = header.featuresCount.toInt(),
            nodeSize = header.indexNodeSize,
        )
        return tree.search(minX, minY, maxX, maxY)
    }
}
