# Development log (gates G0–G5)

The project moved through gates, each with a pass criterion. This is the log as it was kept. The post-mortem
([POSTMORTEM.md](POSTMORTEM.md)) is the distilled version.

G0 deobfuscate → G1 libGDX 1.13.1 → G2 controls built in → G3 transpile, Linux native → G4 NRO in Eden → G5 console.

## G0 — deobfuscation
Exiled Kingdoms bundles **libGDX 1.9.12** (it reports 1.9.11; the class set matches 1.9.12 exactly), with members
renamed by ProGuard and class names kept. `tools/deob/`:
- `Match` pairs each bundled class with the official one. It uses surviving names, unique descriptors, constant
  values, identical bodies, votes from aligned bodies of matched pairs (cross-class), and declaration order for
  abstract methods. Result: 2541 methods + 1721 fields renamed.
- `mapping-manual.tsv`: 23 members the game uses that needed judgement. Each was decided from bytecode behaviour,
  with `Suggest` ranking the candidates.
- `Remap` drops the bundled libGDX/LWJGL/jinput and rewrites the game (+ box2dlights, decoders, gdx-pay) to real
  names, walking the original hierarchy so overrides follow (e.g. `Screen.a()` → `show()`).
  Unresolved: 1, `BatchTiledMapRenderer.b()`, an empty FDGames-only method nothing calls through libGDX (harmless).

Result: `work/ek-std.jar` runs on the **official** libGDX 1.9.12 + LWJGL 2.9.3, with no exceptions: main menu, save
list, loading a save (reflection-based Json), world rendering with box2dlights.

    tools/deob.sh            # fetches official jars, builds work/ek-std.jar, prints the unresolved count
    tools/run-desktop.sh     # plays it from the game folder

## G1 — libGDX 1.13.1
`Link` (static link check: missing classes/members, unimplemented new abstract methods) found 4 problems against
1.13.1 + gdx-controllers 2.2.1. `Patch` fixes them:
- `Input.setCatchBackKey` and `Controller.getPov` are redirected to `compat/src/ekcompat/Compat` (the d-pad comes
  from `ControllerMapping`);
- a `PovDirection` shim;
- `touchCancelled` added to the game's InputProcessor.

It runs on PC with the 1.13.1 LWJGL2 backend.

## G2 — controls built in
`controls/src` is a cursor-free controller mod (first written as a javaagent for the PC version), with real libGDX
names and gdx-controllers 2.x: `ControllerMapping` instead of per-OS button tables. `Patch` adds
`ExiledKingdoms.render() { Pad.frame(); super.render(); Pad.after(); }`, so no javaagent is needed. Verified on PC
with a virtual pad, with no exceptions: menus, load, combat, save to a slot.

    tools/build.sh           # -> work/ek-switch.jar (deob + 1.13.1 compat + controls), ends with the Link count
    tools/run-dev.sh         # PC run with the dev channel /tmp/ekpad-cmd, log /tmp/eksw.log

## G3 — transpile + Linux native
- **Clearwing 3.1.2** is `SwitchGDX/clearwing-vm` commit `c9a6555` (no tag), built with JDK 17. SwitchGDX 3.0.2
  comes from JitPack.
- `switch/src/eksw`: `SwitchLauncher` (SwitchApplication) and `SwitchResolver` (IPlatformResolver without Steam).
- `switch/switch.json` marks `net.fdgames.**` reflective, because saves and settings are reflection-based Json.
- Clearwing's runtime lacks a few JDK APIs the game uses. `switch/runtime` adds a minimal `java.nio.file` and
  `eksw.Runtime` stand-ins. `tools/deob/SwitchPatch` redirects the calls, in the transpiler input only.
- The desktop launcher, Steamworks and JLayer/JOrbis are left out of the transpiler input.
- `tools/transpile.sh` → `work/sw/ExiledKingdoms` (~2,800 .cpp, RomFS ~170 MB). CMake 4 needs
  `-DCMAKE_POLICY_VERSION_MINIMUM=3.5`.
- Verified natively:
  - menu, new game, intro, world, walking;
  - save, then restart → Continue → loaded at the saved position;
  - audio (MP3 via SDL_mixer);
  - box2dlights every frame.
- Clearwing patches 0001/0002 (see the post-mortem): switch bypass slots, `File` and string decoding fixes,
  `getEnumConstants`, the `parseDouble("1.3.1210")` bug (saves "incompatible"), the GC marking race.
- Game-side workarounds:
  - `ekpad.mod.Legacy` replaces `ExiledKingdoms.a(ControllerCommand)`, whose switch-in-try was miscompiled;
  - `KeyInput` is a generated delegate (no `java.lang.reflect.Proxy`);
  - `EKSW_TRACE_CATCH=1` makes every catch handler print its stack trace.

## G4 — NRO in Eden
- `tools/build-nro.sh`: the devkitpro/devkita64 Docker image with SwitchGDX's `DevkitA64Libnx.cmake`, Release.
  libffi (not a devkitPro portlib) is built once from 3.4.6. The template's link line gets `-lharfbuzz`. The icon
  comes from the game's `icon_128.png`.
- `tools/run-eden.sh`: an Eden nightly with an isolated profile. 0.2.1 segfaults in its shader recompiler on this
  game. `use_vsync=0`, because FIFO froze the game while its window was hidden. SIGTERM only opens a dialog, so
  earlier instances are killed by PID.
- Eden starts the NRO with the SD root as working directory; hbmenu on the console uses the NRO's folder.
- Verified in Eden: boot, load a save written by the native build, walk, save to a slot, restart and load.

## G5 — console
- **First run:** boot, menu, character creation, then a crash entering the world (the GC double free).
- **UI fixes:**
  - the GlyphLayout wrap rule (squeezed text);
  - an invisible focus frame (`ShapeRenderer` lines);
  - tutorial tips reworded for the pad (`switch/texts-pad.tsv`, applied by `tools/patch-texts.py`);
  - the name field opens the system keyboard.
- **Crashes:**
  - `init.ini` written empty by `Properties.store`;
  - exit crashing on leaked files;
  - + quitting the app (SwitchGDX template).
- **Performance**, over several console rounds: everything in section 6 of the post-mortem.
- **Diagnostics added:**
  - `ekpad.log`: FPS, the worst frame, slow frames with timestamps;
  - `gc.log`: pauses, and a census with `gc-stats`;
  - `prof.log`: the runtime sampler, with `prof`;
  - `tools/g5-report.py`, `tools/console-pull.sh` and `tools/console-push.sh`.
- **Controller QoL:**
  - the d-pad as consumables (↑ recover, ↓ quick item, ←/→ change it);
  - the skill names while ZL/ZR are held;
  - Y = Take all in loot windows;
  - a "↑ Recover" prompt when hurt.

### Eden play bench
The dev channel is append-only, because Eden's virtual SD only reliably sees files that existed at boot.
`run-eden.sh` creates `ekpad-cmd` and `ekpad-ack`; `eden-cmd.sh` appends one command and waits until the game's
ack count passes it. Commands:
- `tap <text>|#n`, `key`, `walkto`, `talk`, `shop`, `pos`;
- `btn <A|B|X|Y|LB|RB|LT|RT|START|BACK|UP|DOWN|LEFT|RIGHT> [frames]` (the real pad's path);
- `stick <x> <y> <frames>` (screen directions);
- `shot <label>` (back buffer → PPM → `work/shots/<label>.png`, works on a hidden window);
- the host-side `waitfor <text> [seconds]`.
