# Exiled Kingdoms for Nintendo Switch (homebrew)

An unofficial, **native** Nintendo Switch homebrew build of the PC version of
[Exiled Kingdoms](https://store.steampowered.com/app/788270/Exiled_Kingdoms/), the old-school action RPG by
FDG Entertainment. It runs fully offline as an `.nro` from the homebrew menu, with full controller support: no mouse,
no cursor, every screen driven by focus navigation and button prompts.

> **No game files here.** This repository contains build tooling, patches and new code only. You build the `.nro`
> yourself from **your own copy** of the PC game (`exiledkingdoms.jar` from Steam). Please buy the game; the
> developers deserve it. This project is not affiliated with or endorsed by FDG Entertainment.

## How it works

The PC game is Java on libGDX. The Switch has no JVM, so the build runs a pipeline of AOT translation:

1. **Deobfuscate**: the game ships its own ProGuard-obfuscated copy of libGDX. `tools/deob` matches every class
   and member against the official libGDX 1.9.12 jar, rewrites the game to the real names and drops the bundled copy.
2. **Upgrade**: the game is moved to libGDX 1.13.1, the version
   [SwitchGDX](https://github.com/SwitchGDX/switch-gdx) targets, with bytecode patches for the API drift.
3. **Add controls**: a cursor-free controller layer (focus navigation, hint bar, remapping) is built into the game.
4. **Transpile**: [Clearwing](https://github.com/SwitchGDX/clearwing-vm) turns the bytecode into C++, with the
   runtime fixes in `patches/`.
5. **Compile**: devkitPro builds the C++ against SwitchGDX into an `.nro`, with the game's assets in its RomFS.

The full story (what broke, how it was found, what fixed it) is in **[docs/POSTMORTEM.md](docs/POSTMORTEM.md)**.

## Status

Playable on a real console. Tested there: new game, character creation, combat, loot, conversations, area
transitions, saving and loading, sessions of 25+ minutes. Measured on hardware after the performance work:

| | |
|---|---|
| Frame rate | 60 fps in play; short hitches remain at some transitions |
| Boot to main menu | ~16 s |
| Save load / area transition | 5–8 s |
| Save files | compatible with the PC version (same JSON format) |

Known gaps: no rumble (SwitchGDX has no vibration API); the d-pad cannot be remapped; the first area load after
boot is slower than the rest.

## Building and installing

See **[docs/INSTALL.md](docs/INSTALL.md)**. In short, on Linux with Docker and JDK 8 + 17:

```sh
tools/fetch-libs-113.sh
JDK17=/path/to/jdk-17 tools/setup-clearwing.sh
tools/build.sh /path/to/exiledkingdoms.jar
JDK17=/path/to/jdk-17 tools/transpile.sh
tools/build-nro.sh
# copy work/sw/ExiledKingdoms/build-nx/ExiledKingdoms.nro to sdmc:/switch/exiledkingdoms/exiledkingdoms.nro
```

Launch it from the homebrew menu **in title takeover** (hold R while starting a game); applet mode, from the album,
does not have enough memory.

## Controls

Default layout (Nintendo labels; Xbox and PlayStation icons and full remapping are in
*Settings → Controller Setup*):

| In the world | |
|---|---|
| Left stick | Move |
| A | Attack the nearest enemy in range (hold to keep attacking) |
| X | Interact (talk, open, pick up) |
| Y | Use the selected quick item |
| D-pad ↑ | Recover (the green cross: full heal, limited charges) |
| D-pad ↓ | Use the selected quick item |
| D-pad ← / → | Previous / next quick item (also L / R) |
| L + R | Recover |
| ZL + A/B/X/Y | Skills 1–4 (holding ZL lists them by name) |
| ZR + A/B/X/Y | Skills 5–8 |
| − | Character sheet (hold: the full control list) |
| + | Game menu (save, load, settings) |
| L3 / R3 | Map / journal |

| In menus | |
|---|---|
| Stick / d-pad | Move the focus (gold frame) |
| A | Choose; on an item slot: select, then A again for the main action (take, equip, use, buy) |
| X | The secondary action of a selected slot (drop, sell, store…) |
| Y | Take all, in a loot window |
| B | Back / close |

The touch screen keeps working everywhere. A hint bar at the bottom always shows what the buttons do on the
current screen.

## Repository layout

| Path | What |
|---|---|
| `tools/deob/` | Matcher, remapper, link checker, bytecode patchers (`Patch`, `SwitchPatch`, `GdxPatch`) |
| `controls/` | The built-in controller layer (`ekpad`) |
| `compat/`, `gdxpatch/`, `switch/` | libGDX 1.9 → 1.13 shims, libGDX fixes, Switch launcher and runtime stand-ins |
| `patches/` | Clearwing patches (runtime bugs and Switch performance) |
| `tests/` | Small runnable checks (`SplitCheck`, `JsonQuoteCheck`) |
| `tools/*.sh` | Build steps, Eden/native test benches, console FTP helpers, `g5-report.py` |
| `docs/` | Install guide, post-mortem, development log |

## Credits and license

- **Exiled Kingdoms** © FDG Entertainment. Not included and not redistributed.
- [libGDX](https://libgdx.com) (Apache 2.0), [SwitchGDX](https://github.com/SwitchGDX/switch-gdx) and
  [Clearwing](https://github.com/SwitchGDX/clearwing-vm) by TheLogicMaster, [devkitPro](https://devkitpro.org).
- The code in this repository is MIT licensed ([LICENSE](LICENSE)), except `patches/clearwing-*.patch`, which
  modify Clearwing and are under its license (GPLv2). See [NOTICE](NOTICE).
