package eksw;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import net.fdgames.Helpers.GameText;

/**
 * GameString.a(key, alt) with an index (tools/deob/SwitchPatch.java routes its body here). The game scans every text
 * of the language (thousands) for each lookup and lowercases the key once per entry: on the Switch the text lookups
 * of NPCs, skills and the HUD took a fifth of some frames. Same answer: the first entry whose tag equals the key
 * lowercased (Locale.ENGLISH), "" plus the same warning when missing. Rebuilt when a list grows (more files loaded)
 * or is a new one (language change).
 */
public final class Texts {
    private Texts() { }

    private static final IdentityHashMap<ArrayList<GameText>, HashMap<String, GameText>> INDEX =
            new IdentityHashMap<ArrayList<GameText>, HashMap<String, GameText>>();
    private static final IdentityHashMap<ArrayList<GameText>, Integer> SIZE = new IdentityHashMap<ArrayList<GameText>, Integer>();

    public static synchronized String lookup(ArrayList<GameText> main, ArrayList<GameText> alt, String key, boolean useAlt) {
        ArrayList<GameText> list = useAlt ? alt : main;
        String tag = key.toLowerCase(Locale.ENGLISH);
        HashMap<String, GameText> index = INDEX.get(list);
        Integer size = SIZE.get(list);
        if (index == null || size == null || size != list.size()) {
            index = new HashMap<String, GameText>(list.size() * 2);
            for (int i = 0, n = list.size(); i < n; i++) {
                GameText t = list.get(i);
                if (t.tag != null && !index.containsKey(t.tag)) index.put(t.tag, t);
            }
            INDEX.put(list, index);
            SIZE.put(list, list.size());
        }
        GameText t = index.get(tag);
        if (t != null) return t.get();
        System.out.println("WARNING - String not found: " + tag);
        return "";
    }
}
