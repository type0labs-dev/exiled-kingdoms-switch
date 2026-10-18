package ekpad.mod;

import ekpad.Log;

import com.badlogic.gdx.controllers.Controller;

/** EKPAD_PROBE=1: logs raw button/axis/pov changes of the real pad, to learn each OS's layout. */
class Probe {
    static boolean[] lastBtn = new boolean[32];
    static int[] lastAxis = new int[16];
    static String lastPov = "";

    static void run() {
        Controller c = Pad.realPad();
        if (c == null) return;
        for (int i = 0; i < lastBtn.length; i++) {
            boolean b = c.getButton(i);
            if (b != lastBtn[i]) Log.i("btn " + i + (b ? " down" : " up"));
            lastBtn[i] = b;
        }
        for (int i = 0; i < lastAxis.length; i++) {
            int v = Math.round(c.getAxis(i) * 2); // -2..2, logs only coarse moves
            if (v != lastAxis[i]) Log.i("axis " + i + " = " + c.getAxis(i));
            lastAxis[i] = v;
        }
    }
}
