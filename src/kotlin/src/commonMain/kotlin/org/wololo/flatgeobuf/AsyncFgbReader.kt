package org.wololo.flatgeobuf

import org.wololo.flatgeobuf.index.PackedRTree
import org.wololo.flatgeobuf.internal.FeatureDecoder
import org.wololo.flatgeobuf.internal.readIntLe

public class AsyncFgbReader private constructor(
    private val source: AsyncByteRangeSource,
    public val header: HeaderMeta,
) {
    public suspend fun selectAll(): List<FgbFeature> = buildList {
        scanAll { add(it) }
    }

    public suspend fun scanAll(consumer: suspend (FgbFeature) -> Unit) {
        var offset = header.featureSectionOffset.toLong()
        var id = 0
        while (true) {
            val featureBytes = readNextFeature(offset) ?: break
            consumer(
                FeatureDecoder.readFeature(
                    bytes = featureBytes,
                    featureOffset = 0,
                    id = id++,
                    header = header,
                ),
            )
            offset += featureBytes.size
        }
    }

    public suspend fun selectBbox(
        minX: Double,
        minY: Double,
        maxX: Double,
        maxY: Double,
    ): List<FgbFeature> = buildList {
        scanBbox(minX, minY, maxX, maxY) { add(it) }
    }

    public suspend fun scanBbox(
        minX: Double,
        minY: Double,
        maxX: Double,
        maxY: Double,
        consumer: suspend (FgbFeature) -> Unit,
    ) {
        if (!header.hasIndex) {
            throw FlatGeobufException("FlatGeobuf does not contain a searchable index")
        }
        if (header.featuresCount > Int.MAX_VALUE) {
            throw FlatGeobufException("Feature counts above ${Int.MAX_VALUE} are not yet supported")
        }

        val indexBytes = source.readExactly(
            offset = header.indexOffset.toLong(),
            length = header.indexSize,
            description = "FlatGeobuf index",
        )
        val tree = PackedRTree.fromData(
            bytes = indexBytes,
            dataOffset = 0,
            numItems = header.featuresCount.toInt(),
            nodeSize = header.indexNodeSize,
        )

        tree.search(minX, minY, maxX, maxY).forEach { hit ->
            consumer(readFeatureAt(header.featureSectionOffset.toLong() + hit.offset, hit.index))
        }
    }

    private suspend fun readFeatureAt(offset: Long, id: Int): FgbFeature {
        val sizePrefix = source.readExactly(offset, 4, "feature size prefix")
        val featureSize = sizePrefix.readIntLe(0)
        if (featureSize < 0) {
            throw FlatGeobufException("Negative feature size $featureSize at offset $offset")
        }
        val featureData = source.readExactly(offset + 4, featureSize, "feature body")
        val bytes = ByteArray(4 + featureSize)
        sizePrefix.copyInto(bytes, 0)
        featureData.copyInto(bytes, 4)
        return FeatureDecoder.readFeature(
            bytes = bytes,
            featureOffset = 0,
            id = id,
            header = header,
        )
    }

    private suspend fun readNextFeature(offset: Long): ByteArray? {
        val sizePrefix = source.read(offset, 4)
        if (sizePrefix.isEmpty()) return null
        if (sizePrefix.size != 4) {
            throw FlatGeobufException("Truncated feature size prefix at offset $offset")
        }
        val featureSize = sizePrefix.readIntLe(0)
        if (featureSize < 0) {
            throw FlatGeobufException("Negative feature size $featureSize at offset $offset")
        }
        val featureData = source.readExactly(offset + 4, featureSize, "feature body")
        return ByteArray(4 + featureSize).also { bytes ->
            sizePrefix.copyInto(bytes, 0)
            featureData.copyInto(bytes, 4)
        }
    }

    public companion object {
        public suspend fun open(source: AsyncByteRangeSource): AsyncFgbReader =
            AsyncFgbReader(source, readHeader(source))

        public suspend fun readHeader(source: AsyncByteRangeSource): HeaderMeta {
            val prefix = source.readExactly(0, 12, "FlatGeobuf header prefix")
            if (!FlatGeobuf.isFlatGeobuf(prefix)) {
                throw FlatGeobufException("Invalid FlatGeobuf magic bytes")
            }
            val headerSize = prefix.readIntLe(8)
            if (headerSize !in 8..(10 * 1024 * 1024)) {
                throw FlatGeobufException("Illegal FlatGeobuf header size: $headerSize")
            }
            val headerBytes = source.readExactly(
                offset = 0,
                length = 12 + headerSize,
                description = "FlatGeobuf header",
            )
            return FlatGeobuf.readHeader(headerBytes)
        }
    }
}
