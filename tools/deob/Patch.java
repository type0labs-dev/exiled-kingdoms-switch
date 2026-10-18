import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;

/**
 * Moves the deobfuscated game from libGDX 1.9.12 to 1.13.1 / gdx-controllers 2.x (the versions SwitchGDX uses):
 * call sites of removed APIs are redirected to static helpers in ekcompat.Compat, game InputProcessors get the new
 * touchCancelled (delegating to touchUp), and the compat classes are added. Problems come from `Link`.
 *
 * Also wires the built-in controls: ExiledKingdoms gets render() { Pad.frame(); super.render(); Pad.after(); }.
 *
 * Usage: java Patch <in.jar> <out.jar> <classes dir>...   (compat shims, controls)
 */
public class Patch {
    /** "owner name desc" -> {static owner, name, desc}: receiver becomes the first argument */
    static final Map<String, String[]> REDIRECT = new HashMap<String, String[]>();
    static {
        REDIRECT.put("com/badlogic/gdx/Input setCatchBackKey (Z)V",
                new String[]{"ekcompat/Compat", "setCatchBackKey", "(Lcom/badlogic/gdx/Input;Z)V"});
        REDIRECT.put("com/badlogic/gdx/controllers/Controller getPov (I)Lcom/badlogic/gdx/controllers/PovDirection;",
                new String[]{"ekcompat/Compat", "getPov", "(Lcom/badlogic/gdx/controllers/Controller;I)Lcom/badlogic/gdx/controllers/PovDirection;"});
    }
    static int redirected, added;

    public static void main(String[] a) throws Exception {
        ZipFile in = new ZipFile(a[0]);
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(a[1]));
        for (Enumeration<? extends ZipEntry> en = in.entries(); en.hasMoreElements(); ) {
            ZipEntry ze = en.nextElement();
            byte[] data = Remap.readAll(in.getInputStream(ze));
            if (ze.getName().endsWith(".class")) data = patch(data);
            out.putNextEntry(new ZipEntry(ze.getName()));
            out.write(data);
            out.closeEntry();
        }
        for (int i = 2; i < a.length; i++) { File dir = new File(a[i]); addDir(out, dir, dir); }
        out.close();
        in.close();
        System.out.println("redirected " + redirected + " call sites, added " + added + " touchCancelled");
    }

    static byte[] patch(byte[] data) {
        ClassReader cr = new ClassReader(data);
        ClassWriter cw = new ClassWriter(0);
        final boolean[] inputProcessor = {false}, hasCancel = {false}, hasRender = {false};
        final String[] name = {null};
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            public void visit(int v, int acc, String n, String sig, String sup, String[] itf) {
                name[0] = n;
                if (itf != null && (acc & Opcodes.ACC_INTERFACE) == 0)
                    for (String i : itf) if (i.equals("com/badlogic/gdx/InputProcessor")) inputProcessor[0] = true;
                super.visit(v, acc, n, sig, sup, itf);
            }

            public MethodVisitor visitMethod(int acc, String n, String d, String s, String[] e) {
                if (name[0].equals("net/fdgames/ek/ExiledKingdoms") && n.equals("a") && d.equals("(Lnet/fdgames/ek/ControllerCommand;)Z")) {
                    // body -> ekpad.mod.Legacy.pressed (Clearwing miscompiles the original's switch-in-try)
                    MethodVisitor mv = super.visitMethod(acc, n, d, s, e);
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "ekpad/mod/Legacy", "pressed", d, false);
                    mv.visitInsn(Opcodes.IRETURN);
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();
                    System.out.println("replaced ExiledKingdoms.a(ControllerCommand)");
                    return null;
                }
                if (n.equals("touchCancelled") && d.equals("(IIII)Z")) hasCancel[0] = true;
                if (n.equals("render") && d.equals("()V")) hasRender[0] = true;
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(acc, n, d, s, e)) {
                    public void visitMethodInsn(int op, String owner, String mn, String md, boolean itf) {
                        String[] r = REDIRECT.get(owner + " " + mn + " " + md);
                        if (r != null) { redirected++; super.visitMethodInsn(Opcodes.INVOKESTATIC, r[0], r[1], r[2], false); }
                        else super.visitMethodInsn(op, owner, mn, md, itf);
                    }
                };
            }

            public void visitEnd() {
                if (inputProcessor[0] && !hasCancel[0]) { // new in InputProcessor: a cancelled touch ends like touchUp
                    MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "touchCancelled", "(IIII)Z", null, null);
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    for (int i = 1; i <= 4; i++) mv.visitVarInsn(Opcodes.ILOAD, i);
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, name[0], "touchUp", "(IIII)Z", false);
                    mv.visitInsn(Opcodes.IRETURN);
                    mv.visitMaxs(5, 5);
                    mv.visitEnd();
                    added++;
                }
                if (name[0].equals("net/fdgames/ek/ExiledKingdoms") && !hasRender[0]) { // controls hook (was the agent)
                    MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "render", "()V", null, null);
                    mv.visitCode();
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "ekpad/mod/Pad", "frame", "()V", false);
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "com/badlogic/gdx/Game", "render", "()V", false);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "ekpad/mod/Pad", "after", "()V", false);
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();
                    System.out.println("hooked ExiledKingdoms.render");
                }
                super.visitEnd();
            }
        }, 0);
        return cw.toByteArray();
    }

    static void addDir(ZipOutputStream out, File root, File f) throws IOException {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) addDir(out, root, k);
            return;
        }
        String rel = root.toURI().relativize(f.toURI()).getPath();
        out.putNextEntry(new ZipEntry(rel));
        FileInputStream in = new FileInputStream(f);
        out.write(Remap.readAll(in));
        in.close();
        out.closeEntry();
    }
}
