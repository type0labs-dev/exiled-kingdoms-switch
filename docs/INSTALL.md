# Building, installing and playing

You build the `.nro` from your own copy of the PC game. The output contains the game's assets, so it is for your
own use only: do not share the `.nro`.

## Requirements

**To build** (tested on Arch Linux; any modern Linux should do):

| | Why |
|---|---|
| Exiled Kingdoms for PC (Steam, version 1.3.1210) | the input: `exiledkingdoms.jar` from the game folder |
| JDK 8 as the default `java`/`javac` | the deobfuscation tools, and stand-ins in the `java.*` package (newer javac refuse those) |
| JDK 17 (any directory; pass it as `JDK17`) | building and running the Clearwing transpiler |
| Docker | the `devkitpro/devkita64` image compiles the C++ for the Switch |
| `git`, `curl`, `unzip`, `rsync`, `python3`, ImageMagick (`magick`), `ffmpeg` | fetching, packaging, the icon, sound conversion |

Disk: about 10 GB for the generated C++ project and its two build trees. Time: the first transpile plus the NRO
build take 15–40 minutes, depending on the CPU; later builds are incremental.

**To play:** a Switch running Atmosphère with the homebrew menu (hbmenu), and some way to copy a file to the SD
card (FTP homebrew such as ftpd, MTP, or the card itself).

## Build

```sh
git clone <this repo> exiled-kingdoms-switch && cd exiled-kingdoms-switch
export JDK17=/path/to/jdk-17           # e.g. an extracted Temurin 17

tools/fetch-libs-113.sh                # libGDX 1.13.1, gdx-controllers 2.2.1, SwitchGDX 3.0.2 (from Maven/JitPack)
tools/setup-clearwing.sh               # clones Clearwing at the pinned commit, applies patches/, builds the transpiler
tools/build.sh "/path/to/Exiled Kingdoms/exiledkingdoms.jar"   # deobfuscate + upgrade + controls -> work/ek-switch.jar
tools/transpile.sh                     # Java -> C++ project in work/sw/ExiledKingdoms (+ RomFS with the assets)
tools/build-nro.sh                     # Docker: libffi once, then the NRO
```

`tools/build.sh` defaults to the Steam path `~/.local/share/Steam/steamapps/common/Exiled Kingdoms/exiledkingdoms.jar`
when no argument is given. The deobfuscator prints how many references it could not resolve. 1 is expected, and
harmless: an empty method nothing calls.

The result is `work/sw/ExiledKingdoms/build-nx/ExiledKingdoms.nro` (~200 MB).

After a code change, rerun from the step that changed: Java (`controls/`, `switch/`, `tools/deob/`) →
`build.sh` + `transpile.sh` + `build-nro.sh`; a Clearwing patch → `setup-clearwing.sh` first.

## Install and play

1. Copy the NRO to `sdmc:/switch/exiledkingdoms/exiledkingdoms.nro`.
2. Start the homebrew menu **in title takeover**: hold **R** while launching any installed game. From the album
   (applet mode), homebrew gets a fraction of the memory and the game will not fit.
3. Launch *Exiled Kingdoms*.

The game writes next to the NRO:

| File | |
|---|---|
| `data/data/saves/` | save slots. Same format as the PC version, so you can copy saves between the two |
| `data/config/ekpad.properties` | controller options (button icons, remapping) |
| `init.ini` | display settings; created as 1280×720 fullscreen on first launch |
| `init_log.txt`, `ekpad.log`, `gc.log` | the game's log, the controller layer's log (FPS, slow frames), GC pauses |

Controls are in the [README](../README.md#controls). Options: *Settings → Controller Setup* (the mod's screen)
switches the button icons between Nintendo, Xbox and PlayStation, and moves any of its 12 actions (attack, back,
interact, quick items, the two skill triggers, character, menu, map, journal) to another button. The d-pad layout
is fixed. The name field in character creation opens the Switch keyboard.

## Troubleshooting

- **The game closes right after launching:** an empty or broken `init.ini` used to cause this; the launcher now
  recreates it. If it persists, delete `init.ini` next to the NRO.
- **Crash with an error screen:** Atmosphère writes `atmosphere/crash_reports/<time>_<id>.log`. The addresses
  under *Stack Trace* (`ExiledKingdoms + 0x...`) symbolize against your build:
  ```sh
  docker run --rm -v "$PWD":/w -w /w/work/sw/ExiledKingdoms/build-nx devkitpro/devkita64 \
    /opt/devkitpro/devkitA64/bin/aarch64-none-elf-addr2line -f -C -e ExiledKingdoms 0x<offset> ...
  ```
- **Stutters:** put an empty file named `prof` next to the NRO and play. The runtime then samples what the game is
  doing every millisecond into `prof.log`. Copy `ekpad.log`, `gc.log`, `prof.log` and `init_log.txt` into a folder
  and run `tools/g5-report.py <folder>`. It lists every streak of slow frames with the nearest GC pause and the
  profiler's top methods at that moment. `tools/console-pull.sh <run> <switch-ip:port>` fetches the files over FTP
  and runs the report. A `gc-stats` file next to the NRO adds, to `gc.log`, the classes that create the most
  garbage.

## Development benches (optional)

- **Linux native build** of the same C++ (SwitchGDX desktop mode): `tools/build-native.sh`, `tools/run-native.sh`.
  Needs CMake, Ninja and the dev packages for SDL2, SDL2_mixer, GLEW, zziplib, FreeType, curl and libffi.
  `tools/bench-native.sh` measures instructions per frame with `perf`.
- **Eden emulator:** `EDEN_BIN=/path/to/eden EDEN_KEYS=/dir/with/your/keys tools/run-eden.sh`. Use a nightly
  build; 0.2.1 crashes in its shader recompiler on this game. Keys come from your own console and are never part of
  this repo. `tools/eden-cmd.sh` drives the game from scripts: `btn A`, `stick 1 0 60`, `tap <menu text>`,
  `walkto x y`, `shot label` (in-game screenshot), `waitfor <log text>`. See the comment at the top of
  `controls/src/ekpad/mod/Dev.java`.
- **PC (JVM):** `tools/run-dev.sh` plays `work/ek-switch.jar` with the same controller layer, from the game folder.
