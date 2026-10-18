#!/bin/sh
# Moves the window of process $1 to Hyprland workspace $2 (dev runs on a hidden workspace). No-op without $2 or
# without hyprctl. Waits up to 30 s for the window to appear.
[ -n "$2" ] && command -v hyprctl >/dev/null || exit 0
for i in $(seq 1 60); do
  a=$(hyprctl clients -j | python3 -c "import json,sys;p=int(sys.argv[1]);[print(c['address']) for c in json.load(sys.stdin) if c['pid']==p]" "$1" | head -1)
  [ -n "$a" ] && break; sleep 0.5
done
[ -n "$a" ] && hyprctl dispatch "hl.dsp.window.move({ workspace='$2', follow=false, window='address:$a' })" >/dev/null
