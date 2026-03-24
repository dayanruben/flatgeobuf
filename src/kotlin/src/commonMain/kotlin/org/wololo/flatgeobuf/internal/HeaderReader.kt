package org.wololo.flatgeobuf.internal

import org.wololo.flatgeobuf.Envelope
import org.wololo.flatgeobuf.FlatGeobuf
import org.wololo.flatgeobuf.FlatGeobufException
import org.wololo.flatgeobuf.GeometryType
import org.wololo.flatgeobuf.HeaderMeta
import org.wololo.flatgeobuf.index.PackedRTree

internal object HeaderReader {
    private const val MAGIC_LENGTH = 8
    private const val SIZE_PREFIX_LENGTH = 4
    private const val ROOT_OFFSET_LOCATION = MAGIC_LENGTH + SIZE_PREFIX_LENGTH
    private const val HEADER_MAX_BUFFER_SIZE = 10 * 1024 * 1024

    fun read(bytes: ByteArray): HeaderMeta {
        if (!FlatGeobuf.isFlatGeobuf(bytes)) {
            throw FlatGeobufException("Invalid FlatGeobuf magic bytes")
        }
        if (bytes.size < ROOT_OFFSET_LOCATION + 4) {
            throw FlatGeobufException("FlatGeobuf data is truncated before the header")
        }

        val headerSize = bytes.readIntLe(MAGIC_LENGTH)
        if (headerSize !in 8..HEADER_MAX_BUFFER_SIZE) {
            throw FlatGeobufException("Illegal FlatGeobuf header size: $headerSize")
        }

        val headerEndOffset = ROOT_OFFSET_LOCATION + headerSize
        if (headerEndOffset > bytes.size) {
            throw FlatGeobufException("FlatGeobuf header exceeds the available bytes")
        }

        val headerTable = FlatBufferTable(
            bytes = bytes,
            tableStart = ROOT_OFFSET_LOCATION + bytes.readIntLe(ROOT_OFFSET_LOCATION),
        )

        val columns = SchemaReaders.readColumns(headerTable, 18)
        val crs = headerTable.table(24)?.let(SchemaReaders::readCrs)
        val envelope = headerTable.doubleVector(6)?.takeIf { it.size == 4 }?.let {
            Envelope(
                minX = it[0],
                minY = it[1],
                maxX = it[2],
                maxY = it[3],
            )
        }
        val featuresCount = headerTable.long(20)
        val indexNodeSize = headerTable.ushort(22, defaultValue = PackedRTree.DEFAULT_NODE_SIZE)
        val indexOffset = headerEndOffset
        val indexSize = if (featuresCount > 0 && indexNodeSize > 0) {
            PackedRTree.indexSize(featuresCount, indexNodeSize)
        } else {
            0
        }

        return HeaderMeta(
            name = headerTable.string(4),
            envelope = envelope,
            geometryType = GeometryType.fromWireValue(headerTable.ubyte(8)),
            hasZ = headerTable.bool(10),
            hasM = headerTable.bool(12),
            hasT = headerTable.bool(14),
            hasTm = headerTable.bool(16),
            columns = columns,
            featuresCount = featuresCount,
            indexNodeSize = indexNodeSize,
            crs = crs,
            title = headerTable.string(26),
            description = headerTable.string(28),
            metadata = headerTable.string(30),
            headerSize = headerSize,
            headerOffset = MAGIC_LENGTH,
            indexOffset = indexOffset,
            indexSize = indexSize,
            featureSectionOffset = indexOffset + indexSize,
        )
    }

}
