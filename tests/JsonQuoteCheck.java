// Run: javac -d /tmp/jq -cp work/gdx-1.13.1.jar tests/JsonQuoteCheck.java && java -cp /tmp/jq:work/gdx-1.13.1.jar com.badlogic.gdx.utils.JsonQuoteCheck [save.json...]
package com.badlogic.gdx.utils;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** EkJsonQuote's hand-written checks against libGDX 1.13.1's regexes: every string of the given saves + random ones. */
public class JsonQuoteCheck {
    static final Pattern JS = Pattern.compile("^[a-zA-Z_$][a-zA-Z_$0-9]*$");
    static final Pattern NAME = Pattern.compile("^[^\":,}/ ][^:]*$");
    static final Pattern VALUE = Pattern.compile("^[^\":,{\\[\\]/ ][^}\\],]*$");

    public static void main(String[] a) throws Exception {
        List<String> corpus = new ArrayList<String>();
        for (String f : a) {
            java.util.regex.Matcher m = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"|([A-Za-z_$][^:,{}\\[\\]\\n]*)").matcher(new String(Files.readAllBytes(Paths.get(f)), "UTF-8"));
            while (m.find()) corpus.add(m.group(1) != null ? m.group(1) : m.group(2));
        }
        Random r = new Random(1);
        String alphabet = "aZ_$09 \":,{}[]/:\\\\.-#";
        for (int i = 0; i < 200000; i++) {
            StringBuilder b = new StringBuilder();
            for (int n = r.nextInt(6); n > 0; n--) b.append(alphabet.charAt(r.nextInt(alphabet.length())));
            corpus.add(b.toString());
        }
        for (String s : corpus) {
            check("javascript", s, JS.matcher(s).matches(), EkJsonQuote.javascript(s));
            check("minimalName", s, NAME.matcher(s).matches(), EkJsonQuote.minimalName(s));
            check("minimalValue", s, VALUE.matcher(s).matches(), EkJsonQuote.minimalValue(s));
        }
        System.out.println("json quote ok (" + corpus.size() + " strings)");
    }

    static void check(String what, String s, boolean want, boolean got) {
        if (want != got) throw new AssertionError(what + " [" + s + "]: regex " + want + ", EkJsonQuote " + got);
    }
}
