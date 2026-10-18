package ekpad.mod;

import ekpad.Log;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * Cursor-free menu navigation over any scene2d screen (the gdx-controllerutils idea, DevilutionX-style):
 * a logical focus moves between clickable actors by direction, A taps the focused actor through the stage,
 * so the game's own click handlers run unchanged.
 */
class FocusNav {
    // conversation; the keyboard window's "press a key" capture (it must get Enter/Esc/arrows itself)
    static final String[] NATIVE = {"net.fdgames.UI.x", "net.fdgames.UI.aZ"};
    static final String GAME_CONTROLLER_SETUP = "net.fdgames.UI.MainMenu.K";
    // libGDX Input.Keys codes (the constants were inlined away by ProGuard)
    static final int K_UP = 19, K_DOWN = 20, K_LEFT = 21, K_RIGHT = 22, K_ENTER = 66, K_NUMPAD_ENTER = 160,
            K_ESCAPE = 131, K_BACKSPACE = 67;
    static final float FIRST_REPEAT = 0.35f, REPEAT = 0.12f;

    static Actor focus;
    static Group scope;
    static final java.util.Map<Group, Actor> remembered = new java.util.WeakHashMap<Group, Actor>();
    static int lastCount;
    static Stage stage;
    static List<Actor> items = new ArrayList<Actor>();
    static int heldDir = -1;
    static float repeatIn;
    static SpriteBatch batch;
    static Texture white;

    static final int NONE = 0, MENU = 1, MAP = 2;
    /** B found no Back/Close button in an in-game menu: let the game's own cancel handle it. */
    static boolean passCancel;
    /** The conversation or character window is up: the game navigates it, B/cancel closes it. */
    static boolean nativeOpen;

    /**
     * Who owns the input this frame: NONE (world), MENU (a window or a menu screen) or MAP (HUD map mode).
     * Stick and d-pad come separately: on the map the stick scrolls (Pad turns it into arrow keys).
     */
    static int update(float sx, float sy, float dx, float dy, boolean aPressed, boolean bPressed, boolean xPressed,
                      boolean yPressed)
            throws Exception {
        stage = null;
        passCancel = false;
        nativeOpen = false;
        InputProcessor ip = Pad.keys == null ? null : Pad.keys.real.getInputProcessor();
        boolean inGame = ip instanceof InputMultiplexer; // GameScreen: [screen, gestures, HUD stage]
        Stage s = stageOf(ip);
        if (s == null) return NONE;
        Group sc = topWindow(s);
        int mode = MENU;
        if (sc == null && !inGame) sc = s.getRoot();
        if (sc == null && Pad.hud() != null && Pad.hud().d() != 0) { sc = s.getRoot(); mode = MAP; }
        if (sc != null && isNative(sc)) nativeOpen = true;
        if (sc == null || nativeOpen) {
            if (focus != null) { Log.i("focus none"); focus = null; }
            scope = null;
            return NONE;
        }
        stage = s;
        // a closed window starts fresh next time it opens; only one covered by a dialog keeps its focus
        for (java.util.Iterator<Group> it = remembered.keySet().iterator(); it.hasNext(); ) {
            Group g = it.next();
            if (g == null || g.getStage() == null || !g.isVisible()) it.remove();
        }
        items.clear();
        collect(sc, items);
        if (sc != scope || items.size() != lastCount) logItems(sc);
        scope = sc;
        lastCount = items.size();

        // keyboard drives menus too: arrows move, Enter confirms, Esc goes back
        Input kb = Gdx.input; // the proxy: real keys + keys the pad (Start = Esc) or Dev inject
        boolean enter = kb.isKeyJustPressed(K_ENTER) || kb.isKeyJustPressed(K_NUMPAD_ENTER);
        boolean esc = kb.isKeyJustPressed(K_ESCAPE) || kb.isKeyJustPressed(K_BACKSPACE);
        if (s.getKeyboardFocus() instanceof TextField) {
            // typing a name: the field owns the keyboard; Enter/Esc only leave it (the pad keeps navigating)
            if (enter || kb.isKeyJustPressed(K_ESCAPE)) s.setKeyboardFocus(null);
            hide(K_ENTER, K_NUMPAD_ENTER, K_ESCAPE, K_BACKSPACE, K_UP, K_DOWN, K_LEFT, K_RIGHT);
            enter = esc = false;
        }
        aPressed |= enter;

        if (items.isEmpty()) { // "tap to continue" screens (intro, cutscene text): A/Enter taps the middle
            focus = null;
            Hints.add(Hints.A, Hints.t("Continue", "Continuar"));
            if (aPressed) {
                Log.i("tap screen center");
                int x = Gdx.graphics.getWidth() / 2, y = Gdx.graphics.getHeight() / 2;
                s.touchDown(x, y, 0, 0);
                s.touchUp(x, y, 0, 0);
            }
            return mode;
        }
        if (focus == null || !items.contains(focus)) {
            Actor back = remembered.get(sc); // returning from a dialog: focus where the player left it
            setFocus(back != null && items.contains(back) ? back : items.get(0));
        }
        if (Dev.tap != null) devTap();
        trackSlotActions();

        int dir = direction(dx, dy);
        if (dir < 0 && mode == MENU) dir = direction(sx, sy);
        if (dir < 0 && mode == MENU) dir = kb.isKeyPressed(K_UP) ? 0 : kb.isKeyPressed(K_DOWN) ? 1
                : kb.isKeyPressed(K_LEFT) ? 2 : kb.isKeyPressed(K_RIGHT) ? 3 : -1;
        if (dir < 0) {
            heldDir = -1;
        } else if (dir != heldDir) {
            heldDir = dir;
            repeatIn = FIRST_REPEAT;
            move(dir);
        } else if ((repeatIn -= Gdx.graphics.getDeltaTime()) <= 0) {
            repeatIn = REPEAT;
            move(dir);
        }
        if (xPressed) secondaryAction();
        if (yPressed && takeAll() != null) { // loot: Y takes everything (the game's "Take All" button)
            Log.i("take all");
            tap(takeAll());
        }
        if (aPressed) {
            activate();
            if (enter) hide(K_ENTER, K_NUMPAD_ENTER);
        } else if (bPressed || esc) {
            // in-game "Exit" means quit, so only unambiguous words there; menu windows may also use Exit/Sair
            Actor back = findBack(inGame || !(sc instanceof Window) ? STRONG : ANY);
            if (back != null) {
                setFocus(back);
                activate();
                if (esc) hide(K_ESCAPE, K_BACKSPACE); // the game must not also run its own Esc
            } else {
                passCancel = bPressed && inGame;
            }
        }
        menuHints(mode, inGame);
        return mode;
    }

    /** What A/B/X do on this screen, for the prompt bar. */
    static void menuHints(int mode, boolean inGame) {
        if (mode == MAP) Hints.add(Hints.STICK, Hints.t("Scroll", "Rolar"));
        if (focus == null) return;
        if (isSlot(focus) && focus == lastSlot && !slotActions.isEmpty()) {
            Hints.add(Hints.A, plain(text(slotActions.get(slotActions.size() - 1))));
            if (slotActions.size() > 1) Hints.add(Hints.X, plain(text(slotActions.get(0))));
        } else if (focus instanceof Slider) {
            Hints.add(Hints.DPAD, Hints.t("Adjust", "Ajustar"));
        } else if (focus instanceof TextField) {
            Hints.add(Hints.A, Hints.t("Type", "Digitar"));
        } else {
            Hints.add(Hints.A, isSlot(focus) ? Hints.t("Select", "Selecionar") : Hints.t("Choose", "Escolher"));
        }
        Actor all = takeAll();
        if (all != null) Hints.add(Hints.Y, plain(text(all)));
        Actor back = findBack(inGame || !(scope instanceof Window) ? STRONG : ANY);
        if (back != null) Hints.add(Hints.B, plain(text(back)));
        else if (inGame) Hints.add(Hints.B, Hints.t("Close", "Fechar"));
    }

    /** Button text without libGDX color markup ("[WHITE]Yes[]" -> "Yes"). */
    static String plain(String t) {
        if (t == null) return "";
        if (t.indexOf('[') < 0) return t.trim();
        // no regex: Clearwing compiles one per replaceAll, and this ran for every item of a window every frame
        StringBuilder b = new StringBuilder(t.length());
        for (int i = 0, n = t.length(); i < n; i++) {
            char c = t.charAt(i);
            if (c == '[') {
                int close = t.indexOf(']', i);
                if (close > 0) { i = close; continue; }
            }
            b.append(c);
        }
        return b.toString().trim();
    }

    static void hide(int... ks) {
        for (int k : ks) Pad.keys.hidden.add(k);
    }

    // the button has to start with the word ("Back to Game", "No"): option labels mention "no" too
    static final java.util.regex.Pattern STRONG = java.util.regex.Pattern.compile(
            "(?i)(cancel|back|close|no|resume|return|cancelar|voltar|fechar|n[aã]o)\\b.{0,16}");
    static final java.util.regex.Pattern ANY = java.util.regex.Pattern.compile(
            "(?i)(cancel|back|close|exit|no|resume|return|sair|cancelar|voltar|fechar|n[aã]o)\\b.{0,16}");

    /** The Cancel/Back/Close/No button of the current menu, if it has one. */
    // the loot window's "Take All" button, if this window has one: cached like findBack
    static Group allScope;
    static int allCount = -1;
    static Actor allFound;

    static Actor takeAll() {
        if (scope == allScope && items.size() == allCount && (allFound == null || items.contains(allFound))) return allFound;
        allScope = scope;
        allCount = items.size();
        allFound = null;
        String label = plain(net.fdgames.Helpers.GameString.a("TAKE_ALL"));
        for (Actor a : items) if (label.length() > 0 && label.equals(plain(text(a)))) allFound = a;
        return allFound;
    }

    // findBack runs every frame (hints, B): cached until the window or its item count changes (logItems' trigger)
    static Group backScope;
    static int backCount = -1;
    static java.util.regex.Pattern backWords;
    static Actor backFound;

    static Actor findBack(java.util.regex.Pattern words) {
        if (scope == backScope && items.size() == backCount && words == backWords
                && (backFound == null || items.contains(backFound))) return backFound;
        backScope = scope;
        backCount = items.size();
        backWords = words;
        backFound = findBackNow(words);
        return backFound;
    }

    static Actor findBackNow(java.util.regex.Pattern words) {
        for (int i = items.size() - 1; i >= 0; i--) {
            String t = text(items.get(i));
            if (t != null && words.matcher(plain(t)).matches()) return items.get(i);
        }
        return null;
    }

    static void logItems(Group sc) {
        StringBuilder sb = new StringBuilder("screen " + sc.getClass().getName() + ":");
        for (int i = 0; i < items.size(); i++) {
            Vector2 c = center(items.get(i));
            sb.append("\n    #").append(i).append(" @").append((int) c.x).append(',').append((int) c.y).append(' ').append(describe(items.get(i)));
        }
        Log.i(sb.toString());
    }

    static Stage stageOf(InputProcessor ip) throws Exception {
        if (ip instanceof Stage) return (Stage) ip;
        if (ip instanceof InputMultiplexer) {
            Array<InputProcessor> ps = ((InputMultiplexer) ip).getProcessors();
            for (int i = ps.size - 1; i >= 0; i--) if (ps.get(i) instanceof Stage) return (Stage) ps.get(i);
        }
        return null;
    }

    /** Topmost visible Window on the stage = the modal the player is looking at. */
    static Group topWindow(Stage s) {
        Array<Actor> kids = s.getRoot().getChildren();
        for (int i = kids.size - 1; i >= 0; i--) {
            Actor a = kids.get(i);
            if (!(a instanceof Window) || !a.isVisible()) continue;
            if (a.getClass().getName().equals(GAME_CONTROLLER_SETUP)) {
                ControllerWindow.replace((Window) a);
                return ControllerWindow.instance;
            }
            return (Group) a;
        }
        return null;
    }

    static boolean isNative(Actor a) {
        for (String n : NATIVE) if (a.getClass().getName().equals(n)) return true;
        return false;
    }

    static void collect(Group g, List<Actor> out) {
        Array<Actor> kids = g.getChildren();
        for (int i = 0; i < kids.size; i++) {
            Actor a = kids.get(i);
            if (!a.isVisible() || a.getTouchable() == Touchable.disabled) continue;
            if (widget(a)) { out.add(a); continue; }
            int before = out.size();
            if (a instanceof Group) collect((Group) a, out);
            // a group whose children aren't clickable may itself be (e.g. a skill icon built from image + label)
            if (out.size() == before && gameListener(a)) out.add(a);
        }
    }

    // actors the game hit-tests itself (no listener of their own): inventory/equipment/loot slots
    static final String[] SLOTS = {"net.fdgames.UI.a.E"};

    static boolean widget(Actor a) {
        if (!a.isTouchable()) return false;
        for (String n : SLOTS) if (a.getClass().getName().equals(n)) return true;
        if (a instanceof Button) return !((Button) a).isDisabled();
        return a instanceof Slider || a instanceof SelectBox;
    }

    /** A ClickListener, or any listener the game wrote itself (raw InputListener with touchDown, etc.). */
    static boolean gameListener(Actor a) {
        if (!a.isTouchable() || a instanceof Window) return false;
        Array<EventListener> ls = a.getListeners();
        for (int i = 0; i < ls.size; i++) {
            Object l = ls.get(i);
            if (l instanceof ClickListener || !l.getClass().getName().startsWith("com.badlogic.")) return true;
        }
        return false;
    }

    static int direction(float x, float y) { // 0 up, 1 down, 2 left, 3 right; y follows the pad (up < 0)
        if (Math.abs(x) < 0.5f && Math.abs(y) < 0.5f) return -1;
        if (Math.abs(y) >= Math.abs(x)) return y < 0 ? 0 : 1;
        return x < 0 ? 2 : 3;
    }

    static void move(int dir) {
        if (focus instanceof Slider && dir >= 2) {
            Slider sl = (Slider) focus;
            float step = Math.max(sl.getStepSize(), (sl.getMaxValue() - sl.getMinValue()) / 20f);
            sl.setValue(sl.getValue() + (dir == 3 ? step : -step));
            return;
        }
        float dx = dir == 2 ? -1 : dir == 3 ? 1 : 0, dy = dir == 0 ? 1 : dir == 1 ? -1 : 0; // stage y grows up
        Vector2 f = center(focus);
        Actor best = null;
        float bestScore = Float.MAX_VALUE;
        boolean bestInBeam = false;
        for (Actor c : items) {
            if (c == focus) continue;
            Vector2 p = center(c);
            float vx = p.x - f.x, vy = p.y - f.y;
            float along = vx * dx + vy * dy;
            if (along <= 1f) continue;
            float perp = Math.abs(vx * dy - vy * dx);
            // Android's focus "beam": a candidate overlapping the focus on the other axis (same row/column) wins
            float reach = (dx != 0 ? focus.getHeight() + c.getHeight() : focus.getWidth() + c.getWidth()) / 2f;
            boolean inBeam = perp < reach;
            float score = along * along + perp * perp * 9.6f; // sideways counts ~3x (controllerutils' 3.1 squared)
            if (inBeam && !bestInBeam || inBeam == bestInBeam && score < bestScore) {
                bestScore = score; best = c; bestInBeam = inBeam;
            }
        }
        if (best != null) setFocus(best);
    }

    // slot quick action: A selects an item (the game then shows its buttons: Take, Equip, Use...); A again presses
    // the first of those buttons, so "A A" takes/equips without walking the focus over to it
    static Actor lastSlot;
    static List<Actor> beforeTap = new ArrayList<Actor>(), slotActions = new ArrayList<Actor>();

    static boolean isSlot(Actor a) {
        for (String n : SLOTS) if (a.getClass().getName().equals(n)) return true;
        return false;
    }

    static void trackSlotActions() {
        slotActions.clear();
        if (lastSlot == null) return;
        if (!items.contains(lastSlot)) { lastSlot = null; return; }
        for (Actor a : items) if (a instanceof Button && !beforeTap.contains(a)) slotActions.add(a);
        java.util.Collections.sort(slotActions, new java.util.Comparator<Actor>() { // left to right
            public int compare(Actor p, Actor q) { return Float.compare(center(p).x, center(q).x); }
        });
    }

    /** X on a selected slot: the left action when there are two (Store, Drop, Sell...). */
    static void secondaryAction() {
        if (focus != lastSlot || slotActions.size() < 2) return;
        Actor act = slotActions.get(0);
        Log.i("slot action " + describe(act));
        tap(act);
        lastSlot = null;
    }

    static void activate() {
        if (isSlot(focus)) {
            if (focus == lastSlot && !slotActions.isEmpty()) {
                Actor act = slotActions.get(slotActions.size() - 1); // rightmost: Take, Equip, Use, Buy
                Log.i("slot action " + describe(act));
                tap(act);
                lastSlot = null;
                return;
            }
            lastSlot = focus;
            beforeTap = new ArrayList<Actor>(items);
        } else {
            lastSlot = null;
        }
        Log.i("tap " + describe(focus));
        if (plain(text(focus)).equals(plain(net.fdgames.Helpers.GameString.a("CONTROLLER_SETUP")))) {
            ControllerWindow.show(stage); // the mod's controller screen, in game too
            return;
        }
        if (focus instanceof TextField && Pad.CONSOLE) { // no keyboard to type with: the system's on-screen one
            final TextField field = (TextField) focus;
            Gdx.input.getTextInput(new Input.TextInputListener() {
                public void input(String text) {
                    field.setText(text);
                    field.fire(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent());
                    Log.i("text input: " + text.length() + " chars");
                }

                public void canceled() { }
            }, plain(net.fdgames.Helpers.GameString.a("NAME")), "", field.getText());
            return;
        }
        tap(focus);
    }

    static void tap(Actor a) {
        Vector2 p = stage.stageToScreenCoordinates(center(a));
        stage.touchDown((int) p.x, (int) p.y, 0, 0);
        stage.touchUp((int) p.x, (int) p.y, 0, 0);
    }

    /** Dev "tap <text>" or "tap #<index>": jump straight to an item and press it. */
    static void devTap() {
        String t = Dev.tap;
        Dev.tap = null;
        for (int i = 0; i < items.size(); i++) {
            Actor a = items.get(i);
            if (t.startsWith("#") ? t.equals("#" + i) : describe(a).toLowerCase().contains(t.toLowerCase())) {
                setFocus(a);
                activate();
                return;
            }
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < items.size(); i++) b.append(" | ").append(describe(items.get(i)));
        Log.i("dev tap: no item matches [" + t + "] (" + t.length() + " chars) among " + items.size() + ":" + b);
    }

    static void setFocus(Actor a) {
        focus = a;
        remembered.put(scope, a);
        Log.i("focus " + describe(a) + " in " + (scope == null ? "-" : scope.getClass().getName()) + " (" + items.size() + " items)");
    }

    static Vector2 center(Actor a) {
        return a.localToStageCoordinates(new Vector2(a.getWidth() / 2f, a.getHeight() / 2f));
    }

    static String describe(Actor a) {
        String t = text(a);
        return a.getClass().getName() + (t == null ? "" : " \"" + t + "\"");
    }

    static String text(Actor a) {
        if (a instanceof Label) return ((Label) a).getText().toString();
        if (a instanceof Group) {
            Array<Actor> kids = ((Group) a).getChildren();
            for (int i = 0; i < kids.size; i++) {
                String t = text(kids.get(i));
                if (t != null && t.length() > 0) return t;
            }
        }
        return null;
    }

    /** Called after the frame is drawn: gold frame around the focused actor. */
    static void draw() {
        if (focus == null || stage == null) return;
        if (batch == null) { // filled quads: ShapeRenderer's GL_LINES draw nothing on the Switch backend
            batch = new SpriteBatch();
            Pixmap px = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            px.setColor(Color.WHITE);
            px.fill();
            white = new Texture(px);
            px.dispose();
        }
        Vector2 p = focus.localToStageCoordinates(new Vector2(0, 0));
        float x = p.x - 3, y = p.y - 3, w = focus.getWidth() + 6, h = focus.getHeight() + 6, t = 3;
        batch.setProjectionMatrix(stage.getCamera().combined);
        batch.begin();
        batch.setColor(Color.GOLD);
        batch.draw(white, x, y, w, t);
        batch.draw(white, x, y + h - t, w, t);
        batch.draw(white, x, y, t, h);
        batch.draw(white, x + w - t, y, t, h);
        batch.end();
    }
}
