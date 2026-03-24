package org.wololo.flatgeobuf.internal

import org.wololo.flatgeobuf.ColumnMeta
import org.wololo.flatgeobuf.ColumnType
import org.wololo.flatgeobuf.CrsMeta
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

        val columns = buildList {
            repeat(headerTable.tableVectorLength(18)) { index ->
                add(readColumn(headerTable.tableVector(18, index)))
            }
        }
        val crs = headerTable.table(24)?.let(::readCrs)
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

    private fun readColumn(columnTable: FlatBufferTable): ColumnMeta =
        ColumnMeta(
            name = columnTable.string(4) ?: throw FlatGeobufException("Column name is required"),
            type = ColumnType.fromWireValue(columnTable.ubyte(6)),
            title = columnTable.string(8),
            description = columnTable.string(10),
            width = columnTable.int(12, defaultValue = -1),
            precision = columnTable.int(14, defaultValue = -1),
            scale = columnTable.int(16, defaultValue = -1),
            nullable = columnTable.bool(18, defaultValue = true),
            unique = columnTable.bool(20),
            primaryKey = columnTable.bool(22),
            metadata = columnTable.string(24),
        )

    private fun readCrs(crsTable: FlatBufferTable): CrsMeta =
        CrsMeta(
            org = crsTable.string(4),
            code = crsTable.int(6),
            name = crsTable.string(8),
            description = crsTable.string(10),
            wkt = crsTable.string(12),
            codeString = crsTable.string(14),
        )
}
