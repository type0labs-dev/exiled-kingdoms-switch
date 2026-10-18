#!/bin/sh
# Runs work/ek-std.jar on the official libGDX 1.9.12 + LWJGL 2.9.3, from the game folder (saves, config).
cd "$(dirname "$0")/.."
CP="$PWD/work/ek-std.jar:$(ls "$PWD"/lib/*.jar | grep -v asm | tr '\n' ':')"
cd "${EK_DIR:-$HOME/.local/share/Steam/steamapps/common/Exiled Kingdoms}"
SteamAppId=788270 exec java -Xmx1G -cp "$CP" net.fdgames.dr.desktop.Main
