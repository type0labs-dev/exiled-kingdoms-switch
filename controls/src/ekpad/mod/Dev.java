package ekpad.mod;

import ekpad.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

/**
 * EKPAD_DEV only: scripted keyboard for tests without stealing the desktop's focus.
 * Append lines to /tmp/ekpad-cmd (Switch: ekpad-cmd in the working dir); ekpad-ack next to it counts the lines taken:
 *   key <libgdx keycode> [frames]   e.g. "key 20" = Down, 66 = Enter, 131 = Esc
 *   tap <text> | tap #<index>       focus the menu item whose description contains text (or index) and press it
 *   pos                             log the player's tile and how many things are in reach
 *   stick <x> <y> <frames>          hold the left stick (screen directions, -1..1; y down = 1)
 *   walkto <x> <y>                  steer the stick to a world point (stops there, or after 30 s)
 *   talk <spawn> <conversation>     open a conversation like a map script does (e.g. talk adaon adaon_tutorial)
 *   shop <spawn>                    open the trade window against that NPC
 *   btn <A|B|X|Y|LB|RB|LT|RT|START|BACK|UP|DOWN|LEFT|RIGHT> [frames]   hold a pad button (the real pad's path; default 6 frames;
 *                                   later commands run meanwhile, e.g. a shot of what holding ZL shows)
 *   shot <label>                    write what the player sees to ekpad-shot-<label>.ppm (next to ekpad-cmd)
 */
class Dev {
    // EKPAD_DEV, or an "ekpad-dev" file in the working dir (Clearwing builds have no environment variables)
    static final boolean ON = System.getenv("EKPAD_DEV") != null || new File("ekpad-dev").exists();
    // no /tmp on the Switch: next to the game (on Eden that is the virtual SD card, writable from the host)
    static final File CMD = new File(new File("/tmp").isDirectory() ? "/tmp/ekpad-cmd" : "ekpad-cmd");
    static int gap;

    /** Desktop dev runs: keep rendering (and polling) while the window sits on a hidden workspace. */
    static void keepRenderingHidden() {
        try {
            Object g = com.badlogic.gdx.Gdx.graphics;
            java.lang.reflect.Field f = g.getClass().getDeclaredField("config");
            f.setAccessible(true);
            Object cfg = f.get(g);
            for (String n : new String[]{"pauseWhenMinimized", "pauseWhenBackground"}) cfg.getClass().getField(n).setBoolean(cfg, false);
            cfg.getClass().getField("backgroundFPS").setInt(cfg, 60);
            Log.i("dev: rendering kept while hidden");
        } catch (Throwable t) {
            Log.i("dev: no LWJGL config to tweak (" + t + ")");
        }
    }
    static int walkX, walkY, walkFrames, stickFrames;
    static float stickX, stickY;

    /** walkto: steer the stick toward a world point (iso: dx = sx + sy, dy = sx - sy). Returns {sx, sy} or null. */
    static float[] walk() {
        if (ON && stickFrames > 0) { stickFrames--; return new float[]{stickX, stickY}; }
        if (!ON || walkFrames <= 0) return null;
        if (FocusNav.scope != null) { walkFrames--; return null; } // a popup pauses the walk (but its timeout runs)
        net.fdgames.GameEntities.Final.Player pl = net.fdgames.GameWorld.GameData.a().player;
        float dx = walkX - pl.x, dy = walkY - pl.y;
        if (Math.abs(dx) < 24 && Math.abs(dy) < 24 || --walkFrames == 0) {
            Log.i("dev walk end at x=" + pl.x + " y=" + pl.y + (walkFrames == 0 ? " (timeout)" : ""));
            walkFrames = 0;
            return null;
        }
        float sx = (dx + dy) / 2, sy = (dx - dy) / 2, n = Math.max(Math.abs(sx), Math.abs(sy));
        return new float[]{sx / n, sy / n};
    }
    // append-only channel (Eden's virtual SD only reliably sees files that existed at boot, so nothing is deleted or
    // replaced): the game takes complete lines in order and writes how many it took to ekpad-ack
    static final File ACK = new File(CMD.getPath().replace("ekpad-cmd", "ekpad-ack"));
    static int done;
    static long seen; // 0 = a missing or empty file is never opened

    static String next() throws Exception {
        long len = CMD.length();
        java.io.InputStream in = new java.io.FileInputStream(CMD);
        byte[] b = new byte[(int) len];
        int n = 0;
        for (int r; n < b.length && (r = in.read(b, n, b.length - n)) > 0; ) n += r;
        in.close();
        String[] lines = new String(b, 0, n, "UTF-8").split("\n", -1); // last element: an unfinished line, or ""
        if (lines.length - 1 < done) done = 0; // the file was recreated
        if (lines.length - 1 <= done) { seen = len; return null; }
        String line = lines[done++];
        if (lines.length - 1 == done) seen = len; // else read again next time: more lines are waiting
        java.io.FileOutputStream o = new java.io.FileOutputStream(ACK);
        o.write((done + "\n").getBytes("US-ASCII"));
        o.close();
        return line;
    }
    static volatile String tap;
    static String shot;

    /** After the frame is drawn: the back buffer as binary PPM (no PNG: avoids the runtime's Deflater). */
    static void shoot() {
        String label = shot;
        shot = null;
        try {
            int w = com.badlogic.gdx.Gdx.graphics.getBackBufferWidth(), h = com.badlogic.gdx.Gdx.graphics.getBackBufferHeight();
            java.nio.ByteBuffer px = java.nio.ByteBuffer.allocateDirect(w * h * 4);
            com.badlogic.gdx.Gdx.gl.glPixelStorei(com.badlogic.gdx.graphics.GL20.GL_PACK_ALIGNMENT, 1);
            com.badlogic.gdx.Gdx.gl.glReadPixels(0, 0, w, h, com.badlogic.gdx.graphics.GL20.GL_RGBA, com.badlogic.gdx.graphics.GL20.GL_UNSIGNED_BYTE, px);
            byte[] row = new byte[w * 3];
            File f = new File(CMD.getParentFile(), "ekpad-shot-" + label + ".ppm");
            java.io.OutputStream o = new java.io.BufferedOutputStream(new java.io.FileOutputStream(f), 1 << 16);
            o.write(("P6\n" + w + " " + h + "\n255\n").getBytes("US-ASCII"));
            for (int y = h - 1; y >= 0; y--) { // GL rows start at the bottom
                for (int x = 0, i = y * w * 4; x < w; x++, i += 4) {
                    row[x * 3] = px.get(i);
                    row[x * 3 + 1] = px.get(i + 1);
                    row[x * 3 + 2] = px.get(i + 2);
                }
                o.write(row);
            }
            o.close();
            Log.i("dev shot " + f.getName() + " " + w + "x" + h);
        } catch (Throwable t) {
            Log.e("shot", t);
        }
    }
    static final Map<Integer, Integer> keys = new HashMap<Integer, Integer>(); // key -> frames left held
    static final String[] BTN = {"A", "B", "X", "Y", "LB", "RB", "LT", "RT", "BACK", "START", // index = Hints id
            "LS", "RS", "", "", "", "", "UP", "DOWN", "LEFT", "RIGHT"};
    static final Map<Integer, Integer> btns = new HashMap<Integer, Integer>(); // Hints button -> frames left held

    /** Is the scripted pad button held this frame? (counted down in frame()) */
    static boolean btn(int hint) {
        return ON && btns.containsKey(hint);
    }

    static void frame(java.util.Set<Integer> held) throws Exception {
        if (!ON) return;
        for (Integer k : new java.util.ArrayList<Integer>(btns.keySet())) {
            int left = btns.get(k) - 1;
            if (left < 0) btns.remove(k); else btns.put(k, left);
        }
        if (keys.isEmpty() && gap > 0) gap--;
        else if (keys.isEmpty() && walkFrames == 0 && stickFrames == 0 && CMD.length() != seen) { // a walk finishes before the next command
            gap = 4; // release frames between commands, so repeated keys are distinct presses
            String line = next(); // one command per frame keeps "just pressed" edges distinct
            if (line != null && line.startsWith("shop ")) {
                // what interacting with a merchant does: the character window in trade mode against that NPC
                String name = line.trim().split(" +")[1];
                int id = net.fdgames.GameLevel.GameLevelData.b().a(name);
                Object npc = id > 0 ? net.fdgames.GameLevel.GameLevel.d(id) : null;
                Log.i("dev " + line.trim() + " (npc " + npc + ")");
                if (npc instanceof net.fdgames.GameEntities.Helpers.Lootable)
                    net.fdgames.UI.P.a().g.a(1, (net.fdgames.GameEntities.Helpers.Lootable) npc);
            } else if (line != null && line.startsWith("talk ")) {
                // what a map script does to start a conversation: speaker = a spawn on this level, by name
                String[] p = line.trim().split(" +");
                int id = net.fdgames.GameLevel.GameLevelData.b().a(p[1]);
                Log.i("dev " + line.trim() + " (speaker id " + id + ")");
                if (id > 0) {
                    net.fdgames.TiledMap.g conv = new net.fdgames.TiledMap.g(id, p[2]);
                    conv.e();
                    net.fdgames.UI.P.a();
                    net.fdgames.UI.P.q().a(conv);
                }
            } else if (line != null && line.startsWith("walkto ")) {
                String[] p = line.trim().split(" +");
                walkX = Integer.parseInt(p[1]);
                walkY = Integer.parseInt(p[2]);
                walkFrames = 60 * 30;
                Log.i("dev " + line.trim());
            } else if (line != null && line.startsWith("stick ")) {
                String[] p = line.trim().split(" +");
                stickX = Float.parseFloat(p[1]);
                stickY = Float.parseFloat(p[2]);
                stickFrames = Integer.parseInt(p[3]);
                Log.i("dev " + line.trim());
            } else if (line != null && line.startsWith("btn ")) {
                String[] p = line.trim().split(" +");
                int i = java.util.Arrays.asList(BTN).indexOf(p[1].toUpperCase());
                if (i >= 0) btns.put(i, p.length > 2 ? Integer.parseInt(p[2]) : 6);
                Log.i("dev " + line.trim() + (i < 0 ? " (unknown button)" : ""));
            } else if (line != null && line.startsWith("shot")) {
                shot = line.length() > 5 ? line.substring(5).trim() : String.valueOf(Pad.frames);
            } else if (line != null && line.trim().equals("items")) {
                FocusNav.lastCount = -1; // re-log the current menu's items with positions
            } else if (line != null && line.trim().equals("pos")) {
                net.fdgames.GameEntities.Final.Player pl = net.fdgames.GameWorld.GameData.a().player;
                Log.i("dev pos x=" + pl.x + " y=" + pl.y + " activables=" + pl.numActivables
                        + " hudMode=" + (Pad.hud() == null ? -1 : Pad.hud().d()) + " menu=" + (FocusNav.scope == null ? "-" : FocusNav.scope.getClass().getName()));
            } else if (line != null && line.startsWith("tap ")) {
                tap = line.substring(4).trim();
                gap = 45; // windows fade in/out; let the next screen settle
                Log.i("dev " + line.trim());
            } else if (line != null && line.startsWith("key ")) {
                String[] p = line.trim().split(" +");
                keys.put(Integer.parseInt(p[1]), p.length > 2 ? Integer.parseInt(p[2]) : 6);
                Log.i("dev " + line.trim());
            }
        }
        for (Map.Entry<Integer, Integer> e : keys.entrySet()) held.add(e.getKey());
        for (Integer k : new java.util.ArrayList<Integer>(keys.keySet())) {
            int left = keys.get(k) - 1;
            if (left <= 0) keys.remove(k); else keys.put(k, left);
        }
    }
}
