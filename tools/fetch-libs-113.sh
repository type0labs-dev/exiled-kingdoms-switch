#!/bin/sh
# libGDX 1.13.1 + gdx-controllers 2.2.1 (what SwitchGDX 3.0.2 targets) and the desktop bits to test on PC.
set -e
mkdir -p "$(dirname "$0")/../lib/1.13.1" && cd "$(dirname "$0")/../lib/1.13.1"
M=https://repo1.maven.org/maven2; G=$M/com/badlogicgames/gdx; C=$M/com/badlogicgames/gdx-controllers
for u in $G/gdx/1.13.1/gdx-1.13.1.jar $G/gdx-jnigen-loader/2.5.2/gdx-jnigen-loader-2.5.2.jar \
  $G/gdx-backend-lwjgl/1.13.1/gdx-backend-lwjgl-1.13.1.jar $G/gdx-box2d/1.13.1/gdx-box2d-1.13.1.jar \
  $G/gdx-platform/1.13.1/gdx-platform-1.13.1-natives-desktop.jar $G/gdx-box2d-platform/1.13.1/gdx-box2d-platform-1.13.1-natives-desktop.jar \
  $C/gdx-controllers-core/2.2.1/gdx-controllers-core-2.2.1.jar $C/gdx-controllers-desktop/2.2.1/gdx-controllers-desktop-2.2.1.jar \
  $M/com/badlogicgames/jamepad/jamepad/2.0.14.1/jamepad-2.0.14.1.jar
do f=$(basename $u); [ -f "$f" ] || curl -sSfLO "$u"; done
# SwitchGDX 3.0.2 (JitPack; Maven Central for its two dependencies): the backend and the native sources it ships
mkdir -p ../switchgdx && cd ../switchgdx
for u in https://jitpack.io/com/thelogicmaster/switch-gdx/3.0.2/switch-gdx-3.0.2.jar \
  $G/gdx-freetype/1.13.1/gdx-freetype-1.13.1.jar $M/com/github/tommyettinger/regexodus/0.1.15/regexodus-0.1.15.jar
do f=$(basename $u); [ -f "$f" ] || curl -sSfLO "$u"; done
echo "libs 1.13.1 + SwitchGDX ok"
