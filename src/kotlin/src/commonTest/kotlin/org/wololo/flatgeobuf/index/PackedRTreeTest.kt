package org.wololo.flatgeobuf.index

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackedRTreeTest {
    @Test
    fun searchReturnsIntersectingLeafItems() {
        val nodes = mutableListOf(
            NodeItem(0.0, 0.0, 1.0, 1.0, 40),
            NodeItem(2.0, 2.0, 3.0, 3.0, 80),
        )
        PackedRTree.hilbertSortNodeItems(nodes)
        val tree = PackedRTree.fromNodeItems(nodes, nodeSize = 2)

        val results = tree.search(0.0, 0.0, 1.0, 1.0)

        assertEquals(1, results.size)
        assertTrue(nodes[results.first().index].intersects(NodeItem(0.0, 0.0, 1.0, 1.0, 0)))
    }

    @Test
    fun writeAndReadRoundTripsSearchResults() {
        val random = Random(1234)
        val nodeItems = MutableList(2048) { index ->
            val x = random.nextDouble(466379.0, 708929.0)
            val y = random.nextDouble(6096801.0, 6322352.0)
            NodeItem(x, y, x, y, index.toLong())
        }
        PackedRTree.hilbertSortNodeItems(nodeItems)

        val tree = PackedRTree.fromNodeItems(nodeItems, nodeSize = 16)
        val results = tree.search(690407.0, 6063692.0, 811682.0, 6176467.0)
        val encoded = tree.write()
        val roundTripped = PackedRTree.fromData(
            bytes = encoded,
            dataOffset = 0,
            numItems = nodeItems.size,
            nodeSize = 16,
        )
        val roundTripResults = roundTripped.search(690407.0, 6063692.0, 811682.0, 6176467.0)

        assertContentEquals(results, roundTripResults)
    }
}
