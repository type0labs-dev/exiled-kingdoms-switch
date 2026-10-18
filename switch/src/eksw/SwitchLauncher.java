package eksw;

import com.thelogicmaster.switchgdx.SwitchApplication;
import java.io.File;
import java.io.FileWriter;
import net.fdgames.ek.ExiledKingdoms;

/** Entry point for SwitchGDX (Switch NRO and its desktop/Linux build). */
public class SwitchLauncher {
    /** EKSW_PROBE=1: the steps of the game's Serializer.c() one by one (Clearwing runtime check). */
    static void probeIni() {
        try {
            File f = new File("." + "/init.ini");
            System.out.println("probe exists=" + f.exists() + " path=" + f);
            byte[] b = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(f.toString()));
            String s = new String(b);
            System.out.println("probe bytes=" + b.length + " text=[" + s.replace('\n', '|') + "]");
            java.util.Properties p = new java.util.Properties();
            p.load(new java.io.StringReader(s));
            System.out.println("probe props=" + p + " fullscreen=" + p.containsKey("fullscreen"));
        } catch (Throwable t) {
            System.out.println("probe failed: " + t);
        }
    }

    public static void main(String[] args) {
        // The game's constructor reads user.dir/init.ini and, if it has no size, asks Gdx.graphics, which doesn't
        // exist yet (on PC the Steam launcher always wrote the file first). Switch screen: 1280x720 fullscreen.
        // (the game's user.dir reads are redirected to eksw.Runtime, which answers ".")
        try {
            File ini = new File(".", "init.ini");
            // an empty/broken file (an interrupted save) made the next boot close silently: start from defaults
            if (!ini.exists() || ini.length() < 8) {
                FileWriter w = new FileWriter(ini);
                w.write("fullscreen=1\nwidth=1280\nheight=720\n");
                w.close();
            }
        } catch (Exception e) {
            System.out.println("init.ini: " + e);
        }
        if ("1".equals(System.getProperty("eksw.probe"))) probeIni(); // Clearwing runtime check
        new SwitchApplication(new ExiledKingdoms(new SwitchResolver()));
    }
}
