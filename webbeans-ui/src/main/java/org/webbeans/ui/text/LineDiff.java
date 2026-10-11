package org.webbeans.ui.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A line diff (Myers' O(ND) algorithm): the shortest edit script between two line lists as a sequence of
 * {@link Op}s. Used by {@code DiffView}; usable on its own for change detection or unified output.
 */
public final class LineDiff {

    public enum Type { EQUAL, DELETE, INSERT }

    /** One line of the edit script. {@code a} / {@code b} are 0-based line indexes into the inputs (-1 if absent). */
    public static final class Op {
        public final Type type;
        public final int a;
        public final int b;
        public final String text;

        Op(Type type, int a, int b, String text) {
            this.type = type;
            this.a = a;
            this.b = b;
            this.text = text;
        }

        @Override
        public String toString() {
            return (type == Type.EQUAL ? " " : type == Type.DELETE ? "-" : "+") + text;
        }
    }

    private LineDiff() {
    }

    public static List<String> lines(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = text.split("\r?\n", -1);
        List<String> l = new ArrayList<String>(Arrays.asList(parts));
        if (!l.isEmpty() && l.get(l.size() - 1).isEmpty() && text.endsWith("\n")) {
            l.remove(l.size() - 1);
        }
        return l;
    }

    public static List<Op> diff(String a, String b) {
        return diff(lines(a), lines(b));
    }

    public static List<Op> diff(List<String> a, List<String> b) {
        int n = a.size();
        int m = b.size();
        // strip the common prefix and suffix first: cheap and shrinks the problem a lot for typical edits
        int start = 0;
        while (start < n && start < m && a.get(start).equals(b.get(start))) {
            start++;
        }
        int endA = n;
        int endB = m;
        while (endA > start && endB > start && a.get(endA - 1).equals(b.get(endB - 1))) {
            endA--;
            endB--;
        }
        List<Op> out = new ArrayList<Op>(Math.max(n, m));
        for (int i = 0; i < start; i++) {
            out.add(new Op(Type.EQUAL, i, i, a.get(i)));
        }
        myers(a.subList(start, endA), b.subList(start, endB), start, start, out);
        for (int i = endA, j = endB; i < n; i++, j++) {
            out.add(new Op(Type.EQUAL, i, j, a.get(i)));
        }
        return out;
    }

    private static void myers(List<String> a, List<String> b, int offA, int offB, List<Op> out) {
        int n = a.size();
        int m = b.size();
        if (n == 0 && m == 0) {
            return;
        }
        if (n == 0) {
            for (int j = 0; j < m; j++) {
                out.add(new Op(Type.INSERT, -1, offB + j, b.get(j)));
            }
            return;
        }
        if (m == 0) {
            for (int i = 0; i < n; i++) {
                out.add(new Op(Type.DELETE, offA + i, -1, a.get(i)));
            }
            return;
        }
        int max = n + m;
        int[] v = new int[2 * max + 2];
        List<int[]> trace = new ArrayList<int[]>();
        int d;
        outer:
        for (d = 0; d <= max; d++) {
            trace.add(v.clone());
            for (int k = -d; k <= d; k += 2) {
                int idx = k + max;
                int x;
                if (k == -d || (k != d && v[idx - 1] < v[idx + 1])) {
                    x = v[idx + 1];
                } else {
                    x = v[idx - 1] + 1;
                }
                int y = x - k;
                while (x < n && y < m && a.get(x).equals(b.get(y))) {
                    x++;
                    y++;
                }
                v[idx] = x;
                if (x >= n && y >= m) {
                    break outer;
                }
            }
        }
        // backtrack
        List<Op> rev = new ArrayList<Op>();
        int x = n;
        int y = m;
        for (int dd = d; dd > 0; dd--) {
            int[] vv = trace.get(dd);
            int k = x - y;
            int idx = k + max;
            int prevK;
            if (k == -dd || (k != dd && vv[idx - 1] < vv[idx + 1])) {
                prevK = k + 1;
            } else {
                prevK = k - 1;
            }
            int prevX = vv[prevK + max];
            int prevY = prevX - prevK;
            while (x > prevX && y > prevY) {
                x--;
                y--;
                rev.add(new Op(Type.EQUAL, offA + x, offB + y, a.get(x)));
            }
            if (x == prevX) {
                y--;
                rev.add(new Op(Type.INSERT, -1, offB + y, b.get(y)));
            } else {
                x--;
                rev.add(new Op(Type.DELETE, offA + x, -1, a.get(x)));
            }
        }
        while (x > 0 && y > 0) {
            x--;
            y--;
            rev.add(new Op(Type.EQUAL, offA + x, offB + y, a.get(x)));
        }
        Collections.reverse(rev);
        out.addAll(rev);
    }

    /** A unified-diff rendering (no file headers) with the given number of context lines. */
    public static String unified(List<Op> ops, int context) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < ops.size()) {
            if (ops.get(i).type == Type.EQUAL) {
                i++;
                continue;
            }
            int from = Math.max(0, i - context);
            int j = i;
            int lastChange = i;
            while (j < ops.size()) {
                if (ops.get(j).type != Type.EQUAL) {
                    lastChange = j;
                } else if (j - lastChange > 2 * context) {
                    break;
                }
                j++;
            }
            int to = Math.min(ops.size(), lastChange + context + 1);
            int aStart = -1;
            int bStart = -1;
            int aCount = 0;
            int bCount = 0;
            for (int k = from; k < to; k++) {
                Op op = ops.get(k);
                if (op.type != Type.INSERT) {
                    if (aStart < 0) {
                        aStart = op.a;
                    }
                    aCount++;
                }
                if (op.type != Type.DELETE) {
                    if (bStart < 0) {
                        bStart = op.b;
                    }
                    bCount++;
                }
            }
            sb.append("@@ -").append(aStart < 0 ? 0 : aStart + 1).append(',').append(aCount)
                    .append(" +").append(bStart < 0 ? 0 : bStart + 1).append(',').append(bCount).append(" @@\n");
            for (int k = from; k < to; k++) {
                sb.append(ops.get(k)).append('\n');
            }
            i = to;
        }
        return sb.toString();
    }
}
