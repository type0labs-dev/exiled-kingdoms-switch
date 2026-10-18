package eksw;

import com.badlogic.gdx.math.Rectangle;
import java.util.Locale;
import net.fdgames.ek.IPlatformResolver;

/**
 * The game's platform hooks without Steam. Mirrors the desktop one (net.fdgames.dr.desktop.a): achievements and
 * Steam callbacks become no-ops, the device id is fixed, language follows the system locale.
 */
public class SwitchResolver implements IPlatformResolver {
    public boolean a() { return false; }          // Steam running
    public void b() { }                           // Steam init
    public void a(String achievement) { }
    public void c() { } public void d() { } public void e() { } public void f() { } public void g() { }
    public void h() { } public void i() { } public void j() { } public void k() { } // k = Steam callbacks
    public String l() { return "99999"; }         // device id (desktop: last 5 hex of the MAC)
    public String m() { return "PC"; }            // platform name the game knows; keeps the desktop UI paths
    public int n() { return 1000; }
    public String o() {                           // language, same table as the desktop resolver
        String l = Locale.getDefault().getLanguage().toLowerCase();
        if (l.startsWith("es")) return "es";
        if (l.startsWith("pt")) return "pt";
        if (l.startsWith("de")) return "de";
        if (l.startsWith("ru") || l.equals("uk")) return "ru";
        if (l.startsWith("it")) return "it";
        if (l.startsWith("pl")) return "pl";
        if (l.startsWith("cs") || l.startsWith("sk")) return "cs";
        if (l.startsWith("tr")) return "tr";
        return "en";
    }
    public boolean p() { return false; }
    public void q() { } public void r() { } public void s() { } public void t() { } public void u() { }
    public boolean v() { return false; }
    public void w() { } public void x() { }
    public boolean y() { return false; }
    public boolean z() { return true; }
    public boolean A() { return true; }
    public void B() { }                           // Steam shutdown
    public Rectangle C() { return new Rectangle(); }
    public boolean D() { return true; }
    public void E() { }
    public String F() { return ""; }
}
