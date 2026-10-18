#!/bin/sh
# Instructions per frame of a native build in game (load-independent, unlike fps on a hidden workspace):
# loads save slot 1 ("Klaus"), walks back and forth, counts user-space instructions over exactly 600 frames
# (between two "frame N" lines of ekpad.log). Usage: BIN=build-rel/SwitchGDX tools/bench-native.sh
# Needs perf: PERF_BIN (default: the scratch copy unpacked without root) with its libs in PERF_LIBS.
cd "$(dirname "$0")/.."
PB=${PERF_BIN:-perf}
tools/run-native.sh >/dev/null
L=work/sw/ExiledKingdoms/ekpad.log
NATIVE=1 tools/eden-cmd.sh "waitfor Start New Game 180" "tap continue" >/dev/null
NATIVE=1 WAIT=3 tools/eden-cmd.sh "tap Klaus" "tap ok" >/dev/null
NATIVE=1 WAIT=1 tools/eden-cmd.sh "waitfor fps 180" "stick 1 0.5 400" "stick -1 -0.5 400" "stick 1 0.5 400" "stick -1 -0.5 400" >/dev/null &
while ! grep -q "dev stick" $L; do sleep 0.2; done
n=$(grep -c "fps" $L); while [ "$(grep -c fps $L)" -le "$n" ]; do sleep 0.2; done  # a fresh 600-frame window
n=$((n + 1))
LD_LIBRARY_PATH=$PERF_LIBS $PB stat -x, -e instructions:u -p "$(cat /tmp/eknative.pid)" -o /tmp/ekbench.stat &
S=$!
while [ "$(grep -c fps $L)" -le "$n" ]; do sleep 0.05; done
kill -INT $S; wait $S 2>/dev/null
i=$(grep instructions /tmp/ekbench.stat | cut -d, -f1)
echo "$(grep fps $L | tail -1)  ->  $((i / 600)) instructions/frame"
kill "$(cat /tmp/eknative.pid)"
