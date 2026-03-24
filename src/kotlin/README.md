# FlatGeobuf for Kotlin Multiplatform

Experimental Kotlin Multiplatform implementation of [FlatGeobuf](https://flatgeobuf.org/).

## Current scope

The first implementation slice focuses on portable core pieces that can live in `commonMain`:

* FlatGeobuf magic byte and version validation
* FlatBuffer table access for the FlatGeobuf header schema
* Header metadata parsing
* Packed Hilbert R-tree parsing, searching, and binary roundtripping
* `ByteArray`-backed `FgbReader` with `selectAll()` and `selectBbox()`
* Async range-backed `AsyncFgbReader` for remote and custom data sources
* JVM `Path`, `File`, and `InputStream` reader entry points
* JVM sequential streaming reader for feature iteration without full in-memory loading
* JVM HTTP range reader entry points backed by `java.net.http.HttpClient`
* Generic geometry and typed property decoding
* `FgbWriter` with optional packed R-tree generation
* Sink-backed and sequential streaming write APIs
* JVM JTS adapters for the shared `GeometryData` model
* Portable GeoJSON feature and geometry adapters

The module is intentionally keeping JVM-only dependencies out of the shared API surface so it can grow toward JVM and Native support from the same core.

## Build and test

From this directory:

    gradle jvmTest

## Layout

* `src/commonMain`: shared FlatGeobuf core
* `src/commonTest`: platform-independent algorithm tests
* `src/commonMain/.../geojson`: portable GeoJSON model and adapters
* `src/jvmTest`: fixture-backed JVM tests against `../../test/data`

## Next steps

* Add batched async bbox fetch strategies for large remote result sets
* Add header patching for indexed sequential writers
* Add JSON serialization helpers for the portable GeoJSON model
