# Monica Steam Rust core

This directory contains the performance-sensitive Steam protocol core and its Android JNI bridge. The migration is intentionally coarse-grained: parsing/encoding work that benefits from native execution crosses JNI once per response or operation, while Android UI, lifecycle, localization and platform integration stay in Kotlin/Compose.

## Toolchains

- `monica-steam-core`: Rust 1.75+ (`rust-version = "1.75"`)
- `monica-steam-android`: Rust 1.77+ (`rust-version = "1.77"`), matching the current JNI dependency chain
- Android CI NDK: `27.2.12479018`
- Android minSdk: 26
- Packaged ABIs: `arm64-v8a`, `armeabi-v7a`

The CI deliberately tests the core at Rust 1.75 and the complete workspace at Rust 1.77 instead of testing only the latest stable compiler.

## Host tests

```bash
rustup toolchain install 1.75.0 1.77.0

cargo +1.75.0 test \
  --manifest-path rust/monica-steam-core/Cargo.toml \
  --all-targets

cargo +1.77.0 test \
  --manifest-path rust/Cargo.toml \
  --workspace \
  --all-targets
```

## Build Android JNI libraries

Install the Android Rust targets and `cargo-ndk` first:

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi
cargo install cargo-ndk --locked
```

Set `ANDROID_NDK_HOME` (or `ANDROID_NDK_ROOT` / `ANDROID_NDK`) to an installed NDK, then run:

```bash
scripts/build-rust-android.sh
```

The script writes:

```text
app/src/main/jniLibs/arm64-v8a/libmonica_steam_android.so
app/src/main/jniLibs/armeabi-v7a/libmonica_steam_android.so
```

Then build Android normally:

```bash
./gradlew assembleDebug
```

`Steam Network CI` also verifies that both `.so` files are actually present inside the generated split APKs. Successful CI runs upload the debug APKs together with `apk-sha256.txt` and `apk-contents.txt` for device testing.

## Native bridge policy

`RustSteamCoreNative` loads `libmonica_steam_android.so` opportunistically. Every Android call site keeps a Kotlin implementation as a fallback. If the native library is unavailable, JNI throws, a parser rejects malformed input, or a versioned bridge payload fails validation, the existing Kotlin path remains available.

Do not move tiny JVM-only operations across JNI just for language consistency. A native call should cover enough work to amortize the JNI boundary.

## Versioned binary bridges

The JNI layer returns compact binary payloads instead of constructing large Java/Kotlin object graphs one JNI call at a time.

| Magic | Purpose |
| --- | --- |
| `MSC1` | decoded Steam CM envelopes |
| `MSS1` | direct-message chat sessions |
| `MSM1` | direct-message chat history pages |
| `MSL1` | owned games |
| `MSP1` | achievement progress |
| `MST1` | StoreBrowse metadata |
| `MSA1` | merged achievement definitions + user status |
| `MSF1` | family shared-library apps |
| `MSG1` | group-chat history pages |
| `MGS1` | group/room summaries |

Kotlin decoders validate bridge magic, counts, lengths, enum/boolean values and trailing bytes before producing domain objects.

## Semantic compatibility

Rust parsers are expected to preserve the behavior of the Kotlin fallback, including non-obvious cases such as:

- first-vs-last duplicate protobuf fields;
- `Long.toInt()` truncation where the Kotlin parser uses it;
- fixed32/fixed64 vs varint behavior;
- Kotlin's broader `.bytes` semantics for fixed fields in the few call sites that rely on it;
- insertion-order deduplication;
- stable sorting;
- deliberately lax packed-varint decoding in group member lists;
- malformed nested-item skip/fail ordering.

When migrating another parser, add Rust semantic tests and a Kotlin bridge-layout/validation test before routing production traffic through it.
