#!/bin/sh
# Runs the Clearwing-built Linux binary (SwitchGDX desktop mode) from the C++ project dir (romfs/ next to it);
# EK_WORKSPACE moves its window to that Hyprland workspace. Output /tmp/eknative.out, PID /tmp/eknative.pid;
# ekpad.log, init_log.txt, gc.log in that dir.
# BIN=build-rel/SwitchGDX runs the RelWithDebInfo build (profiling); PERF="perf record ..." prefixes the command.
# Dev channel (tools/eden-cmd.sh with NATIVE=1): /tmp/ekpad-cmd + /tmp/ekpad-ack, created here.
cd "$(dirname "$0")/.."; T=$PWD/tools
cd work/sw/ExiledKingdoms
PIDF=/tmp/eknative.pid
if [ -f $PIDF ] && ps -o cmd= -p "$(cat $PIDF)" 2>/dev/null | grep -q SwitchGDX; then kill "$(cat $PIDF)"; sleep 1; fi
# hidden workspace + vsync stalls the swap (same as the LWJGL runs): uncapped for dev
rm -f ekpad.log gc.log /tmp/ekpad-shot-*; : > /tmp/ekpad-cmd; : > /tmp/ekpad-ack; touch ekpad-dev
vblank_mode=0 nohup $PERF ./${BIN:-build-run/SwitchGDX} > /tmp/eknative.out 2>&1 &
echo $! > $PIDF
$T/hypr-move.sh "$(cat $PIDF)" "$EK_WORKSPACE"
echo "native pid $(cat $PIDF)"
