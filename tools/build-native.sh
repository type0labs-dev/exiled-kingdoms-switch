#!/bin/sh
# Linux build of the transpiled project (SwitchGDX desktop mode). Reconfigures every time: CMake globs the sources,
# so classes new to the transpile (new .cpp files) are missed otherwise.
set -e
cd "$(dirname "$0")/../work/sw/ExiledKingdoms"
cmake -DCMAKE_BUILD_TYPE=Debug -DCMAKE_POLICY_VERSION_MINIMUM=3.5 -S . -B build-run -G Ninja > ../cmake-config.log 2>&1
nice -n 15 cmake --build build-run -j${JOBS:-6} 2>&1 | grep -E 'error|warning: unused|Linking|FAILED' | grep -v 'warning' | tail -5
ls -la build-run/SwitchGDX
