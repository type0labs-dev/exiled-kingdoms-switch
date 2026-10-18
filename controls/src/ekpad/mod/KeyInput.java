package ekpad.mod;

import com.badlogic.gdx.Input;
import java.util.HashSet;
import java.util.Set;

/**
 * Wraps Gdx.input so pad buttons read as the game's keyboard bindings (interact, map, journal...) and the mod can
 * hide keys it consumed. A plain delegating class (generated from Input's signatures): Clearwing has no
 * java.lang.reflect.Proxy, which the desktop mod used.
 */
public class KeyInput implements Input {
    final Input real;
    final Set<Integer> held = new HashSet<Integer>(), just = new HashSet<Integer>();
    final Set<Integer> hidden = new HashSet<Integer>(); // real keys the mod consumed this frame

    KeyInput(Input real) { this.real = real; }

    Input proxy() { return this; }

    public float getAccelerometerX() { return real.getAccelerometerX(); }
    public float getAccelerometerY() { return real.getAccelerometerY(); }
    public float getAccelerometerZ() { return real.getAccelerometerZ(); }
    public float getGyroscopeX() { return real.getGyroscopeX(); }
    public float getGyroscopeY() { return real.getGyroscopeY(); }
    public float getGyroscopeZ() { return real.getGyroscopeZ(); }
    public int getMaxPointers() { return real.getMaxPointers(); }
    public int getX() { return real.getX(); }
    public int getX(int a0) { return real.getX(a0); }
    public int getDeltaX() { return real.getDeltaX(); }
    public int getDeltaX(int a0) { return real.getDeltaX(a0); }
    public int getY() { return real.getY(); }
    public int getY(int a0) { return real.getY(a0); }
    public int getDeltaY() { return real.getDeltaY(); }
    public int getDeltaY(int a0) { return real.getDeltaY(a0); }
    public boolean isTouched() { return real.isTouched(); }
    public boolean justTouched() { return real.justTouched(); }
    public boolean isTouched(int a0) { return real.isTouched(a0); }
    public float getPressure() { return real.getPressure(); }
    public float getPressure(int a0) { return real.getPressure(a0); }
    public boolean isButtonPressed(int a0) { return real.isButtonPressed(a0); }
    public boolean isButtonJustPressed(int a0) { return real.isButtonJustPressed(a0); }
    public boolean isKeyPressed(int a0) { return !hidden.contains(a0) && (held.contains(a0) || real.isKeyPressed(a0)); }
    public boolean isKeyJustPressed(int a0) { return !hidden.contains(a0) && (just.contains(a0) || real.isKeyJustPressed(a0)); }
    public void getTextInput(com.badlogic.gdx.Input.TextInputListener a0, java.lang.String a1, java.lang.String a2, java.lang.String a3) { real.getTextInput(a0, a1, a2, a3); }
    public void getTextInput(com.badlogic.gdx.Input.TextInputListener a0, java.lang.String a1, java.lang.String a2, java.lang.String a3, com.badlogic.gdx.Input.OnscreenKeyboardType a4) { real.getTextInput(a0, a1, a2, a3, a4); }
    public void setOnscreenKeyboardVisible(boolean a0) { real.setOnscreenKeyboardVisible(a0); }
    public void setOnscreenKeyboardVisible(boolean a0, com.badlogic.gdx.Input.OnscreenKeyboardType a1) { real.setOnscreenKeyboardVisible(a0, a1); }
    public void openTextInputField(com.badlogic.gdx.input.NativeInputConfiguration a0) { real.openTextInputField(a0); }
    public void closeTextInputField(boolean a0) { real.closeTextInputField(a0); }
    public void setKeyboardHeightObserver(com.badlogic.gdx.Input.KeyboardHeightObserver a0) { real.setKeyboardHeightObserver(a0); }
    public void vibrate(int a0) { real.vibrate(a0); }
    public void vibrate(int a0, boolean a1) { real.vibrate(a0, a1); }
    public void vibrate(int a0, int a1, boolean a2) { real.vibrate(a0, a1, a2); }
    public void vibrate(com.badlogic.gdx.Input.VibrationType a0) { real.vibrate(a0); }
    public float getAzimuth() { return real.getAzimuth(); }
    public float getPitch() { return real.getPitch(); }
    public float getRoll() { return real.getRoll(); }
    public void getRotationMatrix(float[] a0) { real.getRotationMatrix(a0); }
    public long getCurrentEventTime() { return real.getCurrentEventTime(); }
    public void setCatchKey(int a0, boolean a1) { real.setCatchKey(a0, a1); }
    public boolean isCatchKey(int a0) { return real.isCatchKey(a0); }
    public void setInputProcessor(com.badlogic.gdx.InputProcessor a0) { real.setInputProcessor(a0); }
    public com.badlogic.gdx.InputProcessor getInputProcessor() { return real.getInputProcessor(); }
    public boolean isPeripheralAvailable(com.badlogic.gdx.Input.Peripheral a0) { return real.isPeripheralAvailable(a0); }
    public int getRotation() { return real.getRotation(); }
    public com.badlogic.gdx.Input.Orientation getNativeOrientation() { return real.getNativeOrientation(); }
    public void setCursorCatched(boolean a0) { real.setCursorCatched(a0); }
    public boolean isCursorCatched() { return real.isCursorCatched(); }
    public void setCursorPosition(int a0, int a1) { real.setCursorPosition(a0, a1); }
}
