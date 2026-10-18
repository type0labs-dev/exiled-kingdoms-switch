// Run: javac -d /tmp/sc switch/runtime/eksw/Runtime.java tests/SplitCheck.java && java -cp /tmp/sc SplitCheck
public class SplitCheck {
    public static void main(String[] a) {
        String[] in = {"a,b,c", "a,,b,,", ",a", "", ",", ",,", "abc", "a\tb\t", "x.y.z", "a_b", "a;b;", "#c#", "line1\nline2\n\n"};
        String[] re = {",", ",", ",", ",", ",", ",", ",", "\t", "\\.", "\\_", ";", "#", "\n"};
        for (int i = 0; i < in.length; i++) {
            String want = java.util.Arrays.toString(in[i].split(re[i])), got = java.util.Arrays.toString(eksw.Runtime.split(in[i], re[i]));
            if (!want.equals(got)) throw new AssertionError(in[i] + " / " + re[i] + ": " + want + " vs " + got);
        }
        System.out.println("split ok (" + in.length + " cases)");
    }
}
