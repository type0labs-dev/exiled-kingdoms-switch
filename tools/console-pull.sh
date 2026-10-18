#!/bin/sh
# Fetches the game's logs from the console's FTP  into work/g5/<run>, then prints the report.
# Usage: tools/console-pull.sh <run> <switch-ip:port>   (the FTP homebrew shows it, e.g. ftpd on port 5000)
cd "$(dirname "$0")/.."
F=ftp://${2:?switch ip:port}/switch/exiledkingdoms
D=work/g5/$1
mkdir -p "$D"
for f in ekpad.log gc.log prof.log init_log.txt init.ini; do curl -s --max-time 30 -o "$D/$f" "$F/$f"; done
curl -s --max-time 10 "ftp://${2:?switch ip:port}/atmosphere/crash_reports/" | tail -3
tools/g5-report.py "$D"
