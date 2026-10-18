package ekpad;

import java.io.FileOutputStream;

public class Log {
    private static boolean started;

    /**
     * Open, append, close per line: Clearwing's writers only reach the disk on close (flush is a no-op), and on
     * Switch a file can't be reopened while another handle writes it.
     */
    public static synchronized void i(String msg) {
        String line = "[ekpad] " + msg;
        System.out.println(line);
        try {
            FileOutputStream out = new FileOutputStream(path(), started);
            started = true;
            out.write((line + "\n").getBytes("UTF-8"));
            out.close();
        } catch (Exception ignored) {
        }
    }

    static String path() {
        String p = System.getenv("EKPAD_LOG");
        if (p == null) p = System.getProperty("ekpad.log");
        return p != null ? p : "ekpad.log"; // working dir: the game folder on PC, the app's sdmc folder on Switch
    }

    public static void e(String what, Throwable t) {
        i("ERROR " + what + ": " + t);
        for (Throwable c = t.getCause(); c != null && c != t; c = c.getCause() == c ? null : c.getCause())
            i("  caused by: " + c);
        t.printStackTrace(); // readable frames (StackTraceElement.toString is not, under Clearwing)
    }
}
