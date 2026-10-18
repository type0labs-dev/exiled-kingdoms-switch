#!/bin/sh
# G3: work/ek-switch.jar -> C++ project (work/sw/ExiledKingdoms) with Clearwing 3.1.2 + SwitchGDX 3.0.2, mirroring
# SwitchGDX's Gradle "transpile" task: transpile, copy native sources from the jars, game assets -> romfs,
# library resources -> romfs/classpath, the SwitchGDX CMake project template, then rsync for incremental builds.
set -e
cd "$(dirname "$0")/.."
ROOT=$PWD
JAVA=${JDK17:?set JDK17 to a JDK 17 home (the Clearwing transpiler needs it)}/bin/java
SG=lib/switchgdx/switch-gdx-3.0.2.jar
TR=work/clearwing-vm/transpiler/build/libs/transpiler-3.1.2.jar
LIBS="work/gdx-1.13.1.jar lib/1.13.1/gdx-jnigen-loader-2.5.2.jar lib/1.13.1/gdx-box2d-1.13.1.jar
      lib/1.13.1/gdx-controllers-core-2.2.1.jar lib/switchgdx/gdx-freetype-1.13.1.jar lib/switchgdx/regexodus-0.1.15.jar"
L13=$(ls lib/1.13.1/*.jar | sed 's|^lib/1.13.1/gdx-1.13.1.jar$|work/gdx-1.13.1.jar|' | tr '\n' ':')
mkdir -p work/switch-classes work/switch-runtime
# java.nio.file subset Clearwing lacks (compiled by JDK 8's javac, which still allows java.* sources)
javac -nowarn -source 7 -target 7 -d work/switch-runtime $(find switch/runtime -name '*.java') 2>&1 | grep -v bootstrap || true
javac -nowarn -source 8 -target 8 -d work/switch-classes -cp "work/ek-switch.jar:$SG:$L13" switch/src/eksw/*.java 2>&1 | grep -v bootstrap || true
# classes only for the transpiler (the game jar carries 140 MB of assets)
rm -rf work/sw/game-classes && mkdir -p work/sw/game-classes
(cd work/sw/game-classes && unzip -qo "$ROOT/work/ek-switch.jar" '*.class' \
  -x 'net/fdgames/dr/desktop/*' 'com/codedisaster/*' 'c/*' 'com/a/*')  # desktop launcher, Steam, JLayer/JOrbis
# (the jar's obfuscated JSR305 copy stays: ProGuard merged some of it into box2dlights classes)
CPA=lib/asm-9.7.jar:lib/asm-tree-9.7.jar:lib/asm-commons-9.7.jar
javac -nowarn -d work/tools -cp "work/tools:$CPA" tools/deob/SwitchPatch.java 2>&1 | grep -v '^Note' || true
java ${EKSW_TRACE_CATCH:+-Dtrace.catch=true} -cp "work/tools:$CPA" SwitchPatch work/sw/game-classes
D=work/sw/dist
rm -rf $D && mkdir -p $D/src $D/romfs/classpath
"$JAVA" -Xmx6g -jar $TR --input $LIBS $SG work/sw/game-classes work/switch-classes work/switch-runtime --output $D --config switch/switch.json
for j in $LIBS $SG; do (cd $D/src && unzip -qo "$ROOT/$j" '*.cpp' '*.hpp' '*.h' '*.c' -x 'switchgdx/project/*' 2>/dev/null || true); done
# SwitchGDX quits on + (its template's "back to hbmenu"); + is the game's Start/menu button here
python3 - $D/src/switchgdx/SwitchGDX.cpp <<'PY'
import sys
p = sys.argv[1]; s = open(p).read()
old = "    if (kDown & HidNpadButton_Plus)\n        return false;\n"
if old not in s: sys.exit("SwitchGDX.cpp: Plus-quits block not found")
open(p, 'w').write(s.replace(old, "    (void) kDown; // ekpad: + is the menu button, not quit\n"))
PY
for j in $LIBS; do (cd $D/romfs/classpath && unzip -qo "$ROOT/$j" -x '*.class' 'META-INF/*' '*.gwt.xml' '*.cpp' '*.hpp' '*.h' '*.c' 2>/dev/null || true); done
# game assets at the romfs root: Gdx.files.internal("data/...") resolves to romfs:/data/...
(cd $D/romfs && unzip -qo "$ROOT/work/ek-switch.jar" -x '*.class' 'META-INF/*' '*/Thumbs.db' 'Thumbs.db' '*.so' '*.dll')
python3 tools/patch-texts.py switch/texts-pad.tsv $D/romfs/data/ui/strings/texts.txt
# SFX as 16-bit WAV under the same .mp3 names: the game preloads an area's sounds on every transition and SDL_mixer
# decoded each MP3 whole on the Switch's CPU; Mix_LoadWAV sniffs the format from the content (music stays MP3, streamed)
for f in $D/romfs/data/sounds/*.mp3; do
  ffmpeg -nostdin -loglevel error -y -i "$f" -c:a pcm_s16le -f wav "$f.wav" && mv "$f.wav" "$f"
done
(cd $D && unzip -qo "$ROOT/$SG" 'switchgdx/project/*' && cp -r switchgdx/project/. . && rm -rf switchgdx/project)
cp switch/icon.jpg $D/ 2>/dev/null || true
mkdir -p work/sw/ExiledKingdoms
rsync -crh --delete --checksum --exclude '/cmake-build-*' --exclude '/build-*' --exclude '/data' --exclude '/sdmc' --exclude '/ekpad*' --exclude '/init*' $D/ work/sw/ExiledKingdoms/
echo "C++ project: work/sw/ExiledKingdoms ($(find work/sw/ExiledKingdoms/src -name '*.cpp' | wc -l) cpp files, romfs $(du -sh work/sw/ExiledKingdoms/romfs | cut -f1))"
