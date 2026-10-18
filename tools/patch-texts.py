#!/usr/bin/env python3
"""Puts the controller wording of switch/texts-pad.tsv (tag, EN, PT) over the desktop tutorial tips in a romfs's
data/ui/strings/texts.txt (mouse/TAB/SPACEBAR/F9). Other languages keep the desktop text.
Usage: patch-texts.py <switch/texts-pad.tsv> <romfs>/data/ui/strings/texts.txt"""
import sys

pad = {}
for line in open(sys.argv[1], encoding='utf-8').read().splitlines()[1:]:
    tag, en, pt = line.split('\t')
    pad[tag] = (en, pt)
path = sys.argv[2]
raw = open(path, 'rb').read()
bom = raw.startswith(b'\xef\xbb\xbf')
lines = raw.decode('utf-8-sig').split('\r\n')
cols = lines[0].split('\t')
ien, ipt = cols.index('EN'), cols.index('PT')
done = set()
for i, l in enumerate(lines):
    f = l.split('\t')
    if f[0] in pad:
        f[ien], f[ipt] = pad[f[0]]
        lines[i] = '\t'.join(f)
        done.add(f[0])
missing = set(pad) - done
if missing:
    sys.exit('texts.txt lacks ' + ', '.join(sorted(missing)))
open(path, 'wb').write((b'\xef\xbb\xbf' if bom else b'') + '\r\n'.join(lines).encode('utf-8'))
print(f'texts.txt: {len(done)} tips reworded for the controller')
