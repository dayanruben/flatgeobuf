package org.wololo.flatgeobuf

public sealed interface PropertyValue {
    public data class BoolValue(val value: Boolean) : PropertyValue
    public data class ByteValue(val value: Byte) : PropertyValue
    public data class UByteValue(val value: UByte) : PropertyValue
    public data class ShortValue(val value: Short) : PropertyValue
    public data class UShortValue(val value: UShort) : PropertyValue
    public data class IntValue(val value: Int) : PropertyValue
    public data class UIntValue(val value: UInt) : PropertyValue
    public data class LongValue(val value: Long) : PropertyValue
    public data class ULongValue(val value: ULong) : PropertyValue
    public data class FloatValue(val value: Float) : PropertyValue
    public data class DoubleValue(val value: Double) : PropertyValue
    public data class StringValue(val value: String) : PropertyValue
    public data class JsonValue(val value: String) : PropertyValue
    public data class DateTimeValue(val value: String) : PropertyValue
    public data class BinaryValue(val value: ByteArray) : PropertyValue
}
