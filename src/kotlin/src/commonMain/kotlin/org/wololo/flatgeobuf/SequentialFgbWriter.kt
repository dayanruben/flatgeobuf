package org.wololo.flatgeobuf

import org.wololo.flatgeobuf.internal.FeatureEncoder

public class SequentialFgbWriter private constructor(
    private val sink: FgbWriteSink,
    public val header: HeaderMeta,
) {
    private val datasetColumns: List<ColumnMeta> = header.columns
    private var featureCount: Long = 0
    private var closed: Boolean = false

    public fun write(feature: FgbFeature) {
        ensureOpen()
        val columns = if (datasetColumns.isNotEmpty()) datasetColumns else feature.columns
        sink.write(
            FeatureEncoder.encodeFeature(
                geometry = feature.geometry,
                properties = feature.properties,
                columns = columns,
                writeFeatureColumns = datasetColumns.isEmpty(),
            ),
        )
        featureCount++
    }

    public fun close() {
        if (closed) return
        closed = true
        if (header.featuresCount > 0 && header.featuresCount != featureCount) {
            throw FlatGeobufException(
                "Sequential writer expected ${header.featuresCount} features, wrote $featureCount",
            )
        }
    }

    private fun ensureOpen() {
        if (closed) {
            throw FlatGeobufException("Sequential writer is already closed")
        }
    }

    public companion object {
        public fun open(sink: FgbWriteSink, header: HeaderMeta): SequentialFgbWriter {
            if (header.indexNodeSize > 0) {
                throw FlatGeobufException("Sequential writer does not support indexed output")
            }

            val outputHeader = header.copy(
                indexNodeSize = 0,
                indexOffset = 0,
                indexSize = 0,
                featureSectionOffset = 0,
                headerSize = 0,
                headerOffset = 0,
            )

            sink.write(FlatGeobuf.MAGIC_BYTES)
            sink.write(FeatureEncoder.encodeHeader(outputHeader))

            return SequentialFgbWriter(sink, outputHeader)
        }
    }
}
