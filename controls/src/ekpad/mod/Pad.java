package ekpad.mod;

import ekpad.Log;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.utils.Array;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.fdgames.UI.P;
import net.fdgames.ek.ControllerCommand;
import net.fdgames.ek.ControllerCommand$commandType;
import net.fdgames.ek.ControllerConfig;
import net.fdgames.ek.ExiledKingdoms;
import net.fdgames.ek.KeyboardConfig;
import net.fdgames.ek.Settings;

/**
 * Called once per frame at the end of Game.render(). Reads the real pad and drives the game through
 * a VirtualController (ExiledKingdoms.p + Settings.a) and the keyboard bindings (KeyInput).
 *
 * Layout (Xbox names):
 *   left stick / d-pad  move, navigate          A  attack / confirm      B  cancel / back
 *   X  interact (inventory: take)               Y  use quick item (inventory: drop; loot: take all)
 *   LB / RB  previous / next quick item         LB+RB  recovery
 *   d-pad (world)  up recover, down use quick item, left / right previous / next quick item
 *   LT + A/B/X/Y  skills 1-4                    RT + A/B/X/Y  skills 5-8
 *   Back  character    Start  menu    L3  map    R3  journal
 */
public class Pad {
    // the Switch (and Eden) has no /tmp: same test as Dev.CMD
    static final boolean CONSOLE = !new java.io.File("/tmp").isDirectory();
    static final boolean PROBE = System.getenv("EKPAD_PROBE") != null || System.getProperty("ekpad.probe") != null;
    // physical buttons come from gdx-controllers 2.x ControllerMapping (SDL on PC, Joy-Con/Pro on Switch)
    static final VirtualController V = new VirtualController();
    static final boolean[] pulse = new boolean[VirtualController.COUNT]; // last frame's raw state, for edges
    static final boolean[] lastLogged = new boolean[VirtualController.COUNT];
    static KeyInput keys;
    static Set<Integer> heldKeys = new HashSet<Integer>();
    static ControllerConfig configured;
    static long frames;
    static float worstDt;
    static com.badlogic.gdx.graphics.profiling.GLProfiler glProf;
    static int errors;
    static String lastPad;
    static boolean aWas, bWas, xWas, yWas, aMenu, bMenu, xMenu, yMenu;

    public static void frame() {
        if (errors > 3) return; // ponytail: a broken hook goes quiet instead of spamming every frame
        try {
            float dt = Gdx.graphics.getDeltaTime();
            if (dt > worstDt) worstDt = dt;
            if (dt > 0.15f && frames > 60) // hitches with their context (menus feel "stuck" on the console)
                Log.i("slow frame " + (int) (dt * 1000) + " ms at " + System.currentTimeMillis() + (FocusNav.scope == null ? "" : " in " + FocusNav.scope.getClass().getName()));
            if (frames == 2 && Dev.ON) { // dev: GL calls per frame (texture switches flush the batch)
                glProf = new com.badlogic.gdx.graphics.profiling.GLProfiler(Gdx.graphics);
                glProf.enable();
            }
            if (frames++ % 600 == 0) { // ~10 s: frame pacing on the console (where there is no profiler)
                Log.i("frame " + frames + " fps " + Gdx.graphics.getFramesPerSecond() + " worst " + (int) (worstDt * 1000) + " ms"
                        + (glProf == null ? "" : " draws " + glProf.getDrawCalls() + " binds " + glProf.getTextureBindings()
                        + " calls " + glProf.getCalls() + " (last frame)"));
                worstDt = 0;
            }
            if (glProf != null) glProf.reset(); // counts cover one frame: game render runs between this and the next
            if (!Config.loaded) Config.load();
            if (frames == 1 && Dev.ON) Dev.keepRenderingHidden();
            if (PROBE && frames > 900) Probe.run(); // after the game itself initialised Controllers
            if (keys == null && Gdx.input != null) {
                keys = new KeyInput(Gdx.input);
                Gdx.input = keys.proxy();
                Log.i("keyboard proxy installed");
            }
            if (Settings.a != null && Settings.a != configured) configure();
            Controller real = realPad();
            if (real != null && ExiledKingdoms.p != V) ExiledKingdoms.p = V;
            map(real);
        } catch (Throwable t) {
            errors++;
            Log.e("frame", t);
        }
    }

    /** End of the frame, after the game drew everything. */
    public static void after() {
        if (errors > 3) return;
        try {
            FocusNav.draw();
        } catch (Throwable t) {
            errors++;
            Log.e("after", t);
        }
        try {
            Hints.draw();
        } catch (Throwable t) {
            errors++;
            Log.e("hints", t);
        }
        if (Dev.shot != null) Dev.shoot();
    }

    /** First pad, preferring "xbox" like the game does; re-read every frame so hot-plug and streaming reconnects work. */
    static Controller realPad() {
        Array<Controller> all = Controllers.getControllers();
        Controller pick = null;
        for (int i = 0; i < all.size; i++) {
            Controller c = all.get(i);
            if (c == V || c.getMapping() == null) continue;
            if (pick == null || c.getName().toLowerCase().contains("xbox")) pick = c;
        }
        String name = pick == null ? null : pick.getName();
        if (name == null ? lastPad != null : !name.equals(lastPad)) Log.i("pad: " + name);
        lastPad = name;
        return pick;
    }

    /** Points the game's controller config at the virtual ids. Settings.a is replaced when controls.ini loads. */
    static void configure() {
        ControllerConfig c = Settings.a;
        ControllerCommand$commandType neg = ControllerCommand$commandType.a, pos = ControllerCommand$commandType.b,
                button = ControllerCommand$commandType.c;
        c.up = new ControllerCommand(neg, VirtualController.AX_Y);
        c.down = new ControllerCommand(pos, VirtualController.AX_Y);
        c.left = new ControllerCommand(neg, VirtualController.AX_X);
        c.right = new ControllerCommand(pos, VirtualController.AX_X);
        c.action = new ControllerCommand(button, VirtualController.ACTION);
        c.cancel = new ControllerCommand(button, VirtualController.CANCEL);
        c.skill1 = new ControllerCommand(button, VirtualController.SKILL1);
        c.skill2 = new ControllerCommand(button, VirtualController.SKILL1 + 1);
        c.skill3 = new ControllerCommand(button, VirtualController.SKILL1 + 2);
        c.skill4 = new ControllerCommand(button, VirtualController.SKILL1 + 3);
        c.skill5 = new ControllerCommand(button, VirtualController.SKILL1 + 4);
        c.skill6 = new ControllerCommand(button, VirtualController.SKILL1 + 5);
        c.skill7 = new ControllerCommand(button, VirtualController.SKILL1 + 6);
        c.skill8 = new ControllerCommand(button, VirtualController.SKILL1 + 7);
        c.useItem = new ControllerCommand(button, VirtualController.USE);
        c.nextItem = new ControllerCommand(button, VirtualController.NEXT);
        c.prevItem = new ControllerCommand(button, VirtualController.PREV);
        c.recovery = new ControllerCommand(button, VirtualController.RECOVERY);
        c.invTake = new ControllerCommand(button, VirtualController.INV_TAKE);
        c.invDrop = new ControllerCommand(button, VirtualController.INV_DROP);
        configured = c;
        Log.i("controller config pointed at virtual pad");
    }

    static void map(Controller real) throws Exception {
        boolean[] b = V.btn;
        Arrays.fill(b, false);
        Set<Integer> held = new HashSet<Integer>();
        if (keys != null) keys.hidden.clear();
        Hints.clear();
        // no pad = all zeros: menus still run on the keyboard
        float sx = 0, sy = 0, dx = 0, dy = 0;
        boolean a = false, bb = false, x = false, y = false, lt = false, rt = false, lb = false, rb = false;
        if (real != null) {
            ControllerMapping m = real.getMapping();
            if (pressed(real, m.buttonDpadUp)) dy = -1;
            if (pressed(real, m.buttonDpadDown)) dy = 1;
            if (pressed(real, m.buttonDpadRight)) dx = 1;
            if (pressed(real, m.buttonDpadLeft)) dx = -1;
            if (Dev.btn(Hints.DUP)) dy = -1;
            if (Dev.btn(Hints.DDOWN)) dy = 1;
            if (Dev.btn(Hints.DRIGHT)) dx = 1;
            if (Dev.btn(Hints.DLEFT)) dx = -1;
            sx = real.getAxis(m.axisLeftX);
            sy = real.getAxis(m.axisLeftY);
            if (ControllerWindow.capture(real)) real = null; // the press that rebinds does nothing else
            else {
                a = act(real, Hints.A); bb = act(real, Hints.B); x = act(real, Hints.X); y = act(real, Hints.Y);
                lt = act(real, Hints.LT); rt = act(real, Hints.RT); lb = act(real, Hints.LB); rb = act(real, Hints.RB);
            }
        }
        if (Dev.ON) { // scripted presses (Dev "btn") join the real pad's
            a |= Dev.btn(Hints.A); bb |= Dev.btn(Hints.B); x |= Dev.btn(Hints.X); y |= Dev.btn(Hints.Y);
            lb |= Dev.btn(Hints.LB); rb |= Dev.btn(Hints.RB); lt |= Dev.btn(Hints.LT); rt |= Dev.btn(Hints.RT);
        }
        KeyboardConfig k = Settings.b;
        boolean back = real != null && act(real, Hints.BACK) || Dev.btn(Hints.BACK);
        // Back: a tap opens the character window (on release), holding it shows the control legend
        if (back) backHeld += Gdx.graphics.getDeltaTime();
        boolean legend = back && backHeld >= 0.35f, backTap = !back && backHeld > 0 && backHeld < 0.35f;
        if (!back) backHeld = 0;
        // prompts follow the last device touched
        if (keys != null && (keys.real.isKeyPressed(-1) || keys.real.isKeyJustPressed(-1))) Hints.keyboard = true; // ANY_KEY
        if (a || bb || x || y || lb || rb || lt || rt || back || Math.abs(sx) > 0.5f || Math.abs(sy) > 0.5f
                || dx != 0 || dy != 0 || real != null && (act(real, Hints.START) || act(real, Hints.LS) || act(real, Hints.RS)))
            Hints.keyboard = false;
        if (k != null && Dev.btn(Hints.START)) held.add(k.escape.id);
        if (k != null && backTap) held.add(k.character.id);
        if (real != null && k != null) {
            if (act(real, Hints.START)) held.add(k.escape.id);
            if (act(real, Hints.LS)) held.add(k.map.id);
            if (act(real, Hints.RS)) held.add(k.log.id);
        }
        Dev.frame(held);
        applyKeys(held); // this frame's keys before the menu reads them (Start = Esc must not lag a frame)
        float[] w = Dev.walk();
        if (w != null) { sx = w[0]; sy = w[1]; }
        int mode = FocusNav.update(sx, sy, dx, dy, a && !aWas, bb && !bWas, x && !xWas, y && !yWas);
        // a press belongs to the context it started in: the A/B that closed a menu must not attack/cancel after it
        if (a && !aWas) aMenu = mode != FocusNav.NONE;
        if (bb && !bWas) bMenu = mode != FocusNav.NONE;
        if (x && !xWas) xMenu = mode != FocusNav.NONE;
        if (y && !yWas) yMenu = mode != FocusNav.NONE;
        aWas = a;
        bWas = bb;
        xWas = x;
        yWas = y;
        if (aMenu) a = false;
        if (bMenu) bb = false;
        if (xMenu) x = false;
        if (yMenu) y = false;
        if (mode == FocusNav.NONE) {
            if ((lt || rt) && !legend && !FocusNav.nativeOpen) skillHints(lt ? 0 : 4);
            else worldHints(legend);
            if (FocusNav.nativeOpen && keys != null) {
                // the conversation window only reads the controller (and number keys): arrows + Enter drive it too
                Input kb = Gdx.input; // proxy: real keys + Dev-injected ones
                if (kb.isKeyPressed(FocusNav.K_UP)) dy = -1;
                if (kb.isKeyPressed(FocusNav.K_DOWN)) dy = 1;
                if (kb.isKeyPressed(FocusNav.K_ENTER) || kb.isKeyPressed(FocusNav.K_NUMPAD_ENTER)) a = true;
            }
            // the d-pad is the consumables pad in the world (console ARPG style); it still picks conversation lines
            boolean dpadMoves = FocusNav.nativeOpen;
            V.axis[VirtualController.AX_X] = clamp(sx + (dpadMoves ? dx : 0));
            V.axis[VirtualController.AX_Y] = clamp(sy + (dpadMoves ? dy : 0));
            if (lt || rt) {
                int s0 = VirtualController.SKILL1 + (lt ? 0 : 4);
                b[s0] = a; b[s0 + 1] = bb; b[s0 + 2] = x; b[s0 + 3] = y;
            } else {
                b[VirtualController.ACTION] = a;
                b[VirtualController.CANCEL] = bb && FocusNav.nativeOpen; // in the world, B alone would open the menu
                if (inventoryOpen()) {
                    b[VirtualController.INV_TAKE] = x;
                    b[VirtualController.INV_DROP] = y;
                } else {
                    if (x && k != null) held.add(k.interact.id);
                    b[VirtualController.USE] = y;
                }
            }
            if (lb && rb) b[VirtualController.RECOVERY] = true;
            else { b[VirtualController.PREV] = lb; b[VirtualController.NEXT] = rb; }
            if (!dpadMoves) {
                if (dy < 0) b[VirtualController.RECOVERY] = true;
                if (dy > 0) b[VirtualController.USE] = true;
                if (dx < 0) b[VirtualController.PREV] = true;
                if (dx > 0) b[VirtualController.NEXT] = true;
            }
        } else {
            // a menu owns stick, d-pad, A and B; B reaches the game only when the menu has no Back button
            V.axis[0] = V.axis[1] = 0;
            b[VirtualController.CANCEL] = FocusNav.passCancel;
            if (mode == FocusNav.MAP) { // the game scrolls its maps with the arrow keys
                if (sy < -0.5f) held.add(FocusNav.K_UP);
                if (sy > 0.5f) held.add(FocusNav.K_DOWN);
                if (sx < -0.5f) held.add(FocusNav.K_LEFT);
                if (sx > 0.5f) held.add(FocusNav.K_RIGHT);
            }
        }
        // the game polls BUTTONs as "held"; one-shot actions must fire once per press, so they only last one frame
        for (int i = 0; i < b.length; i++) {
            boolean now = b[i];
            if (i != VirtualController.ACTION && i != VirtualController.CANCEL
                    && i != VirtualController.INV_TAKE && i != VirtualController.INV_DROP) b[i] = now && !pulse[i];
            pulse[i] = now;
            if (Dev.ON && b[i] && !lastLogged[i]) Log.i("virtual button " + i);
            lastLogged[i] = b[i];
        }
        applyKeys(held);
        heldKeys = held;
    }

    static float backHeld;

    /** Holding ZL / ZR: which face button fires which hotbar skill (slots 0-3 / 4-7), by the skill's name. */
    static void skillHints(int first) {
        int[] face = {Hints.A, Hints.B, Hints.X, Hints.Y}; // same order as map(): s0 = A, s0+1 = B, s0+2 = X, s0+3 = Y
        try {
            net.fdgames.GameEntities.Final.Player p = net.fdgames.GameLevel.GameLevel.a();
            for (int i = 0; i < 4; i++) {
                String id = p.l(first + i);
                net.fdgames.Rules.Skill skill = id == null || id.length() == 0 ? null : net.fdgames.Rules.Skills.a(id);
                if (skill != null) Hints.add(face[i], skill.b());
            }
        } catch (Throwable ignored) { // no game loaded yet
        }
        Hints.layout = Hints.WORLD;
    }

    /** Prompts outside menus: the conversation's keys, "X Interact" when something is in reach, or the legend. */
    static void worldHints(boolean legend) {
        if (FocusNav.nativeOpen) {
            Hints.add(Hints.UPDOWN, Hints.t("Choose", "Escolher"));
            Hints.add(Hints.A, Hints.t("Confirm", "Confirmar"));
            return;
        }
        if (legend) {
            Hints.layout = Hints.LEGEND;
            Hints.add(Hints.STICK, Hints.t("Move", "Andar"));
            Hints.add(Hints.A, Hints.t("Attack", "Atacar"));
            Hints.add(Hints.X, Hints.t("Interact", "Interagir"));
            Hints.add(Hints.DUP, Hints.t("Recover", "Recuperar"));
            Hints.add(Hints.DDOWN, Hints.t("Use quick item", "Usar item r\u00e1pido"));
            Hints.add(Hints.DLR, Hints.t("Change quick item", "Trocar item r\u00e1pido"));
            Hints.add(Hints.Y, Hints.t("Use quick item", "Usar item r\u00e1pido"));
            Hints.add(Hints.LT, Hints.t("+ face button: skills 1-4", "+ bot\u00e3o: skills 1-4"));
            Hints.add(Hints.RT, Hints.t("+ face button: skills 5-8", "+ bot\u00e3o: skills 5-8"));
            Hints.add(Hints.BACK, Hints.t("Character (hold: this list)", "Personagem (segure: esta lista)"));
            Hints.add(Hints.START, Hints.t("Menu", "Menu"));
            Hints.add(Hints.LS, Hints.t("Map", "Mapa"));
            Hints.add(Hints.RS, Hints.t("Journal", "Di\u00e1rio"));
            return;
        }
        try {
            net.fdgames.GameEntities.Final.Player p = net.fdgames.GameWorld.GameData.a().player;
            if (p.numActivables > 0) {
                Hints.layout = Hints.WORLD;
                Hints.add(Hints.X, Hints.t("Interact", "Interagir"));
            }
            // hurt, a recover charge left and no enemies around (what the game's recover button checks)
            if (p.sheet.stats.missingHP > 0 && p.O() > 0 && !net.fdgames.GameEntities.Final.Player.c) {
                Hints.layout = Hints.WORLD;
                Hints.add(Hints.DUP, Hints.t("Recover", "Recuperar"));
            }
        } catch (Throwable ignored) { // no game loaded yet
        }
    }

    /** Publishes pad-made keys to the game: held, plus "just pressed" for the ones not held last frame. */
    static void applyKeys(Set<Integer> held) {
        if (keys == null) return;
        keys.just.clear();
        for (Integer key : held) if (!heldKeys.contains(key)) keys.just.add(key);
        keys.held.clear();
        keys.held.addAll(held);
    }

    static java.lang.reflect.Field hudField;

    /** GameHUD singleton or null. Never call P.a()/P.m() early: they build the HUD before its assets exist. */
    static P hud() throws Exception {
        if (hudField == null) {
            hudField = P.class.getDeclaredField("av");
            hudField.setAccessible(true);
        }
        return (P) hudField.get(null);
    }

    /** P.m() without its side effect: is the CharacterWindow (inventory/loot/shop) showing? */
    static boolean inventoryOpen() throws Exception {
        P h = hud();
        return h != null && h.g != null && h.g.isVisible();
    }

    /** Is the button the player bound to this action (Config.bind) held? */
    static boolean act(Controller c, int action) {
        return phys(c, Config.bind[action]);
    }

    /** A physical button named by its Xbox position (Hints ids), through the pad's ControllerMapping. */
    static boolean phys(Controller c, int b) {
        ControllerMapping m = c.getMapping();
        if (m == null) return false;
        switch (b) {
            case Hints.A: return pressed(c, m.buttonA);
            case Hints.B: return pressed(c, m.buttonB);
            case Hints.X: return pressed(c, m.buttonX);
            case Hints.Y: return pressed(c, m.buttonY);
            case Hints.LB: return pressed(c, m.buttonL1);
            case Hints.RB: return pressed(c, m.buttonR1);
            case Hints.LT: return pressed(c, m.buttonL2) || m.buttonL2 == ControllerMapping.UNDEFINED && c.getAxis(4) > 0.5f;
            case Hints.RT: return pressed(c, m.buttonR2) || m.buttonR2 == ControllerMapping.UNDEFINED && c.getAxis(5) > 0.5f;
            case Hints.BACK: return pressed(c, m.buttonBack);
            case Hints.START: return pressed(c, m.buttonStart);
            case Hints.LS: return pressed(c, m.buttonLeftStick);
            case Hints.RS: return pressed(c, m.buttonRightStick);
            default: return false;
        }
    }

    static boolean pressed(Controller c, int index) {
        return index != ControllerMapping.UNDEFINED && c.getButton(index);
    }

    static float clamp(float v) {
        return v < -1f ? -1f : v > 1f ? 1f : v;
    }
}
