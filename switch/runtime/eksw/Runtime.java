package eksw;

import java.util.Properties;

/** Stand-ins for JDK calls Clearwing's runtime lacks; call sites redirected by tools/deob/SwitchPatch (Switch only). */
public final class Runtime {
    private Runtime() { }

    /** System.getProperty: SwitchGDX's platform has no user.dir; the game builds paths from it (init.ini, logs). */
    public static String getProperty(String key) {
        if ("user.dir".equals(key)) return ".";
        return System.getProperty(key);
    }

    public static String getProperty(String key, String def) {
        String v = getProperty(key);
        return v != null ? v : def;
    }

    /** System.getProperties(): the keys the game prints, via System.getProperty (which Clearwing has). */
    public static Properties getProperties() {
        Properties p = new Properties();
        for (String k : new String[]{"java.version", "java.vendor", "os.name", "os.arch", "os.version", "user.dir", "user.home", "file.separator"}) {
            String v = getProperty(k);
            if (v != null) p.setProperty(k, v);
        }
        return p;
    }

    /**
     * Properties.store(OutputStream, comment): the game saves init.ini with store(new FileOutputStream(..), null) and
     * never closes the stream. Under Clearwing the runtime's store threw an IOException, the file stayed empty (the
     * next boot closed silently) and the open FILE crashed fclose at exit. Plain key=value lines, then close.
     */
    public static void store(Properties p, java.io.OutputStream out, String comment) throws java.io.IOException {
        StringBuilder b = new StringBuilder();
        for (Object k : p.keySet()) b.append(k).append('=').append(p.getProperty(String.valueOf(k))).append('\n');
        try {
            out.write(b.toString().getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    // String.toLowerCase in game code (GameString looks every text key up lowercased, per call, every frame): keys are
    // mostly literals, the same object each time, so an identity cache returns the same lowercase String without
    // allocating. Bounded: cleared when full (dynamic strings would grow it forever).
    private static final java.util.IdentityHashMap<String, String> LOWER = new java.util.IdentityHashMap<String, String>();

    public static synchronized String toLowerCase(String s) { // the AssetManager loads on its own thread
        String l = LOWER.get(s);
        if (l == null) {
            if (LOWER.size() >= 4096) LOWER.clear();
            l = s.toLowerCase();
            LOWER.put(s, l);
        }
        return l;
    }

    /**
     * String.split in game code: Clearwing compiles a regex per call (conversations and data files split every line).
     * A pattern that is one literal char (",", "\n", ";", or an escaped one like "\\.") is split by hand with
     * split(regex, 0)'s rules: trailing empty strings dropped, no match = the whole string. Anything else: the regex.
     */
    public static String[] split(String s, String regex) {
        char c;
        if (regex.length() == 1 && ".$|()[{^?*+\\".indexOf(c = regex.charAt(0)) < 0) { }
        else if (regex.length() == 2 && regex.charAt(0) == '\\' && !Character.isLetterOrDigit(c = regex.charAt(1))) { }
        else return s.split(regex);
        java.util.ArrayList<String> parts = new java.util.ArrayList<String>();
        int start = 0;
        for (int i; (i = s.indexOf(c, start)) >= 0; start = i + 1) parts.add(s.substring(start, i));
        if (parts.isEmpty()) return new String[]{s};
        parts.add(s.substring(start));
        int n = parts.size();
        while (n > 0 && parts.get(n - 1).length() == 0) n--;
        return parts.subList(0, n).toArray(new String[n]);
    }

    // toLowerCase(Locale) through the same identity cache (keyed per locale: the game only passes Locale.ENGLISH)
    private static final java.util.IdentityHashMap<String, String> LOWER_LOCALE = new java.util.IdentityHashMap<String, String>();

    public static synchronized String toLowerCase(String s, java.util.Locale locale) {
        if (locale != java.util.Locale.ENGLISH) return s.toLowerCase(locale);
        String l = LOWER_LOCALE.get(s);
        if (l == null) {
            if (LOWER_LOCALE.size() >= 4096) LOWER_LOCALE.clear();
            l = s.toLowerCase(locale);
            LOWER_LOCALE.put(s, l);
        }
        return l;
    }
}
