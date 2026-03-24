package org.wololo.flatgeobuf

import org.wololo.flatgeobuf.index.SearchResultItem
import org.wololo.flatgeobuf.internal.FeatureDecoder
import org.wololo.flatgeobuf.internal.readIntLe

public class FgbReader private constructor(
    private val bytes: ByteArray,
    public val header: HeaderMeta,
) {
    public fun selectAll(): Sequence<FgbFeature> = sequence {
        var offset = header.featureSectionOffset
        var id = 0
        while (offset < bytes.size) {
            val feature = readFeature(offset, id)
            yield(feature)
            offset += featureByteLength(offset)
            id++
        }
    }

    public fun selectBbox(
        minX: Double,
        minY: Double,
        maxX: Double,
        maxY: Double,
    ): Sequence<FgbFeature> {
        if (!header.hasIndex) {
            throw FlatGeobufException("FlatGeobuf does not contain a searchable index")
        }
        val hits = FlatGeobuf.searchIndex(bytes, minX, minY, maxX, maxY)
        return hits.asSequence().map { hit ->
            readFeature(header.featureSectionOffset + hit.offset.toInt(), hit.index)
        }
    }

    private fun readFeature(offset: Int, id: Int): FgbFeature =
        FeatureDecoder.readFeature(
            bytes = bytes,
            featureOffset = offset,
            id = id,
            header = header,
        )

    private fun featureByteLength(offset: Int): Int {
        if (offset + 4 > bytes.size) {
            throw FlatGeobufException("Feature size prefix exceeds available bytes")
        }
        val featureSize = bytes.readIntLe(offset)
        val totalSize = 4 + featureSize
        if (featureSize < 0 || offset + totalSize > bytes.size) {
            throw FlatGeobufException("Feature at offset $offset exceeds available bytes")
        }
        return totalSize
    }

    public companion object {
        public fun open(bytes: ByteArray): FgbReader = FgbReader(bytes, FlatGeobuf.readHeader(bytes))
    }
}
