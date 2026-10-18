package ekpad.mod;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerListener;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.controllers.ControllerPowerLevel;

/**
 * The controller the game sees (ExiledKingdoms.p). Its buttons are logical actions, not physical buttons:
 * Pad maps the real pad (ControllerMapping, rebinding, chords) onto these ids and Settings.a points at them.
 */
public class VirtualController implements Controller {
    static final int AX_X = 0, AX_Y = 1;
    static final int ACTION = 0, CANCEL = 1, SKILL1 = 2 /* ..SKILL8 = 9 */, USE = 10, NEXT = 11, PREV = 12,
            RECOVERY = 13, INV_TAKE = 14, INV_DROP = 15, COUNT = 16;

    final boolean[] btn = new boolean[COUNT];
    final float[] axis = new float[2];

    public boolean getButton(int i) { return i >= 0 && i < COUNT && btn[i]; }
    public float getAxis(int i) { return i >= 0 && i < 2 ? axis[i] : 0f; }
    public String getName() { return "ekpad virtual pad"; }
    public String getUniqueId() { return "ekpad-virtual"; }
    public int getMinButtonIndex() { return 0; }
    public int getMaxButtonIndex() { return COUNT - 1; }
    public int getAxisCount() { return 2; }
    public boolean isConnected() { return true; }
    public boolean canVibrate() { return false; }
    public boolean isVibrating() { return false; }
    public void startVibration(int duration, float strength) { }
    public void cancelVibration() { }
    public boolean supportsPlayerIndex() { return false; }
    public int getPlayerIndex() { return PLAYER_IDX_UNSET; }
    public void setPlayerIndex(int i) { }
    public ControllerMapping getMapping() { return null; } // the game's d-pad (Compat.getPov) then reads center
    public ControllerPowerLevel getPowerLevel() { return ControllerPowerLevel.POWER_UNKNOWN; }
    public void addListener(ControllerListener l) { }
    public void removeListener(ControllerListener l) { }
}
