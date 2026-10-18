#!/bin/sh
# Plays work/ek-switch.jar on PC (libGDX 1.13.1, LWJGL2 backend, SDL pads) from the game folder (EK_DIR, default the
# Steam one), with the controls' dev channel (/tmp/ekpad-cmd). EK_WORKSPACE: Hyprland workspace. Log: /tmp/eksw.log
cd "$(dirname "$0")/.."
L=$PWD/lib
CP="$PWD/work/ek-switch.jar:$PWD/work/gdx-1.13.1.jar:$(ls "$L"/1.13.1/*.jar | grep -v /gdx-1.13.1.jar | tr '\n' ':')$L/lwjgl-2.9.3.jar:$L/lwjgl_util-2.9.3.jar:$L/lwjgl-platform-2.9.3-natives-linux.jar:$L/jinput-2.0.5.jar:$L/jinput-platform-2.0.5-natives-linux.jar:$L/jlayer-1.0.1-gdx.jar:$L/jorbis-0.0.17.jar"
PIDF=/tmp/eksw-game.pid
if [ -f $PIDF ] && ps -o cmd= -p "$(cat $PIDF)" 2>/dev/null | grep -q net.fdgames; then
  old=$(cat $PIDF); kill "$old"; for i in $(seq 1 20); do ps -p $old >/dev/null || break; sleep 0.5; done
fi
GAME="${EK_DIR:-$HOME/.local/share/Steam/steamapps/common/Exiled Kingdoms}"
[ -f "$GAME/steam_appid.txt" ] || echo 788270 > "$GAME/steam_appid.txt"
: > /tmp/ekpad-cmd; : > /tmp/ekpad-ack  # the dev channel is append-only (see Dev.java)
export vblank_mode=0 EKPAD_DEV=1 EKPAD_LOG=/tmp/eksw.log SteamAppId=788270
(cd "$GAME" && exec java -Xmx1G -cp "$CP" net.fdgames.dr.desktop.Main > /tmp/eksw-game.out 2>&1) &
echo $! > $PIDF
tools/hypr-move.sh "$(cat $PIDF)" "$EK_WORKSPACE"
echo "game pid $(cat $PIDF)"
