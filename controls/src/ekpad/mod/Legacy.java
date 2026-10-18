package ekpad.mod;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.PovDirection;
import net.fdgames.UI.P;
import net.fdgames.ek.ControllerCommand;
import net.fdgames.ek.ControllerCommand$commandType;
import net.fdgames.ek.ExiledKingdoms;

/**
 * Same logic as the game's ExiledKingdoms.a(ControllerCommand) ("is this binding pressed"), without its
 * enum-switch-inside-try shape, which Clearwing 3.1.2 miscompiles (exception frame left on the stack -> abort).
 * Patch replaces the original body with a call to this.
 */
public final class Legacy {
    private Legacy() { }

    public static boolean pressed(ControllerCommand c) {
        if (P.h().booleanValue()) return false;
        ControllerCommand$commandType t = c.type;
        if (t == ControllerCommand$commandType.d && Gdx.input.isKeyJustPressed(c.id)) return true; // KEY
        Controller p = ExiledKingdoms.p;
        if (p == null) return false;
        if (t == ControllerCommand$commandType.c) return p.getButton(c.id);          // BUTTON
        if (t == ControllerCommand$commandType.a) return p.getAxis(c.id) < -0.2f;    // NEGATIVE_AXIS
        if (t == ControllerCommand$commandType.b) return p.getAxis(c.id) > 0.2f;     // POSITIVE_AXIS
        if (t == ControllerCommand$commandType.e)                                     // POV
            return ekcompat.Compat.getPov(p, c.povIndex) == PovDirection.north && c.id == 1;
        return false;
    }
}
