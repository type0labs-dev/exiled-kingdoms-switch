#!/bin/sh
# Feeds dev commands (args, or lines of stdin) to the game in Eden, one at a time: appends each to ekpad-cmd, waits
# until the game's ekpad-ack count passes it (up to 60 s), then WAIT seconds (default 3). Host-side extras:
#   waitfor <text> [seconds]   block until ekpad.log/init_log.txt gains a line containing text (default 120 s)
#   shot <label>               also converts the game's PPM to work/shots/<label>.png and prints its path
cd "$(dirname "$0")/.."
# NATIVE=1: the Linux build (tools/run-native.sh): channel in /tmp, logs in the C++ project dir
if [ "$NATIVE" = 1 ]; then R=work/sw/ExiledKingdoms; X=/tmp; else R=work/eden-profile/data/eden/sdmc; X=$R; fi
C=$X/ekpad-cmd
logs() { cat $R/ekpad.log $R/init_log.txt 2>/dev/null; }
send() {
  case "$1" in
    waitfor\ *)
      t=$(echo "$1" | cut -d' ' -f2- | sed 's/ [0-9]*$//'); s=$(echo "$1" | grep -oE ' [0-9]+$' | tr -d ' ')
      n0=$(logs | grep -cF "$t"); i=0
      while [ "$(logs | grep -cF "$t")" -le "$n0" ] && [ $i -lt "${s:-120}" ]; do sleep 1; i=$((i + 1)); done
      [ $i -lt "${s:-120}" ] && echo "waitfor: $t (${i}s)" || echo "waitfor: TIMEOUT $t"
      return ;;
  esac
  n=$(wc -l < $C)
  printf "%s\n" "$1" >> $C  # the game ignores an unfinished last line
  i=0; while [ "$(cat $X/ekpad-ack 2>/dev/null || echo 0)" -le "$n" ] 2>/dev/null && [ $i -lt 200 ]; do sleep 0.3; i=$((i + 1)); done
  [ $i -ge 200 ] && echo "eden-cmd: not taken in 60 s: $1"
  case "$1" in
    shot\ *)
      l=${1#shot }; p=$X/ekpad-shot-$l.ppm; mkdir -p work/shots
      i=0; while ! grep -q "dev shot ekpad-shot-$l.ppm" $R/ekpad.log 2>/dev/null && [ $i -lt 60 ]; do sleep 0.5; i=$((i + 1)); done
      magick "$p" "work/shots/$l.png" && rm -f "$p" && echo "work/shots/$l.png"
      return ;;
  esac
  sleep ${WAIT:-3}
}
if [ $# -gt 0 ]; then for c in "$@"; do send "$c"; done
else while read -r c; do send "$c"; done; fi
