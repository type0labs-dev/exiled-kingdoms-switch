package com.badlogic.gdx.graphics.glutils;

import com.badlogic.gdx.Gdx;
import java.util.HashMap;

/**
 * Compiled GL programs reused by source (tools/deob/GdxPatch.java routes ShaderProgram.compileShaders/dispose here
 * and opens the fields it needs). box2dlights builds and disposes the same programs on every area: compiling and
 * linking them took a tenth of each transition on the Switch. A cached program is never deleted (a few, kept for the
 * session); disposing an instance that uses one only drops it from the managed list.
 */
public final class EkShaderCache {
    private EkShaderCache() { }

    private static final HashMap<String, int[]> PROGRAMS = new HashMap<String, int[]>(); // {program, vertex, fragment}

    static void compile (ShaderProgram shader, String vertex, String fragment) {
        String key = vertex + '\0' + fragment;
        int[] cached = PROGRAMS.get(key);
        if (cached != null) {
            shader.program = cached[0];
            shader.vertexShaderHandle = cached[1];
            shader.fragmentShaderHandle = cached[2];
            shader.isCompiled = true;
            return;
        }
        shader.compileShaders$gdx(vertex, fragment);
        if (shader.isCompiled)
            PROGRAMS.put(key, new int[] {shader.program, shader.vertexShaderHandle, shader.fragmentShaderHandle});
    }

    static void dispose (ShaderProgram shader) {
        int[] cached = PROGRAMS.get(shader.getVertexShaderSource() + '\0' + shader.getFragmentShaderSource());
        if (cached == null || cached[0] != shader.program) {
            shader.dispose$gdx();
            return;
        }
        Gdx.gl20.glUseProgram(0);
        if (ShaderProgram.shaders.get(Gdx.app) != null) ShaderProgram.shaders.get(Gdx.app).removeValue(shader, true);
    }
}
