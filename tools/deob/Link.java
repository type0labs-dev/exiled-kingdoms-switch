import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;

/**
 * Static link check of the game against a libGDX version: every class/field/method it references in
 * com.badlogic.gdx.* must resolve (through the target hierarchy), and every concrete game class must implement the
 * abstract methods its libGDX supertypes declare in that version. JDK references are not checked.
 *
 * Usage: java Link <game.jar> <target libGDX jars...>
 */
public class Link {
    static final Map<String, String[]> supers = new HashMap<String, String[]>();
    static final Map<String, Set<String>> members = new HashMap<String, Set<String>>(); // "m name desc" / "f name desc"
    static final Map<String, Set<String>> abstracts = new HashMap<String, Set<String>>();
    static final Map<String, Integer> access = new HashMap<String, Integer>();
    static final Set<String> game = new HashSet<String>();
    static final TreeMap<String, TreeSet<String>> problems = new TreeMap<String, TreeSet<String>>();

    public static void main(String[] a) throws Exception {
        index(a[0], true);
        for (int i = 1; i < a.length; i++) index(a[i], false);
        ZipFile z = new ZipFile(a[0]);
        for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements(); ) {
            ZipEntry ze = en.nextElement();
            if (!ze.getName().endsWith(".class")) continue;
            final ClassReader cr = new ClassReader(z.getInputStream(ze));
            final String user = cr.getClassName();
            cr.accept(new ClassVisitor(Opcodes.ASM9) {
                public MethodVisitor visitMethod(int acc, String n, String d, String s, String[] e) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        public void visitFieldInsn(int op, String owner, String name, String desc) { check("f", owner, name, desc, user); }
                        public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) { check("m", owner, name, desc, user); }
                        public void visitTypeInsn(int op, String type) { checkClass(type, user); }
                    };
                }
            }, 0);
            checkAbstracts(user);
        }
        z.close();
        int n = 0;
        for (Map.Entry<String, TreeSet<String>> e : problems.entrySet()) {
            n++;
            System.out.println(e.getKey() + "   <- " + e.getValue().size() + " user(s), e.g. " + e.getValue().first());
        }
        System.out.println(n + " problems");
    }

    static boolean gdx(String c) { return c.startsWith("com/badlogic/gdx/") && !c.startsWith("com/badlogic/gdx/pay/"); }

    static void problem(String what, String user) {
        if (!problems.containsKey(what)) problems.put(what, new TreeSet<String>());
        problems.get(what).add(user);
    }

    static void checkClass(String c, String user) {
        while (c.startsWith("[")) c = c.substring(1);
        if (c.startsWith("L") && c.endsWith(";")) c = c.substring(1, c.length() - 1);
        if (gdx(c) && !members.containsKey(c)) problem("missing class " + c, user);
    }

    static void check(String kind, String owner, String name, String desc, String user) {
        if (owner.startsWith("[")) return;
        if (!gdx(owner) && !game.contains(owner)) return;
        if (gdx(owner) && !members.containsKey(owner)) { problem("missing class " + owner, user); return; }
        if (resolves(kind, owner, name, desc, new HashSet<String>())) return;
        if (!gdx(owner) && !touchesGdx(owner, new HashSet<String>())) return;
        problem("missing " + (kind.equals("m") ? "method " : "field ") + owner + "." + name + " " + desc, user);
    }

    static boolean touchesGdx(String c, Set<String> seen) {
        if (c == null || !seen.add(c)) return false;
        if (gdx(c)) return true;
        String[] s = supers.get(c);
        if (s != null) for (String x : s) if (touchesGdx(x, seen)) return true;
        return false;
    }

    static boolean resolves(String kind, String c, String name, String desc, Set<String> seen) {
        if (c == null || !seen.add(c)) return false;
        Set<String> m = members.get(c);
        if (m == null) return !gdx(c) && !game.contains(c) && jdkHas(kind, c, name, desc);
        if (m.contains(kind + " " + name + " " + desc)) return true;
        String[] s = supers.get(c);
        if (s != null) for (String x : s) if (resolves(kind, x, name, desc, seen)) return true;
        return false;
    }

    /** Ask the host JDK (by reflection) whether a JDK class declares or inherits the member. */
    static boolean jdkHas(String kind, String c, String name, String desc) {
        try {
            Class<?> k = Class.forName(c.replace('/', '.'), false, Link.class.getClassLoader());
            if (kind.equals("f")) {
                for (Class<?> x = k; x != null; x = x.getSuperclass())
                    for (java.lang.reflect.Field f : x.getDeclaredFields()) if (f.getName().equals(name)) return true;
                return false;
            }
            for (Class<?> x = k; x != null; x = x.getSuperclass())
                for (java.lang.reflect.Method mm : x.getDeclaredMethods())
                    if (mm.getName().equals(name) && Type.getMethodDescriptor(mm).equals(desc)) return true;
            for (Class<?> i : k.getInterfaces())
                if (jdkHas(kind, i.getName().replace('.', '/'), name, desc)) return true;
            return name.equals("<init>");
        } catch (Throwable t) {
            return true; // unknown to this JDK: don't guess
        }
    }

    /** A concrete game class must implement every abstract libGDX method it inherits in the target version. */
    static void checkAbstracts(String c) {
        Integer acc = access.get(c);
        if (acc == null || (acc & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE)) != 0) return;
        Set<String> needed = new TreeSet<String>();
        collectAbstracts(c, needed, new HashSet<String>());
        for (String m : needed) {
            String[] p = m.split(" ");
            if (!implemented(c, p[1], p[2], new HashSet<String>())) problem("unimplemented " + p[0] + "." + p[1] + p[2], c);
        }
    }

    static void collectAbstracts(String c, Set<String> out, Set<String> seen) {
        if (c == null || !seen.add(c)) return;
        if (gdx(c) && abstracts.containsKey(c)) for (String m : abstracts.get(c)) out.add(c + " " + m);
        String[] s = supers.get(c);
        if (s != null) for (String x : s) collectAbstracts(x, out, seen);
    }

    static boolean implemented(String c, String name, String desc, Set<String> seen) {
        if (c == null || !seen.add(c)) return false;
        Set<String> m = members.get(c);
        if (m != null && m.contains("c " + name + " " + desc)) return true; // concrete (or default) here
        String[] s = supers.get(c);
        if (s != null) for (String x : s) if (implemented(x, name, desc, seen)) return true;
        return false;
    }

    static void index(String jar, boolean isGame) throws IOException {
        ZipFile z = new ZipFile(jar);
        for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements(); ) {
            ZipEntry ze = en.nextElement();
            if (!ze.getName().endsWith(".class")) continue;
            final Set<String> mem = new HashSet<String>(), abs = new HashSet<String>();
            final String[] name = new String[1];
            new ClassReader(z.getInputStream(ze)).accept(new ClassVisitor(Opcodes.ASM9) {
                public void visit(int v, int acc, String n, String sig, String sup, String[] itf) {
                    name[0] = n;
                    access.put(n, acc);
                    List<String> l = new ArrayList<String>();
                    if (sup != null) l.add(sup);
                    if (itf != null) l.addAll(Arrays.asList(itf));
                    supers.put(n, l.toArray(new String[0]));
                }
                public MethodVisitor visitMethod(int acc, String n, String d, String s, String[] e) {
                    mem.add("m " + n + " " + d);
                    if ((acc & Opcodes.ACC_ABSTRACT) != 0) abs.add(n + " " + d);
                    else if ((acc & Opcodes.ACC_STATIC) == 0) mem.add("c " + n + " " + d);
                    return null;
                }
                public FieldVisitor visitField(int acc, String n, String d, String s, Object v) { mem.add("f " + n + " " + d); return null; }
            }, ClassReader.SKIP_CODE);
            members.put(name[0], mem); // a libGDX class inside the game jar = our compat shim, indexed as library
            if (isGame && gdx(name[0])) { abstracts.put(name[0], abs); continue; }
            abstracts.put(name[0], abs);
            if (isGame) game.add(name[0]);
        }
        z.close();
    }
}
