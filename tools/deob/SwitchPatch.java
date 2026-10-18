import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;

/**
 * Switch-only call-site redirects for JDK methods Clearwing's runtime lacks, applied in place to the class
 * directory handed to the transpiler (the desktop jar keeps the real calls). Also strips javax.annotation (JSR305)
 * annotations: the jar's copy is obfuscated down to class names, and they mean nothing at run time.
 * With -Dtrace.catch=true (debug builds), every catch handler in game classes first prints its exception's stack
 * trace: the game swallows exceptions and only logs getMessage().
 * Usage: java SwitchPatch <classes dir>
 */
public class SwitchPatch {
    static final Map<String, String[]> REDIRECT = new HashMap<String, String[]>();
    static {
        REDIRECT.put("java/lang/String split (Ljava/lang/String;)[Ljava/lang/String;", new String[]{"eksw/Runtime", "split", "(Ljava/lang/String;Ljava/lang/String;)[Ljava/lang/String;"});
        REDIRECT.put("java/lang/String toLowerCase (Ljava/util/Locale;)Ljava/lang/String;", new String[]{"eksw/Runtime", "toLowerCase", "(Ljava/lang/String;Ljava/util/Locale;)Ljava/lang/String;"});
        REDIRECT.put("java/lang/String toLowerCase ()Ljava/lang/String;", new String[]{"eksw/Runtime", "toLowerCase", "(Ljava/lang/String;)Ljava/lang/String;"});
        REDIRECT.put("java/util/Properties store (Ljava/io/OutputStream;Ljava/lang/String;)V", new String[]{"eksw/Runtime", "store", "(Ljava/util/Properties;Ljava/io/OutputStream;Ljava/lang/String;)V"});
        REDIRECT.put("java/lang/System getProperties ()Ljava/util/Properties;", new String[]{"eksw/Runtime", "getProperties", "()Ljava/util/Properties;"});
        REDIRECT.put("java/lang/System getProperty (Ljava/lang/String;)Ljava/lang/String;", new String[]{"eksw/Runtime", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;"});
        REDIRECT.put("java/lang/System getProperty (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", new String[]{"eksw/Runtime", "getProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"});
    }
    static int n;

    static boolean jsr(String desc) {
        if (!desc.startsWith("Ljavax/annotation/")) return false;
        n++;
        return true;
    }

    static AnnotationVisitor strip() { return null; }

    /** At each catch handler's first instruction (exception on the stack): dup + printStackTrace. */
    static class CatchTracer extends MethodVisitor {
        final Set<Label> handlers = new HashSet<Label>();
        boolean pending;

        CatchTracer(MethodVisitor mv) { super(Opcodes.ASM9, mv); }

        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            handlers.add(handler);
            super.visitTryCatchBlock(start, end, handler, type);
        }

        public void visitLabel(Label l) {
            super.visitLabel(l);
            if (handlers.contains(l)) pending = true;
        }

        void before() {
            if (!pending) return;
            pending = false;
            super.visitInsn(Opcodes.DUP);
            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable", "printStackTrace", "()V", false);
        }

        public void visitInsn(int o) { before(); super.visitInsn(o); }
        public void visitIntInsn(int o, int v) { before(); super.visitIntInsn(o, v); }
        public void visitVarInsn(int o, int v) { before(); super.visitVarInsn(o, v); }
        public void visitTypeInsn(int o, String t) { before(); super.visitTypeInsn(o, t); }
        public void visitFieldInsn(int o, String a, String b, String c) { before(); super.visitFieldInsn(o, a, b, c); }
        public void visitMethodInsn(int o, String a, String b, String c, boolean i) { before(); super.visitMethodInsn(o, a, b, c, i); }
        public void visitJumpInsn(int o, Label l) { before(); super.visitJumpInsn(o, l); }
        public void visitLdcInsn(Object v) { before(); super.visitLdcInsn(v); }
        public void visitIincInsn(int v, int i) { before(); super.visitIincInsn(v, i); }
        public void visitTableSwitchInsn(int a, int b, Label d, Label... l) { before(); super.visitTableSwitchInsn(a, b, d, l); }
        public void visitLookupSwitchInsn(Label d, int[] k, Label[] l) { before(); super.visitLookupSwitchInsn(d, k, l); }
        public void visitMultiANewArrayInsn(String d, int n) { before(); super.visitMultiANewArrayInsn(d, n); }
    }

    public static void main(String[] a) throws Exception {
        final List<Path> files = new ArrayList<Path>();
        Files.walk(Paths.get(a[0])).filter(p -> p.toString().endsWith(".class")).forEach(files::add);
        for (Path f : files) {
            byte[] in = Files.readAllBytes(f);
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            final boolean trace = Boolean.getBoolean("trace.catch") && f.toString().contains("net/fdgames/");
            final int before = n;
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9, cw) {
                public AnnotationVisitor visitAnnotation(String d, boolean v) { return jsr(d) ? strip() : super.visitAnnotation(d, v); }
                public FieldVisitor visitField(int acc, String nm, String d, String s, Object val) {
                    return new FieldVisitor(Opcodes.ASM9, super.visitField(acc, nm, d, s, val)) {
                        public AnnotationVisitor visitAnnotation(String ad, boolean v) { return jsr(ad) ? strip() : super.visitAnnotation(ad, v); }
                    };
                }
                public MethodVisitor visitMethod(int acc, String nm, String d, String s, String[] e) {
                    MethodVisitor base = super.visitMethod(acc, nm, d, s, e);
                    if (f.endsWith(Paths.get("net/fdgames/TiledMap/a.class")) && nm.equals("g") && d.equals("()V")) {
                        // The map renderer's sprite buckets (96x96 lists) rebuild when GameLevel.d() > t + 0.2 s; g()
                        // zeroes t, and GameLevelRenderer calls it every frame while the conversation panel is open
                        // (the camera zooms in): a full rebuild per frame, 4-5 fps on the console. Now g() forces a
                        // rebuild only if the last one is over 0.1 s old:
                        //   if (GameLevel.d() - t > 0.1f) t = 0;
                        n++;
                        Label skip = new Label();
                        base.visitCode();
                        base.visitMethodInsn(Opcodes.INVOKESTATIC, "net/fdgames/GameLevel/GameLevel", "d", "()F", false);
                        base.visitVarInsn(Opcodes.ALOAD, 0);
                        base.visitFieldInsn(Opcodes.GETFIELD, "net/fdgames/TiledMap/a", "t", "F");
                        base.visitInsn(Opcodes.FSUB);
                        base.visitLdcInsn(0.1f);
                        base.visitInsn(Opcodes.FCMPL);
                        base.visitJumpInsn(Opcodes.IFLE, skip);
                        base.visitVarInsn(Opcodes.ALOAD, 0);
                        base.visitInsn(Opcodes.FCONST_0);
                        base.visitFieldInsn(Opcodes.PUTFIELD, "net/fdgames/TiledMap/a", "t", "F");
                        base.visitLabel(skip);
                        base.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                        base.visitInsn(Opcodes.RETURN);
                        base.visitMaxs(0, 0);
                        base.visitEnd();
                        return null; // drop the original body
                    }
                    if (f.endsWith(Paths.get("net/fdgames/Helpers/GameString.class")) && nm.equals("a") && d.equals("(Ljava/lang/String;Z)Ljava/lang/String;")) {
                        // text lookup: linear scan + a toLowerCase per entry -> eksw.Texts.lookup (HashMap index)
                        n++;
                        base.visitCode();
                        base.visitFieldInsn(Opcodes.GETSTATIC, "net/fdgames/Helpers/GameString", "a", "Ljava/util/ArrayList;");
                        base.visitFieldInsn(Opcodes.GETSTATIC, "net/fdgames/Helpers/GameString", "b", "Ljava/util/ArrayList;");
                        base.visitVarInsn(Opcodes.ALOAD, 0);
                        base.visitVarInsn(Opcodes.ILOAD, 1);
                        base.visitMethodInsn(Opcodes.INVOKESTATIC, "eksw/Texts", "lookup",
                                "(Ljava/util/ArrayList;Ljava/util/ArrayList;Ljava/lang/String;Z)Ljava/lang/String;", false);
                        base.visitInsn(Opcodes.ARETURN);
                        base.visitMaxs(0, 0);
                        base.visitEnd();
                        return null;
                    }
                    if (trace) base = new CatchTracer(base);
                    return new MethodVisitor(Opcodes.ASM9, base) {
                        public AnnotationVisitor visitAnnotation(String ad, boolean v) { return jsr(ad) ? strip() : super.visitAnnotation(ad, v); }
                        public AnnotationVisitor visitParameterAnnotation(int p, String ad, boolean v) { return jsr(ad) ? strip() : super.visitParameterAnnotation(p, ad, v); }
                        public void visitMethodInsn(int op, String owner, String mn, String md, boolean itf) {
                            String[] r = REDIRECT.get(owner + " " + mn + " " + md);
                            if (r != null) { n++; super.visitMethodInsn(Opcodes.INVOKESTATIC, r[0], r[1], r[2], false); }
                            else super.visitMethodInsn(op, owner, mn, md, itf);
                        }
                    };
                }
            }, 0);
            if (n != before || trace) Files.write(f, cw.toByteArray());
        }
        System.out.println("switch redirects: " + n);
    }
}
