import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Builds the "standard libGDX" Exiled Kingdoms jar: drops the bundled (obfuscated) libGDX 1.9.12, LWJGL, jinput and
 * gdx natives, and rewrites every remaining class so its references to libGDX members use the real names from
 * mapping.tsv. Member lookups walk the original class hierarchy, so inherited and overriding members follow too.
 * Resources (assets) are copied unchanged.
 *
 * Usage: java Remap <exiledkingdoms.jar> <mapping.tsv> <out.jar> <official jars...>
 * Prints references into libGDX that could not be resolved (the G0 gate number).
 */
public class Remap {
    static final Map<String, String> map = new HashMap<String, String>(); // "m|f owner name desc" -> real
    static final Map<String, String[]> supers = new HashMap<String, String[]>(); // class -> super + interfaces
    static final Map<String, Set<String>> ekMembers = new HashMap<String, Set<String>>(); // class -> "m name desc"
    static final Map<String, Set<String>> stockMembers = new HashMap<String, Set<String>>();
    static final TreeMap<String, Integer> unresolved = new TreeMap<String, Integer>();

    static boolean dropped(String name) {
        return name.startsWith("com/badlogic/gdx/") && !name.startsWith("com/badlogic/gdx/pay/")
                || name.startsWith("org/lwjgl/") || name.startsWith("net/java/games/")
                || name.matches("lib(gdx|lwjgl|openal|jinput).*\\.so") || name.matches(".*\\.dll");
    }

    public static void main(String[] a) throws Exception {
        for (String line : readLines(a[1])) {
            String[] p = line.split("\t");
            map.put(p[0] + " " + p[1] + " " + p[2] + " " + p[3], p[4]);
        }
        index(a[0], ekMembers, true);
        for (int i = 3; i < a.length; i++) index(a[i], stockMembers, false);

        ZipFile in = new ZipFile(a[0]);
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(a[2]));
        int kept = 0;
        for (Enumeration<? extends ZipEntry> en = in.entries(); en.hasMoreElements(); ) {
            ZipEntry ze = en.nextElement();
            String n = ze.getName();
            if (ze.isDirectory() || dropped(n.endsWith(".class") ? n.substring(0, n.length() - 6) : n)) continue;
            byte[] data = readAll(in.getInputStream(ze));
            if (n.endsWith(".class")) {
                ClassReader cr = new ClassReader(data);
                checkOverrides(cr);
                ClassWriter cw = new ClassWriter(0);
                cr.accept(new ClassRemapper(cw, new Lookup()), 0);
                data = cw.toByteArray();
                kept++;
            }
            out.putNextEntry(new ZipEntry(n));
            out.write(data);
            out.closeEntry();
        }
        out.close();
        in.close();
        int total = 0;
        for (int v : unresolved.values()) total += v;
        System.out.println("kept classes " + kept + ", unresolved libGDX references " + unresolved.size()
                + " distinct / " + total + " total");
        PrintWriter u = new PrintWriter(new FileWriter(a[2] + ".unresolved"));
        for (Map.Entry<String, Integer> e : unresolved.entrySet()) u.println(e.getValue() + "\t" + e.getKey());
        u.close();
    }

    /** A kept class implementing/overriding a libGDX method whose real name we don't know (e.g. Screen.show). */
    static void checkOverrides(ClassReader cr) {
        final String cls = cr.getClassName();
        final Lookup l = new Lookup();
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            public MethodVisitor visitMethod(int acc, String n, String d, String sig, String[] ex) {
                if (n.startsWith("<") || (acc & Opcodes.ACC_STATIC) != 0) return null;
                String[] sup = supers.get(cls);
                if (sup == null) return null;
                for (String s : sup) {
                    String decl = l.declaring("m", s, n, d, new HashSet<String>());
                    if (decl == null || !decl.startsWith("com/badlogic/gdx/") || decl.startsWith("com/badlogic/gdx/pay/")) continue;
                    if (l.mapped("m", s, n, d, new HashSet<String>()) != null) continue;
                    Set<String> sm = stockMembers.get(decl);
                    if (sm != null && sm.contains("m " + n + " " + d)) continue;
                    String key = "override " + decl + "." + n + d;
                    Integer c = unresolved.get(key);
                    unresolved.put(key, c == null ? 1 : c + 1);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE);
    }

    /** Resolves names through the original hierarchy; records refs into libGDX that have no real name. */
    static class Lookup extends Remapper {
        public String mapMethodName(String owner, String name, String desc) {
            if (name.startsWith("<")) return name;
            return member("m", owner, name, desc);
        }

        public String mapFieldName(String owner, String name, String desc) {
            return member("f", owner, name, desc);
        }

        String member(String kind, String owner, String name, String desc) {
            // a real name anywhere up the hierarchy wins: covers inherited refs and game overrides of libGDX methods
            String real = mapped(kind, owner, name, desc, new HashSet<String>());
            if (real != null) return real;
            String decl = declaring(kind, owner, name, desc, new HashSet<String>());
            if (decl == null) return name; // JDK or not found: leave
            if (decl.startsWith("com/badlogic/gdx/") && !decl.startsWith("com/badlogic/gdx/pay/")) {
                Set<String> sm = stockMembers.get(decl);
                if (sm == null || !sm.contains(kind + " " + name + " " + desc)) {
                    String key = kind + " " + decl + "." + name + (kind.equals("m") ? desc : ":" + desc);
                    Integer c = unresolved.get(key);
                    unresolved.put(key, c == null ? 1 : c + 1);
                }
            }
            return name;
        }

        String mapped(String kind, String cls, String name, String desc, Set<String> seen) {
            if (cls == null || !seen.add(cls)) return null;
            String r = map.get(kind + " " + cls + " " + name + " " + desc);
            if (r != null) return r;
            if (kind.equals("f") && ekMembers.containsKey(cls) && ekMembers.get(cls).contains("f " + name + " " + desc)
                    && !cls.startsWith("com/badlogic/gdx/")) return null; // a game field shadows nothing upward
            String[] sup = supers.get(cls);
            if (sup == null) return null;
            for (String s : sup) {
                String d = mapped(kind, s, name, desc, seen);
                if (d != null) return d;
            }
            return null;
        }

        /** The class (owner or a supertype) that declares this member in the original jar. */
        String declaring(String kind, String cls, String name, String desc, Set<String> seen) {
            if (cls == null || !seen.add(cls)) return null;
            Set<String> mem = ekMembers.get(cls);
            if (mem != null && mem.contains(kind + " " + name + " " + desc)) return cls;
            String[] sup = supers.get(cls);
            if (sup == null) return null;
            for (String s : sup) {
                String d = declaring(kind, s, name, desc, seen);
                if (d != null) return d;
            }
            return null;
        }
    }

    static void index(String jar, Map<String, Set<String>> into, boolean hierarchy) throws IOException {
        ZipFile z = new ZipFile(jar);
        for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements(); ) {
            ZipEntry ze = en.nextElement();
            if (!ze.getName().endsWith(".class")) continue;
            final Set<String> mem = new HashSet<String>();
            final String[][] holder = new String[1][];
            final String[] nameOut = new String[1];
            new ClassReader(z.getInputStream(ze)).accept(new ClassVisitor(Opcodes.ASM9) {
                public void visit(int v, int acc, String name, String sig, String sup, String[] itf) {
                    nameOut[0] = name;
                    List<String> l = new ArrayList<String>();
                    if (sup != null) l.add(sup);
                    if (itf != null) l.addAll(Arrays.asList(itf));
                    holder[0] = l.toArray(new String[0]);
                }
                public MethodVisitor visitMethod(int acc, String n, String d, String s, String[] e) { mem.add("m " + n + " " + d); return null; }
                public FieldVisitor visitField(int acc, String n, String d, String s, Object v) { mem.add("f " + n + " " + d); return null; }
            }, ClassReader.SKIP_CODE);
            into.put(nameOut[0], mem);
            if (hierarchy) supers.put(nameOut[0], holder[0]);
        }
        z.close();
    }

    static List<String> readLines(String f) throws IOException {
        List<String> l = new ArrayList<String>();
        BufferedReader r = new BufferedReader(new FileReader(f));
        for (String s; (s = r.readLine()) != null; ) if (!s.isEmpty()) l.add(s);
        r.close();
        return l;
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
        return b.toByteArray();
    }
}
