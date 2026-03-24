package org.wololo.flatgeobuf

public data class ColumnMeta(
    val name: String,
    val type: ColumnType?,
    val title: String?,
    val description: String?,
    val width: Int,
    val precision: Int,
    val scale: Int,
    val nullable: Boolean,
    val unique: Boolean,
    val primaryKey: Boolean,
    val metadata: String?,
)
