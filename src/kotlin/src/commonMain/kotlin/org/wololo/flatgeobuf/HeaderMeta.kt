package org.wololo.flatgeobuf

public data class HeaderMeta(
    val name: String?,
    val envelope: Envelope?,
    val geometryType: GeometryType,
    val hasZ: Boolean,
    val hasM: Boolean,
    val hasT: Boolean,
    val hasTm: Boolean,
    val columns: List<ColumnMeta>,
    val featuresCount: Long,
    val indexNodeSize: Int,
    val crs: CrsMeta?,
    val title: String?,
    val description: String?,
    val metadata: String?,
    val headerSize: Int,
    val headerOffset: Int,
    val indexOffset: Int,
    val indexSize: Int,
    val featureSectionOffset: Int,
) {
    public val hasIndex: Boolean
        get() = indexNodeSize > 0 && featuresCount > 0
}
