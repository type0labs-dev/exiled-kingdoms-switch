#!/usr/bin/env python3
"""Summary of a console (or Eden) session from the game's logs in one directory (tools/console-pull.sh):
ekpad.log ("slow frame N ms at <epoch ms> [in <window>]"), gc.log, prof.log (runtime sampler), init_log.txt.

  tools/g5-report.py work/g5/run8 [--prof N]   (N: inclusive entries per profiler window, default 12)

Prints totals, load/transition times, and each slow-frame streak with the GC just before it and the top inclusive
methods of the overlapping 10 s profiler windows (frames over 5 s are loads, listed apart).
"""
import os, re, sys
from datetime import datetime

d = sys.argv[1]
nprof = int(sys.argv[sys.argv.index('--prof') + 1]) if '--prof' in sys.argv else 12
read = lambda f: open(os.path.join(d, f), encoding='utf-8', errors='replace').read() if os.path.exists(os.path.join(d, f)) else ''
short = lambda s: s.replace('net/fdgames/', '').replace('com/badlogic/gdx/', 'gdx/').replace('net.fdgames.UI.', '')
SKIP = ('eksw/', 'SwitchApplication', 'ExiledKingdoms:render', 'Game:render')

slow = []
for m in re.finditer(r'slow frame (\d+) ms at (\d+)(?: in (\S+))?', read('ekpad.log')):
    slow.append((int(m.group(2)), int(m.group(1)), short(m.group(3) or 'world')))
gcs = []
for l in read('gc.log').splitlines():
    m = re.match(r'(\d+) pause (\d+) ms .* live (\d+) queued (\d+) heap (\d+) MB', l)
    if m: gcs.append(tuple(int(x) for x in m.groups()))
wins = []
for blk in read('prof.log').split('== ')[1:]:
    head, *rest = blk.split('\n')
    end, n = int(head.split(':')[0]), int(head.split(':')[1].split()[0])
    inc, mode = [], 'self'
    for l in rest:
        l = l.strip()
        if l in ('inclusive:', 'callers:'): mode = l[:-1]; continue
        if l and mode == 'inclusive' and not any(k in l for k in SKIP): inc.append(short(l))
    wins.append((end, n, inc))

if not slow:
    sys.exit('no "slow frame ... at" lines in ' + d)
t0 = slow[0][0] - slow[0][1]
rel = lambda t: f'+{(t - t0) / 1000:5.0f}s'
loads = [s for s in slow if s[1] > 5000]
frames = [s for s in slow if s[1] <= 5000]
streaks = []
for t, ms, where in frames:
    if streaks and t - streaks[-1]['end'] < 1500:
        s = streaks[-1]; s['end'] = t; s['n'] += 1; s['ms'] += ms; s['where'].add(where)
    else:
        streaks.append({'start': t - ms, 'end': t, 'n': 1, 'ms': ms, 'where': {where}})

fps = re.findall(r'frame \d+ fps (\d+) worst (\d+) ms', read('ekpad.log'))
print(f'== {d}  ({datetime.fromtimestamp(t0 / 1000):%Y-%m-%d %H:%M}, {len(fps) * 10} s of fps lines)')
print(f'slow frames (150 ms - 5 s): {len(frames)}, {sum(s[1] for s in frames) / 1000:.0f} s lost; '
      f'streaks >= 3 frames: {sum(1 for s in streaks if s["n"] >= 3)}, longest '
      f'{max(((s["end"] - s["start"]) / 1000 for s in streaks), default=0):.1f} s')
print(f'GCs: {len(gcs)}, pauses {min((g[1] for g in gcs), default=0)}-{max((g[1] for g in gcs), default=0)} ms, '
      f'queued up to {max((g[3] for g in gcs), default=0)}')
print('\n-- loads (frames > 5 s)')
for t, ms, where in loads:
    print(f'{rel(t - ms)}  {ms / 1000:5.1f} s  {where}')

def windows(a, b):
    return [w for w in wins if w[0] - 10000 <= b and w[0] >= a]

print('\n-- streaks (>= 3 slow frames)')
for s in streaks:
    if s['n'] < 3: continue
    gc = [g for g in gcs if -20000 < g[0] - s['start'] < 2000]
    gtxt = ', '.join(f'GC {g[1]} ms queued {g[3]} at {(g[0] - s["start"]) / 1000:+.0f}s' for g in gc) or 'no GC near'
    print(f'{rel(s["start"])}  {(s["end"] - s["start"]) / 1000:5.1f} s  {s["n"]:3d} frames  {s["ms"]:6d} ms  '
          f'{",".join(sorted(s["where"]))[:30]}  [{gtxt}]')
    for end, n, inc in windows(s['start'], s['end']):
        print(f'      prof {rel(end)} ({n} samples): ' + ' / '.join(inc[:nprof]))
if '--loads' in sys.argv:
    print('\n-- profiler during loads')
    for t, ms, where in loads:
        for end, n, inc in windows(t - ms, t):
            print(f'{rel(t - ms)} {where}: prof {rel(end)} ({n} samples): ' + ' / '.join(inc[:nprof]))
