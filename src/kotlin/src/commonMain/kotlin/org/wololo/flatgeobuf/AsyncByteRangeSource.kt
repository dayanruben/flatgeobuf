package org.wololo.flatgeobuf

public interface AsyncByteRangeSource {
    public val size: Long?

    /**
     * Reads up to [length] bytes starting at [offset].
     *
     * Implementations may return fewer bytes at EOF.
     */
    public suspend fun read(offset: Long, length: Int): ByteArray
}

internal suspend fun AsyncByteRangeSource.readExactly(
    offset: Long,
    length: Int,
    description: String,
): ByteArray {
    if (length == 0) return ByteArray(0)
    val bytes = read(offset, length)
    if (bytes.size != length) {
        throw FlatGeobufException(
            "Expected $length bytes for $description at offset $offset, got ${bytes.size}",
        )
    }
    return bytes
}
