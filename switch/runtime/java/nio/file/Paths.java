package java.nio.file;

import java.io.File;

public final class Paths {
    private Paths() { }

    public static Path get(String first, String... more) {
        StringBuilder b = new StringBuilder(first);
        for (String m : more) b.append('/').append(m);
        final File f = new File(b.toString());
        return new Path() {
            public File toFile() { return f; }
            public String toString() { return f.getPath(); }
        };
    }
}
