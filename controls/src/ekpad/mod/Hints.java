package ekpad.mod;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import java.util.ArrayList;
import java.util.List;
import net.fdgames.assets.Assets;
import net.fdgames.ek.KeyboardConfig;
import net.fdgames.ek.Settings;

/**
 * Console-style button prompts: each frame the current context (menu, map, world...) lists what the buttons do,
 * and they are drawn as a bar at the bottom in the icons of the player's pad, or as key names when the keyboard was
 * the last thing used.
 */
class Hints {
    static final int A = 0, B = 1, X = 2, Y = 3, LB = 4, RB = 5, LT = 6, RT = 7, BACK = 8, START = 9, LS = 10, RS = 11,
            UPDOWN = 12, DPAD = 13, STICK = 14, LR = 15, DUP = 16, DDOWN = 17, DLEFT = 18, DRIGHT = 19, DLR = 20;
    static final int BAR = 0, WORLD = 1, LEGEND = 2;

    static final List<Object[]> items = new ArrayList<Object[]>(); // {button, text}
    static int layout = BAR;
    static boolean keyboard; // last input came from the keyboard

    static OrthographicCamera cam;
    static SpriteBatch batch;
    static ShapeRenderer shapes;
    static BitmapFont font;
    static final GlyphLayout gl = new GlyphLayout();

    static void clear() {
        items.clear();
        layout = BAR;
    }

    static void add(int button, String text) {
        items.add(new Object[]{button, text});
    }

    /** English / Portuguese, following the game's language setting (4 = pt). */
    static String t(String en, String pt) {
        try {
            return Settings.g() == 4 ? pt : en;
        } catch (Throwable e) {
            return en;
        }
    }

    // --- labels -------------------------------------------------------------------------------------------------

    static String padLabel(int b) {
        boolean n = Config.style.equals("nintendo"), ps = Config.style.equals("playstation");
        switch (b) {
            case A: return ps ? "x" : "A";
            case B: return ps ? "o" : "B";
            case X: return ps ? "[]" : "X";
            case Y: return ps ? "^" : "Y";
            case LB: return n ? "L" : ps ? "L1" : "LB";
            case RB: return n ? "R" : ps ? "R1" : "RB";
            case LT: return n ? "ZL" : ps ? "L2" : "LT";
            case RT: return n ? "ZR" : ps ? "R2" : "RT";
            case BACK: return n ? "-" : ps ? "Share" : "View";
            case START: return n ? "+" : ps ? "Options" : "Menu";
            case LS: return "L3";
            case RS: return "R3";
            case UPDOWN: return "↕";
            case DPAD: return "+";
            case LR: return padLabel(LB) + "+" + padLabel(RB);
            case DUP: return "↑";
            case DDOWN: return "↓";
            case DLR: return "←→";
            default: return "LS"; // STICK
        }
    }

    static String keyLabel(int b) {
        KeyboardConfig k = Settings.b;
        switch (b) {
            case A: return "Enter";
            case B: return "Esc";
            case X: return k == null ? "Tab" : keyName(k.interact.id);
            case Y: return "1-5";
            case BACK: return k == null ? "C" : keyName(k.character.id);
            case START: return "Esc";
            case LS: return k == null ? "M" : keyName(k.map.id);
            case RS: return k == null ? "L" : keyName(k.log.id);
            case UPDOWN: return "↑↓";
            case DPAD: case STICK: return "←↑↓→";
            case DUP: return k == null ? "R" : keyName(k.recovery.id);
            case DDOWN: return "1-5";
            default: return "?";
        }
    }

    /** libGDX Input.Keys codes to names (Input.Keys.toString was stripped by ProGuard). */
    static String keyName(int code) {
        if (code >= 29 && code <= 54) return String.valueOf((char) ('A' + code - 29));
        if (code >= 7 && code <= 16) return String.valueOf((char) ('0' + code - 7));
        if (code >= 244 && code <= 255) return "F" + (code - 243);
        switch (code) {
            case 61: return "Tab";
            case 62: return "Space";
            case 66: return "Enter";
            case 131: return "Esc";
            case 59: case 60: return "Shift";
            case 129: case 130: return "Ctrl";
            default: return "#" + code;
        }
    }

    static Color padColor(int b) {
        if (b < Config.ACTIONS && !keyboard) b = Config.bind[b];
        if (!Config.style.equals("xbox") || b > Y) return new Color(0.16f, 0.16f, 0.17f, 1f);
        switch (b) {
            case A: return new Color(0.36f, 0.73f, 0.31f, 1f);
            case B: return new Color(0.88f, 0.24f, 0.19f, 1f);
            case X: return new Color(0.18f, 0.48f, 0.84f, 1f);
            default: return new Color(0.95f, 0.76f, 0.09f, 1f);
        }
    }

    // --- drawing ------------------------------------------------------------------------------------------------

    static void draw() {
        if (items.isEmpty()) return;
        if (cam == null) {
            cam = new OrthographicCamera();
            batch = new SpriteBatch();
            shapes = new ShapeRenderer();
            font = Assets.b().get("menuLabelStrongLargeStyle", Label.LabelStyle.class).font;
        }
        Color before = font.getColor().cpy(); // the skin font is shared with the game's own drawing
        try { drawBar(); } finally { font.setColor(before); }
    }

    static void drawBar() {
        int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        float s = h / 1080f;
        cam.setToOrtho(false, w, h);
        cam.update();
        shapes.setProjectionMatrix(cam.combined);
        batch.setProjectionMatrix(cam.combined);

        // measure: glyph (circle or key cap) + label, spaced
        float r = 17 * s, gap = 10 * s, space = 34 * s;
        List<float[]> boxes = new ArrayList<float[]>(); // {glyphW, labelW}
        float total = 0;
        for (Object[] it : items) {
            String g = glyph((Integer) it[0]);
            gl.setText(font, g);
            float gw = round((Integer) it[0], g) ? 2 * r : gl.width + 16 * s;
            gl.setText(font, (String) it[1]);
            boxes.add(new float[]{gw, gl.width});
            total += gw + gap + gl.width + space;
        }
        total -= space;

        if (layout == LEGEND) { drawLegend(w, h, s, r); return; }
        float cy = layout == WORLD ? 105 * s : 34 * s, x = (w - total) / 2;

        Gdx.gl.glEnable(0x0BE2); // GL_BLEND
        Gdx.gl.glBlendFunc(0x0302, 0x0303); // SRC_ALPHA, ONE_MINUS_SRC_ALPHA
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.6f);
        if (layout == BAR) shapes.rect(0, 0, w, cy * 2);
        else shapes.rect(x - 18 * s, cy - r - 8 * s, total + 36 * s, 2 * r + 16 * s);
        float gx = x;
        for (int i = 0; i < items.size(); i++) {
            int b = (Integer) items.get(i)[0];
            float[] bx = boxes.get(i);
            if (round(b, glyph(b))) {
                shapes.setColor(0.85f, 0.85f, 0.85f, 1f);
                shapes.circle(gx + r, cy, r + 1.5f * s, 24);
                shapes.setColor(padColor(b));
                shapes.circle(gx + r, cy, r, 24);
                arrows(b, gx + r, cy, r);
            } else {
                shapes.setColor(0.85f, 0.85f, 0.85f, 1f);
                shapes.rect(gx, cy - r, bx[0], 2 * r);
                shapes.setColor(0.16f, 0.16f, 0.17f, 1f);
                shapes.rect(gx + 1.5f * s, cy - r + 1.5f * s, bx[0] - 3 * s, 2 * r - 3 * s);
                arrows(b, gx + bx[0] / 2, cy, r);
            }
            gx += bx[0] + gap + bx[1] + space;
        }
        shapes.end();

        batch.begin();
        gx = x;
        for (int i = 0; i < items.size(); i++) {
            int b = (Integer) items.get(i)[0];
            float[] bx = boxes.get(i);
            String g = glyph(b);
            gl.setText(font, g);
            if (!isArrow(b)) text(g, gx + (bx[0] - gl.width) / 2, cy + gl.height / 2, Color.WHITE);
            gl.setText(font, (String) items.get(i)[1]);
            text((String) items.get(i)[1], gx + bx[0] + gap, cy + gl.height / 2, Color.WHITE);
            gx += bx[0] + gap + bx[1] + space;
        }
        batch.end();
    }

    /** Hold Back/View in the world: the full control list. */
    static void drawLegend(int w, int h, float s, float r) {
        Gdx.gl.glEnable(0x0BE2);
        Gdx.gl.glBlendFunc(0x0302, 0x0303);
        float rowH = 46 * s, colW = 470 * s;
        int rows = (items.size() + 1) / 2;
        float bw = 2 * colW + 60 * s, bh = rows * rowH + 50 * s, bx0 = (w - bw) / 2, by0 = (h - bh) / 2;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.75f);
        shapes.rect(bx0, by0, bw, bh);
        for (int i = 0; i < items.size(); i++) {
            int b = (Integer) items.get(i)[0];
            float cx = bx0 + 30 * s + (i % 2) * colW + r, cy = by0 + bh - 25 * s - (i / 2) * rowH - rowH / 2;
            shapes.setColor(0.85f, 0.85f, 0.85f, 1f);
            if (round(b, glyph(b))) shapes.circle(cx, cy, r + 1.5f * s, 24);
            else shapes.rect(cx - r, cy - r, 3.2f * r, 2 * r);
            shapes.setColor(padColor(b));
            if (round(b, glyph(b))) shapes.circle(cx, cy, r, 24);
            else shapes.rect(cx - r + 1.5f * s, cy - r + 1.5f * s, 3.2f * r - 3 * s, 2 * r - 3 * s);
            arrows(b, round(b, glyph(b)) ? cx : cx + 0.6f * r, cy, r);
        }
        shapes.end();
        batch.begin();
        for (int i = 0; i < items.size(); i++) {
            int b = (Integer) items.get(i)[0];
            float cx = bx0 + 30 * s + (i % 2) * colW + r, cy = by0 + bh - 25 * s - (i / 2) * rowH - rowH / 2;
            String g = glyph(b);
            gl.setText(font, g);
            float gw = round(b, g) ? 2 * r : 3.2f * r;
            if (!isArrow(b)) text(g, cx - r + (gw - gl.width) / 2, cy + gl.height / 2, Color.WHITE);
            gl.setText(font, (String) items.get(i)[1]);
            text((String) items.get(i)[1], cx - r + gw + 14 * s, cy + gl.height / 2, Color.WHITE);
        }
        batch.end();
    }

    /** Actions (A..RS) show the button the player bound to them; the rest (stick, d-pad...) are fixed. */
    static String glyph(int b) {
        if (keyboard) return keyLabel(b);
        if (b == LR) return padLabel(Config.bind[LB]) + "+" + padLabel(Config.bind[RB]);
        return padLabel(b < Config.ACTIONS ? Config.bind[b] : b);
    }

    /** D-pad glyphs: the game's bitmap fonts have no arrows, so they are drawn as triangles (pad prompts only). */
    static boolean isArrow(int b) {
        return !keyboard && (b == DUP || b == DDOWN || b == DLR || b == UPDOWN);
    }

    static void arrows(int b, float cx, float cy, float r) {
        if (!isArrow(b)) return;
        shapes.setColor(Color.WHITE);
        float h = r * 0.5f, w = r * 0.45f;
        if (b == DUP) shapes.triangle(cx, cy + h, cx - w, cy - h * 0.7f, cx + w, cy - h * 0.7f);
        if (b == DDOWN) shapes.triangle(cx, cy - h, cx - w, cy + h * 0.7f, cx + w, cy + h * 0.7f);
        if (b == UPDOWN) {
            shapes.triangle(cx, cy + h * 1.2f, cx - w * 0.8f, cy + h * 0.2f, cx + w * 0.8f, cy + h * 0.2f);
            shapes.triangle(cx, cy - h * 1.2f, cx - w * 0.8f, cy - h * 0.2f, cx + w * 0.8f, cy - h * 0.2f);
        }
        if (b == DLR) {
            shapes.triangle(cx - r * 0.6f - w, cy, cx - r * 0.6f + h * 0.7f, cy + w, cx - r * 0.6f + h * 0.7f, cy - w);
            shapes.triangle(cx + r * 0.6f + w, cy, cx + r * 0.6f - h * 0.7f, cy + w, cx + r * 0.6f - h * 0.7f, cy - w);
        }
    }

    /** Face buttons and short labels sit in a circle; longer ones (key names, ZL, Options) in a key cap. */
    static boolean round(int b, String g) {
        return g.length() <= 1;
    }

    static void text(String str, float x, float y, Color c) {
        font.setColor(c);
        font.draw(batch, str, x, y);
    }
}
