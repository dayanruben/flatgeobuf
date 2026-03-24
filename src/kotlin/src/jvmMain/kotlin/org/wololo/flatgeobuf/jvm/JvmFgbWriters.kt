package org.wololo.flatgeobuf.jvm

import org.wololo.flatgeobuf.FgbFeature
import org.wololo.flatgeobuf.FgbWriteSink
import org.wololo.flatgeobuf.FgbWriter
import org.wololo.flatgeobuf.HeaderMeta
import org.wololo.flatgeobuf.SequentialFgbWriter
import org.wololo.flatgeobuf.index.PackedRTree
import java.io.OutputStream

public class OutputStreamFgbWriteSink(
    private val output: OutputStream,
) : FgbWriteSink {
    override fun write(bytes: ByteArray) {
        output.write(bytes)
    }
}

public fun FgbWriter.writeTo(
    output: OutputStream,
    header: HeaderMeta,
    features: List<FgbFeature>,
    includeIndex: Boolean = true,
    indexNodeSize: Int = PackedRTree.DEFAULT_NODE_SIZE,
) {
    writeTo(
        sink = OutputStreamFgbWriteSink(output),
        header = header,
        features = features,
        includeIndex = includeIndex,
        indexNodeSize = indexNodeSize,
    )
}

public fun SequentialFgbWriter.Companion.open(
    output: OutputStream,
    header: HeaderMeta,
): SequentialFgbWriter = open(OutputStreamFgbWriteSink(output), header)
