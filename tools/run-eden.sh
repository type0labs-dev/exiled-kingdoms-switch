#!/bin/sh
# Boots the NRO in an Eden nightly Qt build (0.2.1's shader recompiler segfaults on this game) with an isolated
# profile. EDEN_BIN = the eden binary, EDEN_KEYS = a directory with your own prod.keys/title.keys (dumped from your
# console; never shipped). It runs through a symlink named eden-ek, so it can coexist with other Eden instances and
# be killed by its own path. On Hyprland, EDEN_WORKSPACE moves the window to that workspace (dev runs on a hidden one).
# The NRO sits at sdmc:/switch/exiledkingdoms/, but Eden starts it with the SD root as working dir (hbmenu on the
# console uses the NRO's folder), so init.ini, data/ (saves) and ekpad.log land in work/eden-profile/data/eden/sdmc;
# DEV=1 drops ekpad-dev there so appending to "ekpad-cmd" next to it drives the game (tools/eden-cmd.sh).
# PID in /tmp/ekeden.pid, emulator log work/eden-profile/eden.log.
set -e
cd "$(dirname "$0")/.."
CLI=$PWD/work/eden-ek
ln -sf "${EDEN_BIN:?set EDEN_BIN to the eden binary}" "$CLI"
P=$PWD/work/eden-profile
SD=$P/data/eden/sdmc/switch/exiledkingdoms
PIDF=/tmp/ekeden.pid
# SIGTERM only opens Eden's "are you sure?" dialog: every earlier instance of this bench gets SIGKILL (by PID, own path)
for p in $(pgrep -x eden-ek); do ps -o args= -p $p | grep -q "$PWD/work/eden-ek" && kill -9 $p; done; sleep 1
mkdir -p "$SD" $P/config $P/cache
# the Qt GUI stops on a "missing keys" dialog without them
K=${EDEN_KEYS:?set EDEN_KEYS to a directory with your prod.keys and title.keys}
mkdir -p $P/data/eden/keys && cp "$K/prod.keys" "$K/title.keys" $P/data/eden/keys/
cp work/sw/ExiledKingdoms/build-nx/*.nro "$SD/exiledkingdoms.nro"
# FIFO present blocks while the window sits on a hidden workspace (the game froze mid-walk): Immediate
Q=$P/config/eden/qt-config.ini
[ -f $Q ] && sed -i -e 's/^use_vsync=.*/use_vsync=0/' -e 's/^use_vsync\\default=.*/use_vsync\\default=false/' $Q
R=$P/data/eden/sdmc
rm -f "$R/ekpad.log" "$R/gc.log" "$R"/ekpad-shot-*
: > "$R/ekpad-cmd"; : > "$R/ekpad-ack"  # the dev channel is append-only: both must exist at boot (see Dev.java)
if [ "${DEV:-1}" = 1 ]; then touch "$R/ekpad-dev"; else rm -f "$R/ekpad-dev"; fi
XDG_DATA_HOME=$P/data XDG_CONFIG_HOME=$P/config XDG_CACHE_HOME=$P/cache QT_QPA_PLATFORM=xcb \
  nohup "$CLI" -g "$SD/exiledkingdoms.nro" > $P/eden.log 2>&1 &
echo $! > $PIDF
tools/hypr-move.sh "$(cat $PIDF)" "$EDEN_WORKSPACE"
echo "eden pid $(cat $PIDF) sd $SD"
