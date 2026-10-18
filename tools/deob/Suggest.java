import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * For each unresolved reference, ranks the official methods with the same descriptor by opcode-sequence similarity
 * (LCS ratio) against the obfuscated body. Output feeds manual entries in mapping-manual.tsv.
 * Usage: java Suggest <exiledkingdoms.jar> <unresolved file> <mapping.tsv> <official jars...>
 */
public class Suggest {
    public static void main(String[] a) throws Exception {
        Map<String, ClassNode> ek = new HashMap<String, ClassNode>(), st = new HashMap<String, ClassNode>();
        Match.load(a[0], ek, true);
        for (int i = 3; i < a.length; i++) Match.load(a[i], st, false);
        Set<String> taken = new HashSet<String>(); // real names already used by the automatic mapping
        BufferedReader mr = new BufferedReader(new FileReader(a[2]));
        for (String l; (l = mr.readLine()) != null; ) {
            String[] p = l.split("\t");
            taken.add(p[0] + p[1] + p[4] + p[3]);
            names.put(p[0] + " " + p[1] + " " + p[2] + " " + p[3], p[4]);
        }
        BufferedReader r = new BufferedReader(new FileReader(a[1]));
        for (String line; (line = r.readLine()) != null; ) {
            String ref = line.split("\t")[1]; // "m owner.name(desc)"
            if (!ref.startsWith("m ")) { System.out.println("? " + ref); continue; }
            String s = ref.substring(2);
            int dot = s.indexOf('.'), par = s.indexOf('(');
            String owner = s.substring(0, dot), name = s.substring(dot + 1, par), desc = s.substring(par);
            MethodNode em = find(ek.get(owner), name, desc);
            System.out.println(ref + (em == null ? "  (not declared here?)" : ""));
            ClassNode sc = st.get(owner);
            if (em == null || sc == null) continue;
            List<String> eops = ops(em);
            List<Object[]> ranked = new ArrayList<Object[]>();
            for (MethodNode t : sc.methods) {
                if (!t.desc.equals(desc) || Match.isStatic(t.access) != Match.isStatic(em.access)) continue;
                double sim = lcs(eops, ops(t)) * 2.0 / (eops.size() + ops(t).size() + 1e-9);
                ranked.add(new Object[]{sim, t.name + (taken.contains("m" + owner + t.name + desc) ? " (taken)" : "")});
            }
            Collections.sort(ranked, new Comparator<Object[]>() {
                public int compare(Object[] x, Object[] y) { return Double.compare((Double) y[0], (Double) x[0]); }
            });
            for (int i = 0; i < Math.min(4, ranked.size()); i++)
                System.out.printf("    %.2f %s%n", ranked.get(i)[0], ranked.get(i)[1]);
            String ebody = named(em, true);
            for (MethodNode t : sc.methods)
                if (t.desc.equals(desc) && named(t, false).equals(ebody)) System.out.println("    EXACT (with names): " + t.name);
        }
    }

    static final Map<String, String> names = new HashMap<String, String>();

    /** Body with member names; obfuscated ones translated through the mapping when known. */
    static String named(MethodNode m, boolean translate) {
        StringBuilder b = new StringBuilder();
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i.getOpcode() < 0) continue;
            b.append(i.getOpcode()).append(',');
            if (i instanceof FieldInsnNode) {
                FieldInsnNode f = (FieldInsnNode) i;
                String n = translate && names.containsKey("f " + f.owner + " " + f.name + " " + f.desc) ? names.get("f " + f.owner + " " + f.name + " " + f.desc) : f.name;
                b.append(f.owner).append('.').append(n);
            } else if (i instanceof MethodInsnNode) {
                MethodInsnNode x = (MethodInsnNode) i;
                String n = translate && names.containsKey("m " + x.owner + " " + x.name + " " + x.desc) ? names.get("m " + x.owner + " " + x.name + " " + x.desc) : x.name;
                b.append(x.owner).append('.').append(n).append(x.desc);
            } else if (i instanceof LdcInsnNode) b.append(((LdcInsnNode) i).cst);
            else if (i instanceof IntInsnNode) b.append(((IntInsnNode) i).operand);
            else if (i instanceof TypeInsnNode) b.append(((TypeInsnNode) i).desc);
            b.append(';');
        }
        return b.toString();
    }

    static MethodNode find(ClassNode c, String n, String d) {
        if (c == null) return null;
        for (MethodNode m : c.methods) if (m.name.equals(n) && m.desc.equals(d)) return m;
        return null;
    }

    static List<String> ops(MethodNode m) {
        List<String> l = new ArrayList<String>();
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) if (i.getOpcode() >= 0) l.add("" + i.getOpcode());
        return l;
    }

    static int lcs(List<String> x, List<String> y) {
        int[][] d = new int[x.size() + 1][y.size() + 1];
        for (int i = 1; i <= x.size(); i++)
            for (int j = 1; j <= y.size(); j++)
                d[i][j] = x.get(i - 1).equals(y.get(j - 1)) ? d[i - 1][j - 1] + 1 : Math.max(d[i - 1][j], d[i][j - 1]);
        return d[x.size()][y.size()];
    }
}
