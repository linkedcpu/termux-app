#!/bin/bash
set -e

# ===== 自动获取脚本所在目录（即 app/）=====
SCRIPT_PATH="$(realpath "$0")"
SCRIPT_DIR="$(dirname "$SCRIPT_PATH")"

# ===== bergamot-translator 源码路径（基于脚本位置，换机器不用改）=====
BERGAMOT_SRC="$SCRIPT_DIR/app/src/main/cpp/bergamot-translator"
BUILD_DIR="$BERGAMOT_SRC/build"

TERMUX_DIR="$PREFIX"
TERMUX_TARGET="/data/data/com.termux/files/usr"
MAKE="$TERMUX_DIR/bin/make"

mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

# ===== cmake 直接指向源码目录，不再用 ".." =====
cmake "$BERGAMOT_SRC" \
  -G "Unix Makefiles" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_MAKE_PROGRAM="$MAKE" \
  -DCMAKE_C_COMPILER="$TERMUX_DIR/bin/clang" \
  -DCMAKE_CXX_COMPILER="$TERMUX_DIR/bin/clang++" \
  -DCMAKE_CXX_FLAGS="-fPIC -Wno-error=constant-evaluated -Wno-error=unused-command-line-argument" \
  -DCMAKE_EXE_LINKER_FLAGS="-L$TERMUX_TARGET/lib -Wl,-rpath,$TERMUX_TARGET/lib -liconv" \
  -DUSE_RUY_SGEMM=ON \
  -DBLAS=OFF \
  -DCOMPILE_PYTHON=OFF \
  -DCOMPILE_TESTS=OFF \
  -DBUILD_ARCH=armv8-a \
  -DCMAKE_POLICY_VERSION_MINIMUM=3.5

"$MAKE" -j$(nproc)

# ===== 哈希：binary 在 BUILD_DIR 里（cmake build 产物）=====
if [ -f "$BUILD_DIR/bergamot" ]; then
  sha256sum "$BUILD_DIR/bergamot" | awk '{print $1}' > "$BUILD_DIR/bergamot.sha256"
  echo "✅ Hash: $(cat "$BUILD_DIR/bergamot.sha256")"
else
  echo "⚠️  bergamot binary not found in $BUILD_DIR, skipping hash"
fi

echo "✅ Done! rpath → $TERMUX_TARGET/lib"
echo "✅ Build done! → $BUILD_DIR"
