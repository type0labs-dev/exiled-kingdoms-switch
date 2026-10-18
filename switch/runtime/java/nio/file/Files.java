package java.nio.file;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class Files {
    private Files() { }

    public static byte[] readAllBytes(Path p) throws IOException {
        InputStream in = new FileInputStream(p.toFile());
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    public static Path write(Path p, byte[] bytes, OpenOption... options) throws IOException {
        boolean append = false;
        for (OpenOption o : options) if (o == StandardOpenOption.APPEND) append = true;
        FileOutputStream out = new FileOutputStream(p.toFile(), append);
        try {
            out.write(bytes);
        } finally {
            out.close();
        }
        return p;
    }
}
