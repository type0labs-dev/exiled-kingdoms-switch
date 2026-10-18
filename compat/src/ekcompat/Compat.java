package ekcompat;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.controllers.PovDirection;

/** Static stand-ins for libGDX 1.9 APIs removed by 1.13.1 / gdx-controllers 2.x (call sites redirected by Patch). */
public final class Compat {
    private Compat() { }

    /** Input.setCatchBackKey(boolean) -> setCatchKey(Keys.BACK, boolean) */
    public static void setCatchBackKey(Input input, boolean c) {
        input.setCatchKey(Input.Keys.BACK, c);
    }

    /** Controller.getPov(int): 2.x reports the d-pad as mapped buttons. */
    public static PovDirection getPov(Controller c, int index) {
        ControllerMapping m = c.getMapping();
        if (m == null) return PovDirection.center;
        boolean u = c.getButton(m.buttonDpadUp), d = c.getButton(m.buttonDpadDown);
        boolean l = c.getButton(m.buttonDpadLeft), r = c.getButton(m.buttonDpadRight);
        if (u && r) return PovDirection.northEast;
        if (u && l) return PovDirection.northWest;
        if (d && r) return PovDirection.southEast;
        if (d && l) return PovDirection.southWest;
        if (u) return PovDirection.north;
        if (d) return PovDirection.south;
        if (r) return PovDirection.east;
        if (l) return PovDirection.west;
        return PovDirection.center;
    }
}
