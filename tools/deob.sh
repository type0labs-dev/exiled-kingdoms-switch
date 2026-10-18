#!/bin/sh
# G0: Exiled Kingdoms jar -> work/ek-std.jar that runs on the official libGDX 1.9.12 (bundled obfuscated copy dropped,
# references renamed). Prints the unresolved count; non-zero means mapping-manual.tsv needs entries.
# Usage: tools/deob.sh [path/to/exiledkingdoms.jar]      then: tools/run-desktop.sh
set -e
cd "$(dirname "$0")/.."
EK=${1:-"$HOME/.local/share/Steam/steamapps/common/Exiled Kingdoms/exiledkingdoms.jar"}
tools/fetch-libs.sh >/dev/null
CP=lib/asm-9.7.jar:lib/asm-tree-9.7.jar:lib/asm-commons-9.7.jar
mkdir -p work/tools
javac -nowarn -d work/tools -cp "$CP" tools/deob/*.java 2>&1 | grep -v '^Note:' || true
STOCK="lib/gdx-1.9.12.jar lib/gdx-backend-lwjgl-1.9.12.jar lib/gdx-box2d-1.9.12.jar lib/gdx-controllers-1.9.12.jar lib/gdx-controllers-desktop-1.9.12.jar"
java -cp "work/tools:$CP" Match "$EK" work/mapping.tsv $STOCK
cat work/mapping.tsv tools/deob/mapping-manual.tsv > work/mapping-all.tsv
java -cp "work/tools:$CP" Remap "$EK" work/mapping-all.tsv work/ek-std.jar $STOCK
