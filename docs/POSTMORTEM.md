# Post-mortem: porting a Java/libGDX game to Switch homebrew with SwitchGDX + Clearwing

This is the record of taking *Exiled Kingdoms* (a commercial PC game written in Java on libGDX, shipped as an
obfuscated jar) to a native Nintendo Switch homebrew build. It is written for whoever attempts the next one:
what the pipeline looks like, what broke at each stage, how each problem was found, and what I would do first next
time. Numbers are measurements from this project, not estimates.

## 1. Choosing the route

The Switch has no JVM, and homebrew cannot JIT. The options for a Java game are:

| Route | Verdict here |
|---|---|
| Rewrite the game | out of scope |
| Port a JVM (e.g. a JIT-less interpreter) | slow, huge, memory hungry on 4 GB |
| **AOT-translate the bytecode to C++** and link a Switch libGDX backend | chosen |

[SwitchGDX](https://github.com/SwitchGDX/switch-gdx) is a libGDX backend for the Switch (EGL/GLES, SDL audio, hid
input, swkbd). [Clearwing](https://github.com/SwitchGDX/clearwing-vm) is its bytecode → C++ transpiler with a small
Java runtime (GC, threads, exceptions via setjmp, reflection metadata). Several libGDX games already shipped this way,
which proved the route. What that proof does *not* tell you is how a large, reflection-heavy, allocation-heavy
commercial game behaves on the Switch's CPU. Most of the work turned out to be there (section 6).

Constraints that shaped every decision:
- **Never modify or redistribute the game.** The build reads the owner's jar, writes everything under `work/`, and
  the repo holds only tools, patches and new code.
- **Keep the game's logic intact.** Every change is either in the platform layer (runtime, backend, libGDX) or
  a narrow, documented bytecode patch at a measured hotspot.

## 2. Pipeline

```
exiledkingdoms.jar ──deob──▶ ek-std.jar ──Patch (1.9→1.13, controls)──▶ ek-switch.jar
   (obfuscated, bundles        (game on official        (game + controller layer,
    its own libGDX)             libGDX 1.9.12)            libGDX 1.13.1 API)
                                                              │ SwitchPatch (Switch-only call redirects,
                                                              │ hotspot rewrites) + GdxPatch'ed gdx jar
                                                              ▼
                     Clearwing transpiler ──▶ ~2,800 .cpp + RomFS (assets, texts, WAV SFX)
                                                              │ devkitPro (Docker), SwitchGDX natives, libffi
                                                              ▼
                                                        ExiledKingdoms.nro
```

There are three test targets, used for different questions:
1. **The JVM on PC** (`run-dev.sh`): does the transformed game work? Fast iteration, real debugger.
2. **The Linux native build** of the same C++ (`run-native.sh`): is it the translation? It has gdb, `perf`, and
   instructions/frame as a load-independent benchmark.
3. **The Eden emulator**: does the `.nro` boot and play? It has scripted play and in-game screenshots.

Then **the console**, the only source of truth for performance.

## 3. Deobfuscating an embedded, obfuscated libGDX

The jar bundles its own copy of libGDX, run through ProGuard: class names kept, members renamed (`Array.b(int)` is
`removeIndex`). SwitchGDX is written against the real libGDX API, so the game had to be moved onto the official
library.

- **Identify the version.** The game reports 1.9.11, but its class set matches **1.9.12** exactly. Trust the class
  set, not the version string.
- **Match members** (`tools/deob/Match.java`) between each bundled class and the official one (same fully
  qualified name), in layers:
  1. names that survived;
  2. descriptors unique within the class;
  3. constant values;
  4. identical normalized bodies;
  5. votes from call sites inside bodies already matched, which propagate across classes;
  6. declaration order, for abstract methods.

  Result: **2,541 methods and 1,721 fields** renamed automatically, 23 by hand (`mapping-manual.tsv`, decided from
  bytecode behaviour with `Suggest` ranking candidates), 1 left unresolved: an FDG-only empty method nothing calls.
- **Remap** (`Remap.java`) the game, plus the third-party code ProGuard merged into it (box2dlights, decoders), to
  the real names. Walk the original hierarchy so overrides follow (`Screen.a()` → `show()`). Then drop the
  bundled copy.
- **Link check** (`Link.java`): resolve every reference against the target jars and list missing classes, members
  and unimplemented abstract methods. Against libGDX 1.13.1 it found 4. `Patch.java` fixes them with redirects to
  small shims (`compat/`).

Lesson: invest in the **link checker** early. It turns "crashes somewhere at runtime" into a finite list before
anything runs.

## 4. API drift: 1.9 → 1.13 bit in quiet ways

Nothing failed to link after `Link`, but behaviour drifted:
- **GlyphLayout wrap rule.** The game lays wrapped `Label`s out at width 0. Its libGDX disables wrapping when the
  target width is under 3 spaces; 1.13.1 *widens* the target to 3 spaces and wraps there, so every name and title
  broke into columns of 2–3 letters. `GdxPatch` restores the old rule in a patched copy of the gdx jar.
- **gdx-controllers 2.x** has no POV hats (the d-pad comes as mapped buttons). A shim rebuilds `getPov` from
  `ControllerMapping`.
- **SwitchGDX backend specifics.**
  - `ShapeRenderer` **lines** render nothing; filled shapes do. The focus frame is drawn as quads.
  - The backend exposes no GL30, so there are no VAOs.
  - The C++ template **quits the app on +** (the sample's "back to hbmenu"). In a game whose controller layer
    maps + to the menu, that looked like a crash on Start: the exit path then crashed on leaked files (section 5).

Lesson: diff every libGDX class the game leans on (text layout, scene2d tables, input) between versions, and look at
each one on screen early.

## 5. Clearwing runtime bugs (and how each was found)

All fixes are in `patches/clearwing-000{1,2}-*.patch`.

| Symptom | Cause | How it was found |
|---|---|---|
| C++ compile error: `bypasses` undeclared | switch-in-try bypass slots sized from try-catches only | compiler output |
| "Incompatible save" on load | `Double.parseDouble("1.3.1210")` returned 1.3121e-4 instead of throwing; the JSON reader then stored the version as a number | diffing a save written by the JVM against one written natively |
| SIGSEGV in `runGC` during load | the collection thread erased from the object set while marking ran | `coredumpctl` + gdb on the native Debug build |
| First console crash, entering the world | **double free**: an object queued for collection stayed in the set and was queued again by the next GC; its memory was then finalized after being zeroed | Atmosphère crash report, symbolized with `aarch64-none-elf-addr2line` against the ELF |
| Language change → error screen, then the game closes silently on every launch | the game saves `init.ini` with `Properties.store(new FileOutputStream(...))` and never closes the stream. The runtime's `store` threw and left a 0-byte file (the next boot could not parse it), and `exit()` crashed flushing the leaked `FILE` | the 0-byte `init.ini` on the SD card, plus the crash trace through `fclose` |
| Exit crashes in general | leaked `FileInputStream`s flushed by `exit()` after the RomFS was unmounted | crash trace `System.exit → _fwalk → __sflush_r → lseek` |

Other runtime traps worth knowing:
- **Value macros tested with `#ifdef`.** `USE_VALUE_CHECKS false` still enables the checks. Check how a config flag
  is consumed before relying on it.
- **`transpile` copies the runtime from the transpiler jar**, not from the source tree. Edit `runtime/res`, then
  rebuild the jar, or the next transpile silently reverts your fix.
- **stdout is buffered until exit.** On a crash you get nothing. Log through a file you open, append and close per
  line (Clearwing's `flush` is a no-op).
- Reflection works only for classes listed as reflective in the transpiler config (`net.fdgames.**` here): the
  saves are reflection-based JSON.

## 6. Performance on the Switch (the bulk of the work)

The first console build ran, but sometimes at 4–5 fps, with multi-second freezes and 20–27 s area transitions. The
fixes below are in the order they were found, each with its measurement. "Native" means instructions per frame
on the Linux build (`tools/bench-native.sh`), which is load-independent. "Console" means logs from real play.

### 6.1 Generated code overhead
- **Every Java method kept its locals in a `volatile` array**, needed by setjmp-based exceptions. That forces every
  load and store to memory. Only ~500 of ~21,000 methods contain a try/catch. The transpiler now emits `volatile`
  only for those (patch 0001). This is safe for the GC: the frame's address escapes into the shadow stack, so it is
  stored before every opaque call, where a GC can scan it.
- **String literals** went through a global mutex, a setjmp and a `std::map` lookup **on every use**, including
  `"objects"` inside the tile renderer's per-layer loop. Now there is one atomic static per call site.
- **Interface calls** took a global mutex and two map searches. A per-thread direct-mapped cache now sits in front.
- **Boxing had no cache**: `Integer.valueOf` allocated every time, against the JLS. Added for Integer, Long, Short,
  Byte and Character.

Native: **14.0M → 11.9M instructions/frame (−15%)**. Garbage from `Integer`: **~460k → ~48k objects per GC
cycle**. Time in mutexes: 2.4% → 0.4%.

### 6.2 Regex, everywhere
Clearwing implements `java.util.regex` through regexodus and compiles a pattern **per call**. Regex hid in:
- **`String.split`**, which every data and conversation file uses. Single-char patterns now take a hand-written
  path (`eksw.Runtime.split`), checked against the JVM by `tests/SplitCheck.java`;
- **libGDX `JsonWriter`**, which regex-tests every name and value while writing a save: 30–37% of an area
  transition, because the game autosaves there. The checks are now hand-written (`EkJsonQuote`), equal to the
  regexes on 217k strings, real saves included (`tests/JsonQuoteCheck.java`);
- **my own controller layer**: stripping color markup with `replaceAll` for every item of the open window, every
  frame. The inventory has 55 items, and that alone held it at **4–5 fps on the console**, invisible on PC.

Lesson: on this runtime, treat regex as a syscall. Grep the game *and your own code* for `split`, `replaceAll`,
`matches` and `Pattern`.

### 6.3 Game hotspots, patched narrowly
- **Text lookup** (`GameString.a`): a linear scan of all strings (thousands) that lowercases the key **once per
  entry**. It took up to a fifth of some frames (NPC AI and HUD look texts up constantly). It now uses a HashMap
  index with the same results (`eksw.Texts`).
- **Conversation zoom**: while the conversation panel is open, the renderer reset the sprite-bucket timer every
  frame, so 96×96 lists were rebuilt per frame instead of every 0.2 s. `g()` is now rate-limited to 0.1 s by a
  bytecode patch.
- **`System.gc()`** called by the game on every dialog and transition: each call is a 50–90 ms pause on the console.
  It is now ignored when fewer than 200k objects were allocated since the last collection, which the JLS allows.
- **Shaders recompiled on every area** by box2dlights: compiled programs are now cached by source (`EkShaderCache`).
- **Sounds decoded from MP3 on every area**: the 66 effects become WAV at build time, under the same names, since
  SDL_mixer sniffs the format.

### 6.4 The big one: malloc contention after every GC
After the fixes above, console sessions still had **7–32 s streaks at ~4 fps**. The logs showed every streak
**starting 3–5 s after a GC**, with a length that grew with the number of objects that GC collected (348k → 7.6 s,
639k → 31.7 s). Moving the collection thread to another core changed nothing. So it was **lock contention, not
CPU**. The collection thread freed ~500k objects (two `free` calls each: the object and its `ObjectMonitor`) while
the game thread allocated. Both fought over newlib's global malloc lock, and every contended acquire on Horizon is
a kernel call: about **50 µs per object**.

Fix (runtime):
- **Lazy monitors**: an object's monitor is created on first `synchronized`/`wait`/`notify`, with a CAS. This
  halved the malloc traffic and took the heap at GC from ~165 to ~108 MB.
- **Incremental sweep on the allocating thread**: the collection thread only runs finalizers that execute Java
  code or take locks, then queues the objects. Each allocation frees 8 queued ones. Nobody contends anymore.

Console, before and after this and 6.2–6.3:

| | Before (6.5 min session) | After (26 min session) |
|---|---|---|
| Time lost to slow frames | 97 s | 64 s |
| Longest streak | 32.6 s | 5.6 s |
| Area transition | 20–27 s | 5–6 s |
| Save load | 23 s | 7–8 s |
| Boot to main menu | 56 s (first build) | 16 s |

### 6.5 Things that did not matter (or were impossible)
- **Draw calls.** ~120 per frame in the world and ~250 with the inventory open, each costing ~18 GL calls. They are
  real, but the world runs at 60 fps anyway; VAOs would need GL30 in the backend.
- **Release flags.** The build already used `-O3` with `-march=armv8-a`; LTO is impractical at ~2,800 files.

## 7. Methodology that worked

- **Measure on the device, correlate, then fix.** Three cheap instruments carried the performance work:
  - `ekpad.log`: FPS and the worst frame every 10 s, plus every frame over 150 ms with a timestamp and the open
    window;
  - `gc.log`: every GC pause over 20 ms, with live/queued counts and an optional allocation census by class;
  - **a sampling profiler inside the runtime** (`prof` file → `prof.log`): it reads the main thread's shadow stack
    (Clearwing keeps one for stack traces) every millisecond and dumps self, inclusive and caller→callee counts
    every 10 s. It runs on the GC's collection thread, because a thread of its own hung the boot. Unexpectedly,
    its sample counts were also a signal: windows with few samples were exactly the windows where the collection
    thread was stuck freeing.

  `tools/g5-report.py` joins the three: each streak of slow frames, the GC just before it, and what the profiler saw.
  That join made 6.4 visible in one glance.
- **`perf` without root.** Unpack the distribution's perf package and its three libraries into a scratch directory
  and run it with `LD_LIBRARY_PATH`. With `perf_event_paranoid=2` you can profile your own processes. Attach with
  `-p`. Use instructions per frame, not fps: a window on a hidden workspace is fps-capped by the compositor.
- **An `LD_PRELOAD` I/O counter** (a dozen lines of C) answered "is it tiny reads?" in five minutes. It was not.
- **Eden as a functional bench, never a performance one.**
  - Script it through the game itself: a dev channel file the controller layer reads, with commands like
    `btn A`, `stick`, `tap <text>`, `walkto`, `shot` (reads the back buffer into a PPM, which works on a hidden
    workspace) and a host-side `waitfor <log text>`.
  - Eden's virtual SD only reliably sees files that **existed at boot**, so the channel is append-only with an ack
    counter: create both files before launching, never delete or replace them.
  - Eden pitfalls:
    - Eden ≠ console for anything timing-related;
    - its working directory is the SD root (hbmenu uses the NRO's folder);
    - FIFO vsync on a hidden window freezes the game; use Immediate;
    - SIGTERM only opens a dialog; kill by PID;
    - a dumped `prod.keys` is required.
- **Read crash reports first.** Atmosphère's report plus `addr2line` against the ELF named the faulting function
  every time, in seconds.

## 8. Checklist for the next port

1. Identify the real library version from the class set; build the matcher, remapper and **link checker** first.
2. Get the game running on the official library on the JVM before touching the Switch.
3. Grep the game and your code for regex (`split`, `replaceAll`, `matches`, `Pattern`), `System.gc`, linear lookups
   in hot paths, and per-frame allocation in UI code.
4. Patch the runtime for: boxing caches, literal caching, interface dispatch, `volatile` frames,
   **lazy monitors and sweeping on the allocating thread**, files closed at exit. Check each against your runtime
   version.
5. Before the first console run, add frame-time logs with timestamps, GC logs and the in-runtime sampler, plus a
   report script that correlates them.
6. Remove backend sample behaviour (`+` quits) and check which GL features the backend really has.
7. Keep a native Linux build of the same C++ for gdb and `perf`, and an emulator bench for scripted functional
   checks. Trust only the console for performance.
8. Convert compressed assets the game decodes on load (MP3 SFX) at build time; cache shaders.
