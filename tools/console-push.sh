#!/bin/sh
# Uploads the NRO to the console's FTP , clears the per-session logs, and with PROF=1 (default)
# drops the "prof" file that turns the runtime sampler on. Usage: tools/console-push.sh <switch-ip:port>
cd "$(dirname "$0")/.."
F=ftp://${1:?switch ip:port}/switch/exiledkingdoms
curl -s --max-time 900 -T work/sw/ExiledKingdoms/build-nx/ExiledKingdoms.nro "$F/exiledkingdoms.nro" || { echo "upload failed (FTP closed?)"; exit 1; }
for f in gc.log ekpad.log prof.log; do curl -s -Q "DELE /switch/exiledkingdoms/$f" "$F/" >/dev/null 2>&1; done
if [ "${PROF:-1}" = 1 ]; then : | curl -s -T - "$F/prof"; else curl -s -Q "DELE /switch/exiledkingdoms/prof" "$F/" >/dev/null 2>&1; fi
curl -s "$F/" | grep -E "nro|prof"
