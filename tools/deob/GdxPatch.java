import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;

/**
 * Copy of gdx-1.13.1.jar with one behaviour of the game's embedded libGDX restored: GlyphLayout.setText stops wrapping
 * when the target width is at most 3 spaces (1.13.1 instead widens the target to 3 spaces and wraps there). The game
 * puts wrapped Labels in Table cells that lay them out at width 0, so on 1.13.1 every name and title broke into
 * columns of 2-3 letters (save slots, skill names).
 *
 * The 9-argument setText is renamed setText$gdx and a new setText does
 *   if (truncate == null && targetWidth <= font.getData().spaceXadvance * 3) wrap = false;
 * before calling it.
 *
 * Also (the helpers live in gdxpatch/src and are added to the jar by tools/build.sh):
 * - ShaderProgram: compileShaders/dispose renamed $gdx and routed through EkShaderCache (programs reused by source);
 *   the fields EkShaderCache needs lose `private`.
 * - JsonWriter$OutputType.quoteValue/quoteName bodies call EkJsonQuote (same rules without java.util.regex).
 *
 * Usage: java GdxPatch <gdx-1.13.1.jar> <out.jar>
 */
public class GdxPatch {
    static final String GL = "com/badlogic/gdx/graphics/g2d/GlyphLayout";
    static final String DESC = "(Lcom/badlogic/gdx/graphics/g2d/BitmapFont;Ljava/lang/CharSequence;IILcom/badlogic/gdx/graphics/Color;FIZLjava/lang/String;)V";

    public static void main(String[] a) throws Exception {
        ZipFile in = new ZipFile(a[0]);
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(a[1]));
        int done = 0;
        for (Enumeration<? extends ZipEntry> en = in.entries(); en.hasMoreElements(); ) {
            ZipEntry ze = en.nextElement();
            byte[] data = Remap.readAll(in.getInputStream(ze));
            if (ze.getName().equals(GL + ".class")) { data = patch(data); done++; }
            if (ze.getName().equals(SP + ".class")) { data = patchShader(data); done++; }
            if (ze.getName().equals(OT + ".class")) { data = patchQuote(data); done++; }
            out.putNextEntry(new ZipEntry(ze.getName()));
            out.write(data);
            out.closeEntry();
        }
        out.close();
        in.close();
        if (done != 3) throw new IllegalStateException("patched " + done + " of 3 classes in " + a[0]);
    }

    static final String SP = "com/badlogic/gdx/graphics/glutils/ShaderProgram";
    static final String OT = "com/badlogic/gdx/utils/JsonWriter$OutputType";

    static byte[] patchShader(byte[] data) {
        ClassReader cr = new ClassReader(data);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        final Set<String> open = new HashSet<String>(Arrays.asList("program", "vertexShaderHandle", "fragmentShaderHandle", "isCompiled", "shaders"));
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            public FieldVisitor visitField(int acc, String n, String d, String s, Object v) {
                return super.visitField(open.contains(n) ? acc & ~Opcodes.ACC_PRIVATE : acc, n, d, s, v);
            }

            public MethodVisitor visitMethod(int acc, String n, String d, String s, String[] e) {
                boolean compile = n.equals("compileShaders") && d.equals("(Ljava/lang/String;Ljava/lang/String;)V");
                boolean dispose = n.equals("dispose") && d.equals("()V");
                if (!compile && !dispose) return super.visitMethod(acc, n, d, s, e);
                MethodVisitor mv = super.visitMethod(acc, n, d, s, e); // the routed method, original name and access
                mv.visitCode();
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                if (compile) {
                    mv.visitVarInsn(Opcodes.ALOAD, 1);
                    mv.visitVarInsn(Opcodes.ALOAD, 2);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/badlogic/gdx/graphics/glutils/EkShaderCache", "compile",
                            "(L" + SP + ";Ljava/lang/String;Ljava/lang/String;)V", false);
                } else {
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/badlogic/gdx/graphics/glutils/EkShaderCache", "dispose", "(L" + SP + ";)V", false);
                }
                mv.visitInsn(Opcodes.RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
                // the original body, package access, for EkShaderCache to call
                return super.visitMethod(acc & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC), n + "$gdx", d, s, e);
            }
        }, 0);
        return cw.toByteArray();
    }

    static byte[] patchQuote(byte[] data) {
        ClassReader cr = new ClassReader(data);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            public MethodVisitor visitMethod(int acc, String n, String d, String s, String[] e) {
                boolean value = n.equals("quoteValue") && d.equals("(Ljava/lang/Object;)Ljava/lang/String;");
                boolean name = n.equals("quoteName") && d.equals("(Ljava/lang/String;)Ljava/lang/String;");
                if (!value && !name) return super.visitMethod(acc, n, d, s, e);
                MethodVisitor mv = super.visitMethod(acc, n, d, s, e);
                mv.visitCode();
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/badlogic/gdx/utils/EkJsonQuote", n,
                        "(L" + OT + ";" + (value ? "Ljava/lang/Object;" : "Ljava/lang/String;") + ")Ljava/lang/String;", false);
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
                return null; // drop the regex version
            }
        }, 0);
        return cw.toByteArray();
    }

    static byte[] patch(byte[] data) {
        ClassReader cr = new ClassReader(data);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        final int[] renamed = {0};
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            public MethodVisitor visitMethod(int acc, String n, String d, String s, String[] e) {
                if (!n.equals("setText") || !d.equals(DESC)) return super.visitMethod(acc, n, d, s, e);
                renamed[0]++;
                MethodVisitor mv = super.visitMethod(acc, "setText", d, s, e);
                mv.visitCode();
                Label call = new Label();
                mv.visitVarInsn(Opcodes.ALOAD, 9);
                mv.visitJumpInsn(Opcodes.IFNONNULL, call);
                mv.visitVarInsn(Opcodes.FLOAD, 6);
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "com/badlogic/gdx/graphics/g2d/BitmapFont", "getData",
                        "()Lcom/badlogic/gdx/graphics/g2d/BitmapFont$BitmapFontData;", false);
                mv.visitFieldInsn(Opcodes.GETFIELD, "com/badlogic/gdx/graphics/g2d/BitmapFont$BitmapFontData", "spaceXadvance", "F");
                mv.visitLdcInsn(3f);
                mv.visitInsn(Opcodes.FMUL);
                mv.visitInsn(Opcodes.FCMPG); // NaN keeps wrapping, as in the game's copy
                mv.visitJumpInsn(Opcodes.IFGT, call);
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitVarInsn(Opcodes.ISTORE, 8);
                mv.visitLabel(call);
                mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitVarInsn(Opcodes.ALOAD, 2);
                mv.visitVarInsn(Opcodes.ILOAD, 3);
                mv.visitVarInsn(Opcodes.ILOAD, 4);
                mv.visitVarInsn(Opcodes.ALOAD, 5);
                mv.visitVarInsn(Opcodes.FLOAD, 6);
                mv.visitVarInsn(Opcodes.ILOAD, 7);
                mv.visitVarInsn(Opcodes.ILOAD, 8);
                mv.visitVarInsn(Opcodes.ALOAD, 9);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, GL, "setText$gdx", d, false);
                mv.visitInsn(Opcodes.RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
                return super.visitMethod((acc & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PRIVATE, "setText$gdx", d, s, e);
            }
        }, 0);
        if (renamed[0] != 1) throw new IllegalStateException("GlyphLayout.setText" + DESC + " found " + renamed[0] + " times");
        return cw.toByteArray();
    }
}
