import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Recovers the names ProGuard stripped from Exiled Kingdoms' bundled libGDX (1.9.12) by matching it, class by class,
 * against the official 1.9.12 jars (class names were kept). Members whose names survived match directly; renamed ones
 * by descriptor, then by aligning the bodies of already-matched method pairs (votes from field/method instructions at
 * the same position), iterated to a fixed point.
 *
 * Usage: java Match <exiledkingdoms.jar> <out mapping.tsv> <official jars...>
 * Output lines: m|f <tab> owner <tab> obfName <tab> desc <tab> realName   (only renamed members)
 */
public class Match {
    static final Map<String, ClassNode> ek = new HashMap<String, ClassNode>(), stock = new HashMap<String, ClassNode>();
    static final Map<String, Map<MethodNode, MethodNode>> MM = new HashMap<String, Map<MethodNode, MethodNode>>();
    static final Map<String, Map<FieldNode, FieldNode>> FM = new HashMap<String, Map<FieldNode, FieldNode>>();
    /** "owner|kind name desc" -> real name -> votes, gathered from every matched pair (cross-class) */
    static Map<String, Map<String, Integer>> globalVotes = new HashMap<String, Map<String, Integer>>();

    public static void main(String[] a) throws Exception {
        load(a[0], ek, true);
        for (int i = 2; i < a.length; i++) load(a[i], stock, false);
        int cls = 0, renamedM = 0, renamedF = 0, unmatchedM = 0, unmatchedF = 0;
        PrintWriter out = new PrintWriter(new FileWriter(a[1]));
        List<String> unresolved = new ArrayList<String>();
        TreeMap<String, ClassNode> sorted = new TreeMap<String, ClassNode>(ek);
        for (ClassNode e : sorted.values()) if (stock.containsKey(e.name)) {
            MM.put(e.name, new LinkedHashMap<MethodNode, MethodNode>());
            FM.put(e.name, new LinkedHashMap<FieldNode, FieldNode>());
        }
        for (int round = 0; round < 8; round++) {
            for (ClassNode e : sorted.values()) {
                ClassNode s = stock.get(e.name);
                if (s != null) matchClass(e, s, MM.get(e.name), FM.get(e.name));
            }
            Map<String, Map<String, Integer>> v = new HashMap<String, Map<String, Integer>>();
            for (String owner : MM.keySet())
                for (Map.Entry<MethodNode, MethodNode> p : MM.get(owner).entrySet()) align(null, p.getKey(), p.getValue(), v);
            globalVotes = v;
        }
        for (ClassNode e : sorted.values()) {
            ClassNode s = stock.get(e.name);
            if (s == null) continue;
            cls++;
            Map<MethodNode, MethodNode> mm = MM.get(e.name);
            Map<FieldNode, FieldNode> fm = FM.get(e.name);
            for (MethodNode m : e.methods) {
                MethodNode t = mm.get(m);
                if (t == null) { unmatchedM++; unresolved.add("m " + e.name + "." + m.name + m.desc); continue; }
                if (!t.name.equals(m.name)) { renamedM++; out.println("m\t" + e.name + "\t" + m.name + "\t" + m.desc + "\t" + t.name); }
            }
            for (FieldNode f : e.fields) {
                FieldNode t = fm.get(f);
                if (t == null) { unmatchedF++; unresolved.add("f " + e.name + "." + f.name + ":" + f.desc); continue; }
                if (!t.name.equals(f.name)) { renamedF++; out.println("f\t" + e.name + "\t" + f.name + "\t" + f.desc + "\t" + t.name); }
            }
        }
        out.close();
        System.out.println("classes " + cls + ", renamed methods " + renamedM + ", renamed fields " + renamedF
                + ", unmatched methods " + unmatchedM + ", unmatched fields " + unmatchedF);
        PrintWriter u = new PrintWriter(new FileWriter(a[1] + ".unresolved"));
        for (String s : unresolved) u.println(s);
        u.close();
    }

    static void load(String jar, Map<String, ClassNode> into, boolean onlyGdx) throws IOException {
        ZipFile z = new ZipFile(jar);
        for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements(); ) {
            ZipEntry ze = en.nextElement();
            String n = ze.getName();
            if (!n.endsWith(".class") || onlyGdx && !n.startsWith("com/badlogic/gdx/")) continue;
            ClassNode c = new ClassNode();
            new ClassReader(z.getInputStream(ze)).accept(c, 0);
            into.put(c.name, c);
        }
        z.close();
    }

    static boolean isStatic(int acc) { return (acc & Opcodes.ACC_STATIC) != 0; }

    static void matchClass(ClassNode e, ClassNode s, Map<MethodNode, MethodNode> mm, Map<FieldNode, FieldNode> fm) {
        // 1. names that survived (and constructors/initializers)
        Set<MethodNode> usedM = new HashSet<MethodNode>(mm.values());
        for (MethodNode m : e.methods) if (!mm.containsKey(m))
            for (MethodNode t : s.methods)
                if (t.name.equals(m.name) && t.desc.equals(m.desc) && !usedM.contains(t)) { mm.put(m, t); usedM.add(t); break; }
        Set<FieldNode> usedF = new HashSet<FieldNode>(fm.values());
        for (FieldNode f : e.fields) if (!fm.containsKey(f))
            for (FieldNode t : s.fields)
                if (t.name.equals(f.name) && t.desc.equals(f.desc) && !usedF.contains(t)) { fm.put(f, t); usedF.add(t); break; }
        boolean progress = true;
        while (progress) {
            progress = false;
            // 2. unique descriptor among the still-unmatched
            for (MethodNode m : e.methods) {
                if (mm.containsKey(m)) continue;
                MethodNode only = null; int n = 0;
                for (MethodNode t : s.methods)
                    if (!usedM.contains(t) && t.desc.equals(m.desc) && isStatic(t.access) == isStatic(m.access)) { only = t; n++; }
                int peers = 0;
                for (MethodNode o : e.methods)
                    if (!mm.containsKey(o) && o.desc.equals(m.desc) && isStatic(o.access) == isStatic(m.access)) peers++;
                if (n == 1 && peers == 1) { mm.put(m, only); usedM.add(only); progress = true; }
            }
            for (FieldNode f : e.fields) {
                if (fm.containsKey(f)) continue;
                FieldNode only = null; int n = 0;
                for (FieldNode t : s.fields)
                    if (!usedF.contains(t) && t.desc.equals(f.desc) && isStatic(t.access) == isStatic(f.access)) { only = t; n++; }
                int peers = 0;
                for (FieldNode o : e.fields)
                    if (!fm.containsKey(o) && o.desc.equals(f.desc) && isStatic(o.access) == isStatic(f.access)) peers++;
                if (n == 1 && peers == 1) { fm.put(f, only); usedF.add(only); progress = true; }
            }
            // 2b. constants: same static final value
            for (FieldNode f : e.fields) {
                if (fm.containsKey(f) || f.value == null) continue;
                FieldNode hit = null; int n = 0;
                for (FieldNode t : s.fields)
                    if (!usedF.contains(t) && t.desc.equals(f.desc) && f.value.equals(t.value)) { hit = t; n++; }
                if (n == 1) { fm.put(f, hit); usedF.add(hit); progress = true; }
            }
            // 3. ambiguous methods: identical normalized body
            for (MethodNode m : e.methods) {
                if (mm.containsKey(m)) continue;
                String fp = fingerprint(m);
                MethodNode hit = null; int n = 0;
                for (MethodNode t : s.methods)
                    if (!usedM.contains(t) && t.desc.equals(m.desc) && isStatic(t.access) == isStatic(m.access) && fingerprint(t).equals(fp)) { hit = t; n++; }
                if (n == 1) { mm.put(m, hit); usedM.add(hit); progress = true; }
            }
            // 4. votes from aligned bodies of matched pairs (own-class fields and methods)
            Map<String, Map<String, Integer>> votes = new HashMap<String, Map<String, Integer>>();
            for (Map.Entry<MethodNode, MethodNode> p : mm.entrySet()) align(e.name, p.getKey(), p.getValue(), votes);
            mergeVotes(votes, e.name);
            for (FieldNode f : e.fields) {
                if (fm.containsKey(f)) continue;
                String best = argmax(votes.get("f" + f.name + ":" + f.desc));
                if (best == null) continue;
                for (FieldNode t : s.fields)
                    if (!usedF.contains(t) && t.name.equals(best) && t.desc.equals(f.desc)) { fm.put(f, t); usedF.add(t); progress = true; break; }
            }
            // 5. abstract methods have no body: ProGuard names them in declaration order and keeps that order, so pair the
            //    unmatched ones of each descriptor by position when both sides have the same count
            Map<String, List<MethodNode>> eg = new LinkedHashMap<String, List<MethodNode>>(), sg = new LinkedHashMap<String, List<MethodNode>>();
            for (MethodNode m : e.methods)
                if (!mm.containsKey(m) && (m.access & Opcodes.ACC_ABSTRACT) != 0) group(eg, m);
            for (MethodNode t : s.methods)
                if (!usedM.contains(t) && (t.access & Opcodes.ACC_ABSTRACT) != 0) group(sg, t);
            for (Map.Entry<String, List<MethodNode>> g : eg.entrySet()) {
                List<MethodNode> st = sg.get(g.getKey());
                if (st == null || st.size() != g.getValue().size()) continue;
                for (int k = 0; k < st.size(); k++) { mm.put(g.getValue().get(k), st.get(k)); usedM.add(st.get(k)); progress = true; }
            }
            for (MethodNode m : e.methods) {
                if (mm.containsKey(m)) continue;
                String best = argmax(votes.get("m" + m.name + m.desc));
                if (best == null) continue;
                for (MethodNode t : s.methods)
                    if (!usedM.contains(t) && t.name.equals(best) && t.desc.equals(m.desc)) { mm.put(m, t); usedM.add(t); progress = true; break; }
            }
        }
    }

    static void group(Map<String, List<MethodNode>> g, MethodNode m) {
        String k = m.desc + (isStatic(m.access) ? "s" : "");
        if (!g.containsKey(k)) g.put(k, new ArrayList<MethodNode>());
        g.get(k).add(m);
    }

    /** Opcodes plus referenced owners/descriptors and constants, but no member names (those are what differ). */
    static String fingerprint(MethodNode m) {
        StringBuilder b = new StringBuilder();
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i.getOpcode() < 0) continue;
            b.append(i.getOpcode()).append(',');
            if (i instanceof FieldInsnNode) b.append(((FieldInsnNode) i).owner).append(((FieldInsnNode) i).desc);
            else if (i instanceof MethodInsnNode) b.append(((MethodInsnNode) i).owner).append(((MethodInsnNode) i).desc);
            else if (i instanceof LdcInsnNode) b.append(((LdcInsnNode) i).cst);
            else if (i instanceof IntInsnNode) b.append(((IntInsnNode) i).operand);
            else if (i instanceof TypeInsnNode) b.append(((TypeInsnNode) i).desc);
            b.append(';');
        }
        return b.toString();
    }

    /** Same opcode stream (ignoring names): pair field/method instructions on this class and vote obf -> real. */
    static void align(String owner, MethodNode em, MethodNode sm, Map<String, Map<String, Integer>> votes) {
        List<AbstractInsnNode> ei = real(em), si = real(sm);
        if (ei.size() != si.size()) return;
        for (int k = 0; k < ei.size(); k++) if (ei.get(k).getOpcode() != si.get(k).getOpcode()) return;
        for (int k = 0; k < ei.size(); k++) {
            AbstractInsnNode x = ei.get(k), y = si.get(k);
            if (x instanceof FieldInsnNode && (owner == null || ((FieldInsnNode) x).owner.equals(owner))) {
                FieldInsnNode fx = (FieldInsnNode) x, fy = (FieldInsnNode) y;
                if (fx.desc.equals(fy.desc) && fx.owner.equals(fy.owner))
                    vote(votes, (owner == null ? fx.owner + "|" : "") + "f" + fx.name + ":" + fx.desc, fy.name);
            } else if (x instanceof MethodInsnNode && (owner == null || ((MethodInsnNode) x).owner.equals(owner))) {
                MethodInsnNode mx = (MethodInsnNode) x, my = (MethodInsnNode) y;
                if (mx.desc.equals(my.desc) && mx.owner.equals(my.owner))
                    vote(votes, (owner == null ? mx.owner + "|" : "") + "m" + mx.name + mx.desc, my.name);
            }
        }
    }

    /** Add the cross-class votes about this owner's members (from bodies in other classes). */
    static void mergeVotes(Map<String, Map<String, Integer>> votes, String owner) {
        String pre = owner + "|";
        for (Map.Entry<String, Map<String, Integer>> e : globalVotes.entrySet()) {
            if (!e.getKey().startsWith(pre)) continue;
            String k = e.getKey().substring(pre.length());
            for (Map.Entry<String, Integer> r : e.getValue().entrySet())
                for (int i = 0; i < r.getValue(); i++) vote(votes, k, r.getKey());
        }
    }

    static List<AbstractInsnNode> real(MethodNode m) {
        List<AbstractInsnNode> l = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) if (i.getOpcode() >= 0) l.add(i);
        return l;
    }

    static void vote(Map<String, Map<String, Integer>> v, String key, String real) {
        Map<String, Integer> m = v.get(key);
        if (m == null) v.put(key, m = new HashMap<String, Integer>());
        Integer n = m.get(real);
        m.put(real, n == null ? 1 : n + 1);
    }

    static String argmax(Map<String, Integer> m) {
        if (m == null) return null;
        String best = null; int bn = 0, total = 0;
        for (Map.Entry<String, Integer> e : m.entrySet()) { total += e.getValue(); if (e.getValue() > bn) { bn = e.getValue(); best = e.getKey(); } }
        return bn * 2 > total ? best : null; // clear majority only
    }
}
