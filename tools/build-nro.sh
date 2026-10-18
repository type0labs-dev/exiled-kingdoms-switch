#!/bin/sh
# Switch NRO of the transpiled project, in the devkitpro/devkita64 Docker image -> work/sw/ExiledKingdoms/build-nx/.
# libffi is not a devkitPro portlib: an aarch64 static build of the 3.4.6 release is made once into
# work/switch-deps and copied into the container's portlibs.
set -e
cd "$(dirname "$0")/.."
ROOT=$PWD
if [ ! -f work/switch-deps/lib/libffi.a ]; then
  mkdir -p work
  [ -d work/libffi-3.4.6 ] || curl -sL https://github.com/libffi/libffi/releases/download/v3.4.6/libffi-3.4.6.tar.gz | tar xz -C work
  docker run --rm -v "$ROOT":/app -w /app devkitpro/devkita64:latest sh -c '
    . /opt/devkitpro/switchvars.sh && mkdir -p work/ffi-build && cd work/ffi-build &&
    ../libffi-3.4.6/configure --host=aarch64-none-elf --prefix=/app/work/switch-deps --disable-shared \
      --enable-static --disable-docs --disable-exec-static-tramp && make -j4 && make install &&
    chown -R '"$(id -u):$(id -g)"' /app/work/ffi-build /app/work/switch-deps'
fi
P=work/sw/ExiledKingdoms
# devkitPro's freetype is built with harfbuzz (newer than the SwitchGDX template's link line)
grep -q lharfbuzz $P/switch.cmake || sed -i 's/-lfreetype/-lfreetype -lharfbuzz -lfreetype/' $P/switch.cmake
[ -f $P/icon.jpg ] || magick $P/romfs/data/ui/icon_128.png -filter point -resize 256x256 -background black -flatten $P/icon.jpg
docker run --rm -v "$ROOT":/app -w /app/$P \
  -e APP_TITLE="Exiled Kingdoms" -e APP_AUTHOR="FDG Entertainment (unofficial port)" -e APP_VERSION="1.3.1210" \
  devkitpro/devkita64:latest sh -c '
    cp /app/work/switch-deps/lib/libffi.a /opt/devkitpro/portlibs/switch/lib/
    cp /app/work/switch-deps/include/ffi*.h /opt/devkitpro/portlibs/switch/include/
    cmake -S . -B build-nx -G Ninja -DCMAKE_TOOLCHAIN_FILE=DevkitA64Libnx.cmake -DCMAKE_BUILD_TYPE=Release \
      -DCMAKE_POLICY_VERSION_MINIMUM=3.5 > build-nx-config.log 2>&1 || { tail -20 build-nx-config.log; exit 1; }
    nice -n 15 cmake --build build-nx -j'"${JOBS:-6}"' 2>&1 | grep -E "error|Linking|FAILED|Built" | tail -8
    chown -R '"$(id -u):$(id -g)"' build-nx build-nx-config.log'
ls -la $P/build-nx/*.nro
