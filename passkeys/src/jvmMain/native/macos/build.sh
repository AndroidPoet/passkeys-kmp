#!/bin/bash
# Builds the macOS native passkey backend (Swift + JNI bridge) into
# libPasskeysNative.dylib for both arm64 and x86_64, placed under the JVM
# resources so NativeLibraryLoader can extract + load it at runtime.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT_DIR="${NATIVE_LIBS_OUTPUT_DIR:-$SCRIPT_DIR/../../resources/passkeys/native}"

SWIFT_SOURCE="$SCRIPT_DIR/ApplePasskeyBridge.swift"
JNI_BRIDGE="$SCRIPT_DIR/jni_bridge.c"
LIB_NAME="libPasskeysNative.dylib"

JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home 2>/dev/null || echo '')}"
if [ -z "$JAVA_HOME" ]; then
    echo "ERROR: JAVA_HOME is not set and could not be detected." >&2
    exit 1
fi
JNI_INCLUDES="-I${JAVA_HOME}/include -I${JAVA_HOME}/include/darwin"

ARM64_DIR="$OUTPUT_DIR/darwin-aarch64"
X64_DIR="$OUTPUT_DIR/darwin-x86-64"
mkdir -p "$ARM64_DIR" "$X64_DIR"

build_arch() {
    local ARCH="$1"
    local OUT="$2"
    local TARGET="${ARCH}-apple-macosx12.0"
    local BRIDGE_OBJ="/tmp/passkeys_jni_bridge_${ARCH}.o"

    echo "=== Compiling JNI bridge ($ARCH) ==="
    clang -c -arch "$ARCH" -target "$TARGET" $JNI_INCLUDES "$JNI_BRIDGE" -o "$BRIDGE_OBJ"

    echo "=== Building $LIB_NAME ($ARCH) ==="
    swiftc -emit-library -module-name PasskeysNative \
        -target "$TARGET" \
        -framework AppKit -framework AuthenticationServices \
        -o "$OUT/$LIB_NAME" \
        "$SWIFT_SOURCE" "$BRIDGE_OBJ" \
        -O -whole-module-optimization

    rm -f "$OUT"/PasskeysNative.swift* "$OUT"/PasskeysNative.abi.json "$BRIDGE_OBJ"
}

build_arch "arm64"  "$ARM64_DIR"
build_arch "x86_64" "$X64_DIR"

echo "=== Done ==="
echo "arm64:  $ARM64_DIR/$LIB_NAME"
echo "x86_64: $X64_DIR/$LIB_NAME"
