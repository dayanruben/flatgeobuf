package org.wololo.flatgeobuf.index

import kotlin.math.max
import kotlin.math.min

public data class NodeItem(
    var minX: Double,
    var minY: Double,
    var maxX: Double,
    var maxY: Double,
    var offset: Long,
) {
    public fun width(): Double = maxX - minX

    public fun height(): Double = maxY - minY

    public fun intersects(other: NodeItem): Boolean =
        minX <= other.maxX &&
            maxX >= other.minX &&
            minY <= other.maxY &&
            maxY >= other.minY

    public fun expand(other: NodeItem) {
        minX = min(minX, other.minX)
        minY = min(minY, other.minY)
        maxX = max(maxX, other.maxX)
        maxY = max(maxY, other.maxY)
    }

    public companion object {
        public fun empty(offset: Long): NodeItem =
            NodeItem(
                minX = Double.POSITIVE_INFINITY,
                minY = Double.POSITIVE_INFINITY,
                maxX = Double.NEGATIVE_INFINITY,
                maxY = Double.NEGATIVE_INFINITY,
                offset = offset,
            )
    }
}
