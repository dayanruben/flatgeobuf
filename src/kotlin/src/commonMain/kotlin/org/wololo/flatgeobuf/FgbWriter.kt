package org.wololo.flatgeobuf

import org.wololo.flatgeobuf.index.NodeItem
import org.wololo.flatgeobuf.index.PackedRTree
import org.wololo.flatgeobuf.internal.FeatureEncoder

public object FgbWriter {
    public fun write(
        header: HeaderMeta,
        features: List<FgbFeature>,
        includeIndex: Boolean = true,
        indexNodeSize: Int = PackedRTree.DEFAULT_NODE_SIZE,
    ): ByteArray {
        val sink = BufferingWriteSink()
        writeTo(
            sink = sink,
            header = header,
            features = features,
            includeIndex = includeIndex,
            indexNodeSize = indexNodeSize,
        )
        return sink.toByteArray()
    }

    public fun writeTo(
        sink: FgbWriteSink,
        header: HeaderMeta,
        features: List<FgbFeature>,
        includeIndex: Boolean = true,
        indexNodeSize: Int = PackedRTree.DEFAULT_NODE_SIZE,
    ) {
        if (includeIndex && features.any { it.geometry == null }) {
            throw FlatGeobufException("Indexed writes require geometry on all features")
        }

        val datasetColumns = when {
            header.columns.isNotEmpty() -> header.columns
            features.isNotEmpty() -> features.first().columns
            else -> emptyList()
        }

        val encoded = features.map { feature ->
            val columns = if (datasetColumns.isNotEmpty()) datasetColumns else feature.columns
            val bytes = FeatureEncoder.encodeFeature(
                geometry = feature.geometry,
                properties = feature.properties,
                columns = columns,
                writeFeatureColumns = datasetColumns.isEmpty(),
            )
            EncodedFeature(
                feature = feature,
                bytes = bytes,
                columns = columns,
                bounds = feature.geometry?.let(::computeBounds),
            )
        }.toMutableList()

        val extent = encoded.mapNotNull { it.bounds }.takeIf { it.isNotEmpty() }?.let(PackedRTree::calcExtent)
        val indexed = includeIndex && encoded.isNotEmpty()
        if (indexed) {
            val safeExtent = requireNotNull(extent)
            encoded.sortByDescending { PackedRTree.hilbertValue(it.bounds ?: error("Missing geometry bounds"), safeExtent) }
        }

        val featureBytes = buildList {
            var featureOffset = 0L
            encoded.forEach { encodedFeature ->
                encodedFeature.bounds?.offset = featureOffset
                add(encodedFeature.bytes)
                featureOffset += encodedFeature.bytes.size
            }
        }

        val indexBytes = if (indexed) {
            val tree = PackedRTree.fromNodeItems(
                nodeItems = encoded.map { requireNotNull(it.bounds) },
                nodeSize = indexNodeSize,
            )
            tree.write()
        } else {
            ByteArray(0)
        }

        val outputHeader = header.copy(
            envelope = extent?.let { Envelope(it.minX, it.minY, it.maxX, it.maxY) },
            hasZ = header.hasZ || encoded.any { hasDimension(it.feature.geometry) { geometry -> geometry.z != null } },
            hasM = header.hasM || encoded.any { hasDimension(it.feature.geometry) { geometry -> geometry.m != null } },
            hasT = header.hasT || encoded.any { hasDimension(it.feature.geometry) { geometry -> geometry.t != null } },
            hasTm = header.hasTm || encoded.any { hasDimension(it.feature.geometry) { geometry -> geometry.tm != null } },
            columns = datasetColumns,
            featuresCount = features.size.toLong(),
            indexNodeSize = if (indexed) indexNodeSize else 0,
            headerSize = 0,
            headerOffset = 0,
            indexOffset = 0,
            indexSize = 0,
            featureSectionOffset = 0,
        )
        val headerBytes = FeatureEncoder.encodeHeader(outputHeader)

        sink.write(FlatGeobuf.MAGIC_BYTES)
        sink.write(headerBytes)
        if (indexBytes.isNotEmpty()) sink.write(indexBytes)
        featureBytes.forEach(sink::write)
    }

    private fun hasDimension(geometry: GeometryData?, predicate: (GeometryData) -> Boolean): Boolean {
        if (geometry == null) return false
        if (predicate(geometry)) return true
        return geometry.parts.any { hasDimension(it, predicate) }
    }

    private fun computeBounds(geometry: GeometryData): NodeItem {
        val bounds = NodeItem.empty(0)
        appendBounds(bounds, geometry)
        return bounds
    }

    private fun appendBounds(bounds: NodeItem, geometry: GeometryData) {
        val xy = geometry.xy
        var index = 0
        while (index + 1 < xy.size) {
            val x = xy[index]
            val y = xy[index + 1]
            bounds.expand(NodeItem(x, y, x, y, 0))
            index += 2
        }
        geometry.parts.forEach { appendBounds(bounds, it) }
    }

    private data class EncodedFeature(
        val feature: FgbFeature,
        val bytes: ByteArray,
        val columns: List<ColumnMeta>,
        val bounds: NodeItem?,
    )

    private class BufferingWriteSink : FgbWriteSink {
        private val chunks = mutableListOf<ByteArray>()
        private var size = 0

        override fun write(bytes: ByteArray) {
            chunks += bytes
            size += bytes.size
        }

        fun toByteArray(): ByteArray {
            val output = ByteArray(size)
            var offset = 0
            chunks.forEach { chunk ->
                chunk.copyInto(output, offset)
                offset += chunk.size
            }
            return output
        }
    }
}
