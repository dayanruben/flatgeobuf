package org.wololo.flatgeobuf

public enum class GeometryType(public val wireValue: Int) {
    Unknown(0),
    Point(1),
    LineString(2),
    Polygon(3),
    MultiPoint(4),
    MultiLineString(5),
    MultiPolygon(6),
    GeometryCollection(7),
    CircularString(8),
    CompoundCurve(9),
    CurvePolygon(10),
    MultiCurve(11),
    MultiSurface(12),
    Curve(13),
    Surface(14),
    PolyhedralSurface(15),
    Tin(16),
    Triangle(17),
    ;

    public companion object {
        public fun fromWireValue(value: Int): GeometryType =
            entries.firstOrNull { it.wireValue == value } ?: Unknown
    }
}
