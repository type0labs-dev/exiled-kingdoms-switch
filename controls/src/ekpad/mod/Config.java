package ekpad.mod;

import ekpad.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/** Player options of the mod, in the game's own config folder (data/config/ekpad.properties). */
class Config {
    static final File FILE = new File("data/config/ekpad.properties");
    static final String[] STYLES = {"nintendo", "xbox", "playstation"};

    static String style = "nintendo"; // button icons when playing on a pad
    /**
     * Action -> physical button. Actions and buttons share the Hints ids (A=confirm, B=cancel, X=interact, Y=use,
     * LB/RB=prev/next item, LT/RT=skill modifiers, BACK=character, START=menu, LS=map, RS=journal), so the default
     * is identity and a button id names an Xbox-position button.
     */
    static final int ACTIONS = 12;
    static final int[] bind = defaults();

    static int[] defaults() {
        int[] b = new int[ACTIONS];
        for (int i = 0; i < ACTIONS; i++) b[i] = i;
        return b;
    }

    /** Put button on action; whatever action had that button takes the action's old one (no duplicates). */
    static void assign(int action, int button) {
        for (int i = 0; i < ACTIONS; i++) if (bind[i] == button) bind[i] = bind[action];
        bind[action] = button;
        save();
    }

    static void reset() {
        System.arraycopy(defaults(), 0, bind, 0, ACTIONS);
        save();
    }
    static boolean loaded;

    static void load() {
        loaded = true;
        if (!FILE.exists()) return;
        try {
            Properties p = new Properties();
            FileInputStream in = new FileInputStream(FILE);
            p.load(in);
            in.close();
            style = p.getProperty("style", style);
            for (int i = 0; i < ACTIONS; i++) bind[i] = Integer.parseInt(p.getProperty("bind." + i, String.valueOf(bind[i])));
        } catch (Exception e) {
            Log.e("config load", e);
        }
    }

    static void save() {
        try {
            Properties p = new Properties();
            p.setProperty("style", style);
            for (int i = 0; i < ACTIONS; i++) p.setProperty("bind." + i, String.valueOf(bind[i]));
            FILE.getParentFile().mkdirs();
            FileOutputStream out = new FileOutputStream(FILE);
            p.store(out, "Exiled Kingdoms gamepad mod");
            out.close();
        } catch (Exception e) {
            Log.e("config save", e);
        }
    }
}
