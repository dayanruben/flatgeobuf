package org.wololo.flatgeobuf.index

import org.wololo.flatgeobuf.FlatGeobufException
import org.wololo.flatgeobuf.internal.readDoubleLe
import org.wololo.flatgeobuf.internal.readLongLe
import org.wololo.flatgeobuf.internal.writeDoubleLe
import org.wololo.flatgeobuf.internal.writeLongLe
import kotlin.math.floor
import kotlin.math.min

public class PackedRTree private constructor(
    private val nodeItems: List<NodeItem>,
    private val numItems: Int,
    private val nodeSize: Int,
    private val levelBounds: List<LevelBound>,
) {
    public fun search(minX: Double, minY: Double, maxX: Double, maxY: Double): List<SearchResultItem> {
        val query = NodeItem(minX, minY, maxX, maxY, 0)
        val results = mutableListOf<SearchResultItem>()
        val leafNodesOffset = levelBounds.first().start
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.addLast(0 to (levelBounds.lastIndex))

        while (queue.isNotEmpty()) {
            val (nodeIndex, level) = queue.removeFirst()
            val isLeafNode = nodeIndex >= nodeItems.size - numItems
            val end = min(nodeIndex + nodeSize, levelBounds[level].end)
            for (pos in nodeIndex until end) {
                val nodeItem = nodeItems[pos]
                if (!nodeItem.intersects(query)) continue
                if (isLeafNode) {
                    results += SearchResultItem(
                        offset = nodeItem.offset,
                        index = pos - leafNodesOffset,
                    )
                } else {
                    queue.addLast(nodeItem.offset.toInt() to (level - 1))
                }
            }
        }

        return results.sortedBy { it.offset }
    }

    public fun write(): ByteArray {
        val bytes = ByteArray(nodeItems.size * NODE_ITEM_LEN_BYTES)
        nodeItems.forEachIndexed { index, nodeItem ->
            val offset = index * NODE_ITEM_LEN_BYTES
            bytes.writeDoubleLe(offset, nodeItem.minX)
            bytes.writeDoubleLe(offset + 8, nodeItem.minY)
            bytes.writeDoubleLe(offset + 16, nodeItem.maxX)
            bytes.writeDoubleLe(offset + 24, nodeItem.maxY)
            bytes.writeLongLe(offset + 32, nodeItem.offset)
        }
        return bytes
    }

    private data class LevelBound(val start: Int, val end: Int)

    public companion object {
        public const val DEFAULT_NODE_SIZE: Int = 16
        public const val HILBERT_MAX: Int = (1 shl 16) - 1
        public const val NODE_ITEM_LEN_BYTES: Int = 8 * 4 + 8

        public fun fromNodeItems(
            nodeItems: List<NodeItem>,
            nodeSize: Int = DEFAULT_NODE_SIZE,
        ): PackedRTree {
            require(nodeItems.isNotEmpty()) { "cannot create an empty PackedRTree" }
            val boundedNodeSize = validateNodeSize(nodeSize)
            val levelBounds = generateLevelBounds(nodeItems.size, boundedNodeSize)
            val totalNodes = levelBounds.first().end
            val allNodes = MutableList(totalNodes) { NodeItem.empty(0) }
            val leafStart = totalNodes - nodeItems.size
            nodeItems.forEachIndexed { index, nodeItem ->
                allNodes[leafStart + index] = nodeItem.copy()
            }
            generateNodes(allNodes, boundedNodeSize, levelBounds)
            return PackedRTree(
                nodeItems = allNodes,
                numItems = nodeItems.size,
                nodeSize = boundedNodeSize,
                levelBounds = levelBounds,
            )
        }

        public fun fromData(
            bytes: ByteArray,
            dataOffset: Int,
            numItems: Int,
            nodeSize: Int,
        ): PackedRTree {
            require(numItems > 0) { "cannot create an empty PackedRTree" }
            val boundedNodeSize = validateNodeSize(nodeSize)
            val levelBounds = generateLevelBounds(numItems, boundedNodeSize)
            val totalNodes = levelBounds.first().end
            val expectedSize = totalNodes * NODE_ITEM_LEN_BYTES
            if (dataOffset < 0 || dataOffset + expectedSize > bytes.size) {
                throw FlatGeobufException("PackedRTree data exceeds the available byte array")
            }
            val nodeItems = MutableList(totalNodes) { index ->
                readNodeItem(bytes, dataOffset + (index * NODE_ITEM_LEN_BYTES))
            }
            return PackedRTree(
                nodeItems = nodeItems,
                numItems = numItems,
                nodeSize = boundedNodeSize,
                levelBounds = levelBounds,
            )
        }

        public fun calcExtent(nodeItems: Iterable<NodeItem>): NodeItem {
            val extent = NodeItem.empty(0)
            nodeItems.forEach { extent.expand(it) }
            return extent
        }

        public fun hilbertSortNodeItems(nodeItems: MutableList<NodeItem>) {
            val extent = calcExtent(nodeItems)
            nodeItems.sortByDescending {
                hilbertValue(it, extent)
            }
        }

        internal fun hilbertValue(nodeItem: NodeItem, extent: NodeItem): Int =
            hilbertForNodeItem(
                nodeItem = nodeItem,
                hilbertMax = HILBERT_MAX,
                minX = extent.minX,
                minY = extent.minY,
                width = extent.width(),
                height = extent.height(),
            )

        public fun indexSize(numItems: Long, nodeSize: Int): Int {
            if (numItems <= 0) return 0
            val boundedNodeSize = validateNodeSize(nodeSize)
            var n = numItems
            var numNodes = n
            do {
                n = (n + boundedNodeSize - 1) / boundedNodeSize
                numNodes += n
            } while (n != 1L)
            val size = numNodes * NODE_ITEM_LEN_BYTES
            if (size > Int.MAX_VALUE) {
                throw FlatGeobufException("PackedRTree larger than ${Int.MAX_VALUE} bytes is not yet supported")
            }
            return size.toInt()
        }

        private fun generateNodes(
            nodeItems: MutableList<NodeItem>,
            nodeSize: Int,
            levelBounds: List<LevelBound>,
        ) {
            for (i in 0 until levelBounds.lastIndex) {
                var pos = levelBounds[i].start
                val end = levelBounds[i].end
                var newPos = levelBounds[i + 1].start
                while (pos < end) {
                    val node = NodeItem.empty(pos.toLong())
                    repeat(nodeSize) {
                        if (pos < end) {
                            node.expand(nodeItems[pos])
                            pos++
                        }
                    }
                    nodeItems[newPos++] = node
                }
            }
        }

        private fun generateLevelBounds(numItems: Int, nodeSize: Int): List<LevelBound> {
            require(numItems > 0) { "cannot create an empty PackedRTree" }
            val boundedNodeSize = validateNodeSize(nodeSize)
            val levelNumNodes = mutableListOf(numItems)
            var n = numItems
            var numNodes = n
            do {
                n = (n + boundedNodeSize - 1) / boundedNodeSize
                numNodes += n
                levelNumNodes += n
            } while (n != 1)

            var cursor = numNodes
            val levelOffsets = levelNumNodes.map { size ->
                val start = cursor - size
                cursor = start
                start
            }

            return levelNumNodes.indices.map { index ->
                LevelBound(
                    start = levelOffsets[index],
                    end = levelOffsets[index] + levelNumNodes[index],
                )
            }
        }

        private fun validateNodeSize(nodeSize: Int): Int {
            require(nodeSize >= 2) { "nodeSize must be >= 2" }
            return nodeSize.coerceAtMost(HILBERT_MAX)
        }

        private fun readNodeItem(bytes: ByteArray, offset: Int): NodeItem =
            NodeItem(
                minX = bytes.readDoubleLe(offset),
                minY = bytes.readDoubleLe(offset + 8),
                maxX = bytes.readDoubleLe(offset + 16),
                maxY = bytes.readDoubleLe(offset + 24),
                offset = bytes.readLongLe(offset + 32),
            )

        private fun hilbertForNodeItem(
            nodeItem: NodeItem,
            hilbertMax: Int,
            minX: Double,
            minY: Double,
            width: Double,
            height: Double,
        ): Int {
            val x = if (width == 0.0) {
                0
            } else {
                floor(hilbertMax * (((nodeItem.minX + nodeItem.maxX) / 2.0) - minX) / width).toInt()
            }
            val y = if (height == 0.0) {
                0
            } else {
                floor(hilbertMax * (((nodeItem.minY + nodeItem.maxY) / 2.0) - minY) / height).toInt()
            }
            return hilbert(x, y)
        }

        // Based on the public-domain implementation used by the existing Go and Java bindings.
        private fun hilbert(x0: Int, y0: Int): Int {
            var x = x0
            var y = y0
            var a = x xor y
            var b = 0xFFFF xor a
            var c = 0xFFFF xor (x or y)
            var d = x and (y xor 0xFFFF)
            var aa = a or (b shr 1)
            var bb = (a shr 1) xor a
            var cc = ((c shr 1) xor (b and (d shr 1))) xor c
            var dd = ((a and (c shr 1)) xor (d shr 1)) xor d

            a = aa
            b = bb
            c = cc
            d = dd
            aa = ((a and (a shr 2)) xor (b and (b shr 2)))
            bb = ((a and (b shr 2)) xor (b and ((a xor b) shr 2)))
            cc = cc xor ((a and (c shr 2)) xor (b and (d shr 2)))
            dd = dd xor ((b and (c shr 2)) xor ((a xor b) and (d shr 2)))

            a = aa
            b = bb
            c = cc
            d = dd
            aa = ((a and (a shr 4)) xor (b and (b shr 4)))
            bb = ((a and (b shr 4)) xor (b and ((a xor b) shr 4)))
            cc = cc xor ((a and (c shr 4)) xor (b and (d shr 4)))
            dd = dd xor ((b and (c shr 4)) xor ((a xor b) and (d shr 4)))

            a = aa
            b = bb
            cc = cc xor ((a and (cc shr 8)) xor (b and (dd shr 8)))
            dd = dd xor ((b and (cc shr 8)) xor ((a xor b) and (dd shr 8)))

            a = cc xor (cc shr 1)
            b = dd xor (dd shr 1)

            var i0 = x xor y
            var i1 = b or (0xFFFF xor (i0 or a))

            i0 = (i0 or (i0 shl 8)) and 0x00FF00FF
            i0 = (i0 or (i0 shl 4)) and 0x0F0F0F0F
            i0 = (i0 or (i0 shl 2)) and 0x33333333
            i0 = (i0 or (i0 shl 1)) and 0x55555555

            i1 = (i1 or (i1 shl 8)) and 0x00FF00FF
            i1 = (i1 or (i1 shl 4)) and 0x0F0F0F0F
            i1 = (i1 or (i1 shl 2)) and 0x33333333
            i1 = (i1 or (i1 shl 1)) and 0x55555555

            return (i1 shl 1) or i0
        }
    }
}
