package org.wololo.flatgeobuf.internal

import org.wololo.flatgeobuf.ColumnMeta
import org.wololo.flatgeobuf.ColumnType
import org.wololo.flatgeobuf.CrsMeta
import org.wololo.flatgeobuf.FlatGeobufException

internal object SchemaReaders {
    fun readColumns(table: FlatBufferTable, fieldOffset: Int): List<ColumnMeta> =
        buildList {
            repeat(table.tableVectorLength(fieldOffset)) { index ->
                add(readColumn(table.tableVector(fieldOffset, index)))
            }
        }

    fun readCrs(table: FlatBufferTable): CrsMeta =
        CrsMeta(
            org = table.string(4),
            code = table.int(6),
            name = table.string(8),
            description = table.string(10),
            wkt = table.string(12),
            codeString = table.string(14),
        )

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
}
