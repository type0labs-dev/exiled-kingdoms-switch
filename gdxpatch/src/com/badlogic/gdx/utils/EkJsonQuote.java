package com.badlogic.gdx.utils;

/**
 * JsonWriter.OutputType.quoteValue/quoteName without regex (tools/deob/GdxPatch.java points the originals here).
 * Clearwing compiles and runs java.util.regex through regexodus: one match per name and value made the autosave on
 * every area transition seconds slower on the Switch. Same logic and patterns as libGDX 1.13.1 JsonWriter.java:
 *   javascript   ^[a-zA-Z_$][a-zA-Z_$0-9]*$
 *   minimalName  ^[^":,}/ ][^:]*$
 *   minimalValue ^[^":,{\[\]/ ][^}\],]*$
 * (the buffers have their line breaks escaped already, so no raw newline reaches the checks). tests/JsonQuoteCheck.java
 */
public final class EkJsonQuote {
    private EkJsonQuote() { }

    static boolean javascript(CharSequence s) {
        int n = s.length();
        if (n == 0 || !jsStart(s.charAt(0))) return false;
        for (int i = 1; i < n; i++) {
            char c = s.charAt(i);
            if (!jsStart(c) && (c < '0' || c > '9')) return false;
        }
        return true;
    }

    private static boolean jsStart(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_' || c == '$';
    }

    static boolean minimalName(CharSequence s) {
        int n = s.length();
        if (n == 0 || "\":,}/ ".indexOf(s.charAt(0)) >= 0) return false;
        for (int i = 1; i < n; i++) if (s.charAt(i) == ':') return false;
        return true;
    }

    static boolean minimalValue(CharSequence s) {
        int n = s.length();
        if (n == 0 || "\":,{[]/ ".indexOf(s.charAt(0)) >= 0) return false;
        for (int i = 1; i < n; i++) {
            char c = s.charAt(i);
            if (c == '}' || c == ']' || c == ',') return false;
        }
        return true;
    }

    public static String quoteValue (JsonWriter.OutputType type, @Null Object value) {
        if (value == null) return "null";
        String string = value.toString();
        if (value instanceof Number || value instanceof Boolean) return string;
        StringBuilder buffer = new StringBuilder(string);
        buffer.replace('\\', "\\\\").replace('\r', "\\r").replace('\n', "\\n").replace('\t', "\\t");
        if (type == JsonWriter.OutputType.minimal && !string.equals("true") && !string.equals("false") && !string.equals("null")
            && !string.contains("//") && !string.contains("/*")) {
            int length = buffer.length();
            if (length > 0 && buffer.charAt(length - 1) != ' ' && minimalValue(buffer)) return buffer.toString();
        }
        return '"' + buffer.replace('"', "\\\"").toString() + '"';
    }

    public static String quoteName (JsonWriter.OutputType type, String value) {
        StringBuilder buffer = new StringBuilder(value);
        buffer.replace('\\', "\\\\").replace('\r', "\\r").replace('\n', "\\n").replace('\t', "\\t");
        switch (type) {
        case minimal:
            if (!value.contains("//") && !value.contains("/*") && minimalName(buffer)) return buffer.toString();
        case javascript:
            if (javascript(buffer)) return buffer.toString();
        default:
        }
        return '"' + buffer.replace('"', "\\\"").toString() + '"';
    }
}
