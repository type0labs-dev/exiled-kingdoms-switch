package ekpad.mod;

import ekpad.Log;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import net.fdgames.assets.Assets;

/**
 * The mod's "Controller" screen, shown instead of the game's Controller Setup (which binds Android key codes and
 * means nothing with the mod): pick the button icons, rebind each action ("press a button..."), reset.
 * Built like the game's own windows (default skin window, "windowbg", "menuButton"), so the focus navigation and
 * the mouse both work on it.
 */
class ControllerWindow extends Window {
    static final String[][] ACTION_NAMES = {
            {"Attack / confirm", "Atacar / confirmar"}, {"Cancel / back", "Cancelar / voltar"},
            {"Interact", "Interagir"}, {"Use quick item", "Usar item rápido"},
            {"Previous item", "Item anterior"}, {"Next item", "Próximo item"},
            {"Skills 1-4 (hold)", "Skills 1-4 (segure)"}, {"Skills 5-8 (hold)", "Skills 5-8 (segure)"},
            {"Character", "Personagem"}, {"Menu", "Menu"}, {"Map", "Mapa"}, {"Journal", "Diário"}};

    static ControllerWindow instance;
    static int capturing = -1; // action waiting for its new button
    static final boolean[] physWas = new boolean[Config.ACTIONS];

    final TextButton style;
    final TextButton[] binds = new TextButton[Config.ACTIONS];

    /** The game opened its Controller Setup window: hide it and show ours on the same stage. */
    static void replace(Window gameWindow) {
        Stage stage = gameWindow.getStage();
        gameWindow.setVisible(false);
        show(stage);
        Log.i("controller window shown instead of " + gameWindow.getClass().getName());
    }

    /** Also opened straight from the Controller Setup button, which the game refuses while playing. */
    static void show(Stage stage) {
        if (instance == null) instance = new ControllerWindow();
        instance.refresh();
        instance.setVisible(true);
        stage.addActor(instance); // re-adding moves it to the top
    }

    ControllerWindow() {
        super("", Assets.b());
        setBackground(Assets.b().getDrawable("windowbg"));
        setMovable(false);
        setModal(true);
        float sc = 0.8f, bw = 230 * sc, bh = 46 * sc;
        center();
        pad(30 * sc);

        row().colspan(4);
        add(new Label(Hints.t("Controller", "Controle"), Assets.b(), "menuLabelStrongLargeStyle")).spaceBottom(16 * sc);
        row();
        add(new Label(Hints.t("Button icons", "Ícones dos botões"), Assets.b(), "menuLabelStrongStyle")).colspan(2);
        style = button("", bw, bh);
        add(style).colspan(2).width(bw).height(bh).space(4 * sc);
        style.addListener(new ChangeListener() {
            public void changed(ChangeListener.ChangeEvent e, Actor a) {
                int i = java.util.Arrays.asList(Config.STYLES).indexOf(Config.style);
                Config.style = Config.STYLES[(i + 1) % Config.STYLES.length];
                Config.save();
                refresh();
            }
        });
        for (int i = 0; i < Config.ACTIONS; i++) {
            if (i % 2 == 0) row();
            final int action = i;
            add(new Label(Hints.t(ACTION_NAMES[i][0], ACTION_NAMES[i][1]), Assets.b(), "menuLabelStyle")).right().padRight(8 * sc);
            binds[i] = button("", bw * 0.55f, bh);
            add(binds[i]).width(bw * 0.55f).height(bh).space(4 * sc).padRight(20 * sc);
            binds[i].addListener(new ChangeListener() {
                public void changed(ChangeListener.ChangeEvent e, Actor a) {
                    capturing = action;
                    refresh();
                }
            });
        }
        row().colspan(4).padTop(14 * sc);
        TextButton reset = button(Hints.t("Reset", "Padrão"), bw, bh), exit = button(Hints.t("Back", "Voltar"), bw, bh);
        add(reset).width(bw).height(bh).colspan(2).space(4 * sc);
        add(exit).width(bw).height(bh).colspan(2).space(4 * sc);
        reset.addListener(new ChangeListener() {
            public void changed(ChangeListener.ChangeEvent e, Actor a) {
                Config.reset();
                refresh();
            }
        });
        pack();
        setPosition((Gdx.graphics.getWidth() - getWidth()) / 2f, (Gdx.graphics.getHeight() - getHeight()) / 2f);
        exit.addListener(new ChangeListener() {
            public void changed(ChangeListener.ChangeEvent e, Actor a) {
                capturing = -1;
                setVisible(false);
            }
        });
    }

    static TextButton button(String text, float w, float h) {
        TextButton b = new TextButton(text, Assets.b(), "menuButton");
        b.setSize(w, h);
        return b;
    }

    void refresh() {
        style.setText(Config.style.substring(0, 1).toUpperCase() + Config.style.substring(1));
        boolean was = Hints.keyboard;
        Hints.keyboard = false; // this screen is about the pad
        for (int i = 0; i < Config.ACTIONS; i++)
            binds[i].setText(i == capturing ? Hints.t("Press...", "Aperte...") : Hints.glyph(i));
        Hints.keyboard = was;
    }

    static int swallow = -1; // the rebinding press, ignored until released

    /** While waiting for a button: the first newly pressed one is bound. True = this frame's pad input is consumed. */
    static boolean capture(Controller c) {
        boolean waiting = capturing >= 0 && instance != null && instance.isVisible();
        for (int b = 0; b < Config.ACTIONS; b++) {
            boolean now = Pad.phys(c, b);
            if (waiting && now && !physWas[b]) {
                Config.assign(capturing, b);
                Log.i("rebound action " + capturing + " to button " + b);
                capturing = -1;
                swallow = b;
                instance.refresh();
                waiting = false;
            }
            physWas[b] = now;
        }
        if (capturing >= 0 && !waiting) capturing = -1;
        if (swallow >= 0 && !Pad.phys(c, swallow)) swallow = -1;
        return waiting || capturing >= 0 || swallow >= 0;
    }
}
