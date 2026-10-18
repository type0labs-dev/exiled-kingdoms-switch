#!/bin/sh
# Full pipeline up to the jar the Switch build will transpile:
#   exiledkingdoms.jar -> deob (libGDX 1.9.12 names) -> 1.13.1 compat -> built-in controls -> work/ek-switch.jar
set -e
cd "$(dirname "$0")/.."
[ -f work/ek-std.jar ] || tools/deob.sh "$@"
tools/fetch-libs-113.sh >/dev/null
CP=lib/asm-9.7.jar:lib/asm-tree-9.7.jar:lib/asm-commons-9.7.jar
javac -nowarn -d work/tools -cp "work/tools:$CP" tools/deob/*.java 2>&1 | grep -v '^Note:' || true
# gdx with the game's wrap rule (see GdxPatch); every classpath below uses work/gdx-1.13.1.jar instead of lib's
java -cp "work/tools:$CP" GdxPatch lib/1.13.1/gdx-1.13.1.jar work/gdx-1.13.1.jar
# GdxPatch's helpers (same packages as the classes they serve), compiled against the patched jar and added to it
rm -rf work/gdxpatch && mkdir -p work/gdxpatch
javac -nowarn -source 8 -target 8 -d work/gdxpatch -cp work/gdx-1.13.1.jar $(find gdxpatch/src -name '*.java') 2>&1 | grep -v '^warning\|^Note' || true
(cd work/gdxpatch && jar uf ../gdx-1.13.1.jar $(find . -name '*.class'))
L13=$(ls lib/1.13.1/*.jar | sed 's|^lib/1.13.1/gdx-1.13.1.jar$|work/gdx-1.13.1.jar|' | tr '\n' ':')
jc() { javac -nowarn -source 7 -target 7 -encoding UTF-8 "$@" 2>work/javac.log || { grep -v bootstrap work/javac.log; exit 1; }; }
rm -rf work/compat work/controls && mkdir -p work/compat work/controls
jc -d work/compat -cp "$L13" $(find compat/src -name '*.java')
jc -d work/controls -cp "work/ek-std.jar:work/compat:$L13" $(find controls/src -name '*.java')
java -cp "work/tools:$CP" Patch work/ek-std.jar work/ek-switch.jar work/compat work/controls
java -cp "work/tools:$CP" Link work/ek-switch.jar work/gdx-1.13.1.jar lib/1.13.1/gdx-box2d-1.13.1.jar \
  lib/1.13.1/gdx-backend-lwjgl-1.13.1.jar lib/1.13.1/gdx-controllers-core-2.2.1.jar | tail -1
