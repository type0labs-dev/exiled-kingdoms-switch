#!/bin/sh
# Official jars the deobfuscation is matched against (libGDX 1.9.12 = the version bundled in Exiled Kingdoms)
# and the desktop runtime used to test the result on PC.
set -e
cd "$(dirname "$0")/../lib" 2>/dev/null || { mkdir -p "$(dirname "$0")/../lib"; cd "$(dirname "$0")/../lib"; }
M=https://repo1.maven.org/maven2
G=com/badlogicgames/gdx
for a in \
  $G/gdx/1.9.12/gdx-1.9.12.jar $G/gdx-backend-lwjgl/1.9.12/gdx-backend-lwjgl-1.9.12.jar \
  $G/gdx-box2d/1.9.12/gdx-box2d-1.9.12.jar $G/gdx-controllers/1.9.12/gdx-controllers-1.9.12.jar \
  $G/gdx-controllers-desktop/1.9.12/gdx-controllers-desktop-1.9.12.jar \
  $G/gdx-platform/1.9.12/gdx-platform-1.9.12-natives-desktop.jar \
  $G/gdx-box2d-platform/1.9.12/gdx-box2d-platform-1.9.12-natives-desktop.jar \
  $G/gdx-controllers-platform/1.9.12/gdx-controllers-platform-1.9.12-natives-desktop.jar \
  org/lwjgl/lwjgl/lwjgl/2.9.3/lwjgl-2.9.3.jar org/lwjgl/lwjgl/lwjgl_util/2.9.3/lwjgl_util-2.9.3.jar \
  org/lwjgl/lwjgl/lwjgl-platform/2.9.3/lwjgl-platform-2.9.3-natives-linux.jar \
  net/java/jinput/jinput/2.0.5/jinput-2.0.5.jar net/java/jinput/jinput-platform/2.0.5/jinput-platform-2.0.5-natives-linux.jar \
  com/badlogicgames/jlayer/jlayer/1.0.1-gdx/jlayer-1.0.1-gdx.jar org/jcraft/jorbis/0.0.17/jorbis-0.0.17.jar \
  org/ow2/asm/asm/9.7/asm-9.7.jar org/ow2/asm/asm-commons/9.7/asm-commons-9.7.jar org/ow2/asm/asm-tree/9.7/asm-tree-9.7.jar
do f=$(basename $a); [ -f "$f" ] || curl -sSfLO "$M/$a"; done
echo "libs ok"
