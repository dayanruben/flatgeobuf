package org.wololo.flatgeobuf.jvm

import org.wololo.flatgeobuf.FgbFeature
import org.wololo.flatgeobuf.FgbReader
import org.wololo.flatgeobuf.FlatGeobuf
import org.wololo.flatgeobuf.FlatGeobufException
import org.wololo.flatgeobuf.HeaderMeta
import org.wololo.flatgeobuf.internal.FeatureDecoder
import org.wololo.flatgeobuf.internal.readIntLe
import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.inputStream

public fun FgbReader.Companion.open(path: Path): FgbReader = Files.readAllBytes(path).let(::open)

public fun FgbReader.Companion.open(file: File): FgbReader = open(file.toPath())

public fun FgbReader.Companion.open(input: InputStream): FgbReader = open(input.readBytes())

public fun FlatGeobuf.readHeader(path: Path): HeaderMeta = Files.readAllBytes(path).let(::readHeader)

public fun FlatGeobuf.readHeader(file: File): HeaderMeta = readHeader(file.toPath())

public fun FlatGeobuf.readHeader(input: InputStream): HeaderMeta = readHeader(input.readBytes())

public class SequentialFgbReader private constructor(
    private val input: InputStream,
    public val header: HeaderMeta,
) : Closeable {
    public fun selectAll(): Sequence<FgbFeature> = sequence {
        var id = 0
        while (true) {
            val sizePrefix = input.readNBytes(4)
            if (sizePrefix.isEmpty()) break
            if (sizePrefix.size != 4) {
                throw FlatGeobufException("Truncated feature size prefix in streaming reader")
            }
            val featureSize = sizePrefix.readIntLe(0)
            if (featureSize < 0) {
                throw FlatGeobufException("Negative feature size $featureSize in streaming reader")
            }
            val featureBytes = input.readNBytes(featureSize)
            if (featureBytes.size != featureSize) {
                throw EOFException("Unexpected EOF while reading streamed feature")
            }
            val bytes = ByteArray(4 + featureSize)
            sizePrefix.copyInto(bytes, 0)
            featureBytes.copyInto(bytes, 4)
            yield(
                FeatureDecoder.readFeature(
                    bytes = bytes,
                    featureOffset = 0,
                    id = id++,
                    header = header,
                ),
            )
        }
    }

    override fun close() {
        input.close()
    }

    public companion object {
        public fun open(input: InputStream): SequentialFgbReader {
            val buffered = input.buffered()
            val headerBytes = readHeaderBytes(buffered)
            val header = FlatGeobuf.readHeader(headerBytes)
            skipFully(buffered, header.indexSize.toLong())
            return SequentialFgbReader(buffered, header)
        }

        public fun open(path: Path): SequentialFgbReader = open(path.inputStream())

        public fun open(file: File): SequentialFgbReader = open(file.inputStream())

        private fun readHeaderBytes(input: InputStream): ByteArray {
            val magicBytes = input.readNBytes(8)
            if (magicBytes.size != 8) {
                throw EOFException("Unexpected EOF while reading FlatGeobuf magic bytes")
            }
            val sizePrefix = input.readNBytes(4)
            if (sizePrefix.size != 4) {
                throw EOFException("Unexpected EOF while reading FlatGeobuf header size")
            }
            val headerSize = sizePrefix.readIntLe(0)
            if (headerSize < 0) {
                throw FlatGeobufException("Negative FlatGeobuf header size $headerSize")
            }
            val headerPayload = input.readNBytes(headerSize)
            if (headerPayload.size != headerSize) {
                throw EOFException("Unexpected EOF while reading FlatGeobuf header")
            }

            return ByteArray(12 + headerSize).also { bytes ->
                magicBytes.copyInto(bytes, 0)
                sizePrefix.copyInto(bytes, 8)
                headerPayload.copyInto(bytes, 12)
            }
        }

        private fun skipFully(input: InputStream, bytesToSkip: Long) {
            var remaining = bytesToSkip
            while (remaining > 0) {
                val skipped = input.skip(remaining)
                if (skipped > 0) {
                    remaining -= skipped
                } else {
                    if (input.read() == -1) {
                        throw EOFException("Unexpected EOF while skipping FlatGeobuf index")
                    }
                    remaining--
                }
            }
        }
    }
}
