# FlatGeobuf for Kotlin Multiplatform

Experimental Kotlin Multiplatform implementation of [FlatGeobuf](https://flatgeobuf.org/).

## Current scope

The first implementation slice focuses on portable core pieces that can live in `commonMain`:

* FlatGeobuf magic byte and version validation
* FlatBuffer table access for the FlatGeobuf header schema
* Header metadata parsing
* Packed Hilbert R-tree parsing, searching, and binary roundtripping

The module is intentionally keeping JVM-only dependencies out of the shared API surface so it can grow toward JVM and Native support from the same core.

## Build and test

From this directory:

    gradle jvmTest

## Layout

* `src/commonMain`: shared FlatGeobuf core
* `src/commonTest`: platform-independent algorithm tests
* `src/jvmTest`: fixture-backed JVM tests against `../../test/data`

## Next steps

* Add portable feature and geometry decoding
* Add a full `FgbReader` API over the common core
* Add writer support and optional JVM geometry adapters
