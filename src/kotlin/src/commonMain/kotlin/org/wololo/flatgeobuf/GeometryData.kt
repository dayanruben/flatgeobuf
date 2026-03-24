package org.wololo.flatgeobuf

public data class GeometryData(
    val type: GeometryType,
    val xy: DoubleArray,
    val z: DoubleArray? = null,
    val m: DoubleArray? = null,
    val t: DoubleArray? = null,
    val tm: LongArray? = null,
    val ends: IntArray? = null,
    val parts: List<GeometryData> = emptyList(),
)
