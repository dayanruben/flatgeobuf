package org.wololo.flatgeobuf

public data class FgbFeature(
    val id: Int,
    val offset: Int,
    val geometry: GeometryData?,
    val properties: Map<String, PropertyValue>,
    val columns: List<ColumnMeta>,
)
