package org.webbeans.windows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.webbeans.core.json.Json;

/**
 * The layout of one UI: a tree of splits and modes, floating modes, the maximized mode, minimised (sliding)
 * modes, and per-component state. Identical in memory, on the wire (the dock's {@code layout} property) and on
 * disk ({@code layout.json}). Mutations are plain methods; the window manager decides when to send and save.
 */
public final class LayoutModel {

    public static final int VERSION = 1;
    public static final double STAR = -1; // "the rest" in sizes

    /** A split or a mode. */
    public abstract static class Node {
        Split parent;

        public abstract Map<String, Object> toJson();

        abstract Node copy();
    }

    public static final class Split extends Node {
        public final String dir; // "row" | "col"
        public final List<Double> sizes = new ArrayList<Double>();
        public final List<Node> children = new ArrayList<Node>();

        public Split(String dir) {
            this.dir = dir;
        }

        public void add(int index, Node n, double size) {
            children.add(index, n);
            sizes.add(index, size);
            n.parent = this;
        }

        void remove(Node n) {
            int i = children.indexOf(n);
            if (i >= 0) {
                children.remove(i);
                sizes.remove(i);
                n.parent = null;
            }
        }

        @Override
        public Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("type", "split");
            m.put("dir", dir);
            List<Object> s = new ArrayList<Object>();
            for (Double d : sizes) {
                s.add(d == STAR ? "*" : (Object) d);
            }
            m.put("sizes", s);
            List<Object> c = new ArrayList<Object>();
            for (Node n : children) {
                c.add(n.toJson());
            }
            m.put("children", c);
            return m;
        }

        @Override
        Split copy() {
            Split s = new Split(dir);
            for (int i = 0; i < children.size(); i++) {
                s.add(i, children.get(i).copy(), sizes.get(i));
            }
            return s;
        }
    }

    public static final class ModeNode extends Node {
        public final String name;
        public String selected;
        public final List<String> tcs = new ArrayList<String>();
        /** Floating geometry, null when docked. */
        public int[] rect;

        public ModeNode(String name) {
            this.name = name;
        }

        @Override
        public Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("type", "mode");
            m.put("name", name);
            if (selected != null) {
                m.put("selected", selected);
            }
            m.put("tcs", new ArrayList<String>(tcs));
            if (rect != null) {
                m.put("x", rect[0]);
                m.put("y", rect[1]);
                m.put("w", rect[2]);
                m.put("h", rect[3]);
            }
            return m;
        }

        @Override
        ModeNode copy() {
            ModeNode n = new ModeNode(name);
            n.selected = selected;
            n.tcs.addAll(tcs);
            n.rect = rect == null ? null : rect.clone();
            return n;
        }
    }

    public String role = "default";
    public String active;
    public Node root;
    public final List<ModeNode> floating = new ArrayList<ModeNode>();
    public String maximized;
    /** mode name → side ("left", "right", "top", "bottom") for minimised modes. */
    public final Map<String, String> sliding = new LinkedHashMap<String, String>();
    /** mode name → the tree position it had before it was floated/minimised, as the side to return to. */
    public final Map<String, String> returnSides = new LinkedHashMap<String, String>();
    public final Map<String, Map<String, Object>> tcState = new LinkedHashMap<String, Map<String, Object>>();
    /** component id → mode name, for closed persisted components so they reopen where they were. */
    public final Map<String, String> closedTcModes = new LinkedHashMap<String, String>();

    public LayoutModel() {
        root = new Split("row");
    }

    // ---- queries ----------------------------------------------------------------------------------------------

    /** The mode node anywhere (tree, floating or minimised), or null. */
    public ModeNode findMode(String name) {
        ModeNode n = findIn(root, name);
        if (n != null) {
            return n;
        }
        for (ModeNode f : floating) {
            if (f.name.equals(name)) {
                return f;
            }
        }
        for (ModeNode f : minimised) {
            if (f.name.equals(name)) {
                return f;
            }
        }
        return null;
    }

    private final List<ModeNode> minimised = new ArrayList<ModeNode>();

    public List<ModeNode> getMinimised() {
        return Collections.unmodifiableList(minimised);
    }

    static ModeNode findIn(Node n, String name) {
        if (n instanceof ModeNode) {
            return ((ModeNode) n).name.equals(name) ? (ModeNode) n : null;
        }
        for (Node c : ((Split) n).children) {
            ModeNode r = findIn(c, name);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    public ModeNode findModeOf(String tcId) {
        for (ModeNode m : allModes()) {
            if (m.tcs.contains(tcId)) {
                return m;
            }
        }
        return null;
    }

    public List<ModeNode> allModes() {
        List<ModeNode> out = new ArrayList<ModeNode>();
        collect(root, out);
        out.addAll(floating);
        out.addAll(minimised);
        return out;
    }

    private static void collect(Node n, List<ModeNode> into) {
        if (n instanceof ModeNode) {
            into.add((ModeNode) n);
        } else {
            for (Node c : ((Split) n).children) {
                collect(c, into);
            }
        }
    }

    public List<String> allTcs() {
        List<String> out = new ArrayList<String>();
        for (ModeNode m : allModes()) {
            out.addAll(m.tcs);
        }
        return out;
    }

    /** The node at a path of child indexes from the root, or null. */
    public Node nodeAt(List<Integer> path) {
        Node n = root;
        for (int i : path) {
            if (!(n instanceof Split) || i < 0 || i >= ((Split) n).children.size()) {
                return null;
            }
            n = ((Split) n).children.get(i);
        }
        return n;
    }

    public boolean isFloating(String mode) {
        for (ModeNode f : floating) {
            if (f.name.equals(mode)) {
                return true;
            }
        }
        return false;
    }

    public boolean isMinimised(String mode) {
        return sliding.containsKey(mode);
    }

    // ---- placement --------------------------------------------------------------------------------------------

    /** Registered sides and positions, maintained by the window manager so placement can classify subtrees. */
    final Map<String, Mode.Side> sides = new LinkedHashMap<String, Mode.Side>();
    final Map<String, Integer> positions = new LinkedHashMap<String, Integer>();

    public void setModeInfo(String name, Mode.Side side, int position) {
        sides.put(name, side);
        positions.put(name, position);
    }

    /**
     * Inserts a mode node into the tree according to its registered side: LEFT/RIGHT go into the main row beside
     * the centre area (stacked into a column with modes already on that side), TOP/BOTTOM above/below the main
     * row, CENTER beside the existing centre modes. {@code size} is the default width/height in pixels.
     */
    public void place(ModeNode mode, int size) {
        if (findMode(mode.name) != null) {
            return;
        }
        Mode.Side side = sides.containsKey(mode.name) ? sides.get(mode.name) : Mode.Side.CENTER;
        int position = positions.containsKey(mode.name) ? positions.get(mode.name) : Integer.MAX_VALUE;
        if (root instanceof ModeNode) {
            Split s = new Split("row");
            Node old = root;
            s.add(0, old, STAR);
            root = s;
        }
        switch (side) {
            case TOP:
            case BOTTOM: {
                Split col = ensureDir((Split) root, "col");
                int at;
                if (side == Mode.Side.TOP) {
                    at = 0;
                    while (at < col.children.size() && sideOf(col.children.get(at)) == Mode.Side.TOP
                            && positionOf(col.children.get(at)) <= position) {
                        at++;
                    }
                } else {
                    at = col.children.size();
                    while (at > 0 && sideOf(col.children.get(at - 1)) == Mode.Side.BOTTOM
                            && positionOf(col.children.get(at - 1)) > position) {
                        at--;
                    }
                }
                col.add(at, mode, size);
                break;
            }
            case LEFT:
            case RIGHT: {
                Split row = mainRow();
                if (row.children.isEmpty()) {
                    row.add(0, mode, size);
                    break;
                }
                int edge = side == Mode.Side.LEFT ? 0 : row.children.size() - 1;
                Node edgeNode = row.children.get(edge);
                if (sideOf(edgeNode) == side) {
                    Split col;
                    if (edgeNode instanceof Split && ((Split) edgeNode).dir.equals("col")) {
                        col = (Split) edgeNode;
                    } else {
                        col = new Split("col");
                        double sz = row.sizes.get(edge);
                        row.remove(edgeNode);
                        col.add(0, edgeNode, STAR);
                        row.add(edge, col, sz);
                    }
                    int at = 0;
                    while (at < col.children.size() && positionOf(col.children.get(at)) <= position) {
                        at++;
                    }
                    col.add(at, mode, STAR);
                } else {
                    row.add(side == Mode.Side.LEFT ? 0 : row.children.size(), mode, size);
                }
                break;
            }
            default: {
                Split row = mainRow();
                int at = centreIndex(row);
                row.add(at, mode, STAR);
            }
        }
    }

    private int centreIndex(Split row) {
        int last = -1;
        for (int i = 0; i < row.children.size(); i++) {
            Mode.Side s = sideOf(row.children.get(i));
            if (s == Mode.Side.CENTER || s == null) {
                last = i;
            }
        }
        if (last >= 0) {
            return last + 1;
        }
        int i = 0;
        while (i < row.children.size() && sideOf(row.children.get(i)) == Mode.Side.LEFT) {
            i++;
        }
        return i;
    }

    private int positionOf(Node n) {
        if (n instanceof ModeNode) {
            Integer p = positions.get(((ModeNode) n).name);
            return p == null ? Integer.MAX_VALUE : p;
        }
        int min = Integer.MAX_VALUE;
        for (Node c : ((Split) n).children) {
            min = Math.min(min, positionOf(c));
        }
        return min;
    }

    /** The side the registry assigns to a subtree (all modes in it share a side), CENTER for anonymous modes, null if mixed. */
    Mode.Side sideOf(Node n) {
        if (n instanceof ModeNode) {
            Mode.Side s = sides.get(((ModeNode) n).name);
            return s == null ? Mode.Side.CENTER : s;
        }
        Mode.Side s = null;
        for (Node c : ((Split) n).children) {
            Mode.Side cs = sideOf(c);
            if (cs == null) {
                return null;
            }
            if (s == null) {
                s = cs;
            } else if (s != cs) {
                return null;
            }
        }
        return s;
    }

    /** The row split holding the centre area (created if needed). */
    Split mainRow() {
        Split r = (Split) root;
        if (r.dir.equals("row")) {
            return r;
        }
        for (Node c : r.children) {
            if (c instanceof Split && ((Split) c).dir.equals("row")) {
                return (Split) c;
            }
        }
        for (int i = 0; i < r.children.size(); i++) {
            Node c = r.children.get(i);
            Mode.Side s = sideOf(c);
            if (s != Mode.Side.TOP && s != Mode.Side.BOTTOM) {
                Split row = new Split("row");
                double sz = r.sizes.get(i);
                r.remove(c);
                row.add(0, c, STAR);
                r.add(i, row, sz);
                return row;
            }
        }
        Split row = new Split("row");
        int at = 0;
        while (at < r.children.size() && sideOf(r.children.get(at)) == Mode.Side.TOP) {
            at++;
        }
        r.add(at, row, STAR);
        return row;
    }

    /** Makes the root a split of the given direction, wrapping the current root if necessary. */
    private Split ensureDir(Split rootSplit, String dir) {
        if (rootSplit.dir.equals(dir)) {
            return rootSplit;
        }
        Split s = new Split(dir);
        s.add(0, rootSplit, STAR);
        root = s;
        return s;
    }

    /** Splits the pane of {@code target}: the new mode goes to the given side of it with half the space. */
    public void splitBeside(ModeNode target, ModeNode mode, String side) {
        boolean horizontal = side.equals("left") || side.equals("right");
        boolean before = side.equals("left") || side.equals("top");
        Split parent = target.parent;
        if (parent != null && parent.dir.equals(horizontal ? "row" : "col")) {
            int i = parent.children.indexOf(target);
            double sz = parent.sizes.get(i);
            double half = sz == STAR ? STAR : sz / 2; // two stars share the rest evenly
            parent.sizes.set(i, half);
            parent.add(before ? i : i + 1, mode, half);
            return;
        }
        Split s = new Split(horizontal ? "row" : "col");
        if (parent != null) {
            int i = parent.children.indexOf(target);
            double sz = parent.sizes.get(i);
            parent.remove(target);
            parent.add(i, s, sz);
        } else {
            root = s;
        }
        s.add(0, target, STAR);
        s.add(before ? 0 : 1, mode, STAR);
    }

    /** Removes a mode node from wherever it is and collapses now-trivial splits. */
    public void removeMode(ModeNode m) {
        if (floating.remove(m) || minimised.remove(m)) {
            sliding.remove(m.name);
            return;
        }
        Split p = m.parent;
        if (p == null) {
            if (root == m) {
                root = new Split("row");
            }
            return;
        }
        p.remove(m);
        collapse(p);
    }

    /**
     * Every split needs one child that takes the remaining space ({@link #STAR}); removing the child that had it
     * (a closed window beside a resized editor, say) leaves only fixed sizes and the row no longer fills the
     * window. Restores it, preferring the subtree with the centre area, then the largest child. Safe to call on
     * any model, including layouts saved before this rule existed.
     */
    public void ensureStars() {
        ensureStars(root);
    }

    private void ensureStars(Node n) {
        if (!(n instanceof Split)) {
            return;
        }
        Split s = (Split) n;
        for (Node c : s.children) {
            ensureStars(c);
        }
        if (s.children.isEmpty() || s.sizes.contains(STAR)) {
            return;
        }
        int pick = -1;
        for (int i = 0; i < s.children.size(); i++) {
            if (hasCentre(s.children.get(i))) {
                pick = i;
            }
        }
        if (pick < 0) {
            for (int i = 0; i < s.children.size(); i++) {
                if (pick < 0 || s.sizes.get(i) > s.sizes.get(pick)) {
                    pick = i;
                }
            }
        }
        s.sizes.set(pick, STAR);
    }

    private boolean hasCentre(Node n) {
        if (n instanceof ModeNode) {
            return sideOf(n) == Mode.Side.CENTER;
        }
        for (Node c : ((Split) n).children) {
            if (hasCentre(c)) {
                return true;
            }
        }
        return false;
    }

    private void collapse(Split s) {
        ensureStars(s);
        Split p = s.parent;
        if (s.children.size() == 1 && p != null) {
            Node only = s.children.get(0);
            int i = p.children.indexOf(s);
            double sz = p.sizes.get(i);
            s.remove(only);
            p.remove(s);
            p.add(i, only, sz);
            if (only instanceof Split && ((Split) only).dir.equals(p.dir)) {
                // merge same-direction splits
                Split inner = (Split) only;
                int at = p.children.indexOf(inner);
                p.remove(inner);
                for (int k = 0; k < inner.children.size(); k++) {
                    Node c = inner.children.get(k);
                    double cs = inner.sizes.get(k);
                    p.add(at + k, c, cs);
                }
            }
            collapse(p);
        } else if (s.children.isEmpty() && p != null) {
            p.remove(s);
            collapse(p);
        } else if (s.children.size() == 1 && p == null && s.children.get(0) instanceof Split) {
            Split only = (Split) s.children.get(0);
            s.remove(only);
            only.parent = null;
            root = only;
        }
    }

    // ---- floating / minimised ----------------------------------------------------------------------------------

    public void floatMode(ModeNode m, int[] rect) {
        if (isFloating(m.name)) {
            m.rect = rect;
            return;
        }
        Mode.Side s = sides.get(m.name);
        returnSides.put(m.name, s == null ? "CENTER" : s.name());
        detachFromTree(m);
        m.rect = rect;
        floating.add(m);
    }

    public void minimise(ModeNode m, String side) {
        if (isMinimised(m.name)) {
            return;
        }
        detachFromTree(m);
        m.rect = null;
        minimised.add(m);
        sliding.put(m.name, side);
    }

    /** Takes the mode out of the tree, the floating list or the minimised list without touching its contents. */
    void detachFromTree(ModeNode m) {
        if (floating.remove(m)) {
            return;
        }
        if (minimised.remove(m)) {
            sliding.remove(m.name);
            return;
        }
        Split p = m.parent;
        if (p != null) {
            p.remove(m);
            collapse(p);
        } else if (root == m) {
            root = new Split("row");
        }
        if (maximized != null && maximized.equals(m.name)) {
            maximized = null;
        }
    }

    // ---- JSON -------------------------------------------------------------------------------------------------

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("version", VERSION);
        m.put("role", role);
        m.put("active", active);
        m.put("root", root.toJson());
        List<Object> f = new ArrayList<Object>();
        for (ModeNode n : floating) {
            f.add(n.toJson());
        }
        m.put("floating", f);
        m.put("maximized", maximized);
        Map<String, Object> sl = new LinkedHashMap<String, Object>();
        for (ModeNode n : minimised) {
            Map<String, Object> e = n.toJson();
            e.put("side", sliding.get(n.name));
            sl.put(n.name, e);
        }
        m.put("minimised", sl);
        m.put("returnSides", new LinkedHashMap<String, String>(returnSides));
        m.put("tcState", new LinkedHashMap<String, Object>(tcState));
        m.put("closedTcModes", new LinkedHashMap<String, String>(closedTcModes));
        return m;
    }

    public String toJsonString() {
        return Json.write(toJson());
    }

    @SuppressWarnings("unchecked")
    public static LayoutModel fromJson(Map<String, Object> m) {
        LayoutModel l = new LayoutModel();
        if (Json.string(m, "role") != null) {
            l.role = Json.string(m, "role");
        }
        l.active = Json.string(m, "active");
        Map<String, Object> rootJson = Json.object(m, "root");
        l.root = rootJson == null ? new Split("row") : nodeFromJson(rootJson);
        List<Object> fl = Json.array(m, "floating");
        if (fl != null) {
            for (Object o : fl) {
                if (o instanceof Map) {
                    Node n = nodeFromJson((Map<String, Object>) o);
                    if (n instanceof ModeNode) {
                        l.floating.add((ModeNode) n);
                    }
                }
            }
        }
        l.maximized = Json.string(m, "maximized");
        Map<String, Object> sl = Json.object(m, "minimised");
        if (sl != null) {
            for (Map.Entry<String, Object> e : sl.entrySet()) {
                if (e.getValue() instanceof Map) {
                    Map<String, Object> mj = (Map<String, Object>) e.getValue();
                    Node n = nodeFromJson(mj);
                    if (n instanceof ModeNode) {
                        l.minimised.add((ModeNode) n);
                        String side = Json.string(mj, "side");
                        l.sliding.put(((ModeNode) n).name, side == null ? "left" : side);
                    }
                }
            }
        }
        Map<String, Object> rs = Json.object(m, "returnSides");
        if (rs != null) {
            for (Map.Entry<String, Object> e : rs.entrySet()) {
                l.returnSides.put(e.getKey(), String.valueOf(e.getValue()));
            }
        }
        Map<String, Object> ts = Json.object(m, "tcState");
        if (ts != null) {
            for (Map.Entry<String, Object> e : ts.entrySet()) {
                if (e.getValue() instanceof Map) {
                    l.tcState.put(e.getKey(), (Map<String, Object>) e.getValue());
                }
            }
        }
        Map<String, Object> cm = Json.object(m, "closedTcModes");
        if (cm != null) {
            for (Map.Entry<String, Object> e : cm.entrySet()) {
                l.closedTcModes.put(e.getKey(), String.valueOf(e.getValue()));
            }
        }
        return l;
    }

    @SuppressWarnings("unchecked")
    static Node nodeFromJson(Map<String, Object> m) {
        String type = Json.string(m, "type");
        if ("split".equals(type)) {
            Split s = new Split("col".equals(Json.string(m, "dir")) ? "col" : "row");
            List<Object> ch = Json.array(m, "children");
            List<Object> sz = Json.array(m, "sizes");
            if (ch != null) {
                for (int i = 0; i < ch.size(); i++) {
                    Object o = ch.get(i);
                    if (!(o instanceof Map)) {
                        continue;
                    }
                    Node c = nodeFromJson((Map<String, Object>) o);
                    Object sv = sz != null && i < sz.size() ? sz.get(i) : null;
                    double size = sv instanceof Number ? ((Number) sv).doubleValue() : STAR;
                    s.add(s.children.size(), c, size);
                }
            }
            return s;
        }
        ModeNode n = new ModeNode(String.valueOf(m.get("name")));
        n.selected = Json.string(m, "selected");
        List<Object> tcs = Json.array(m, "tcs");
        if (tcs != null) {
            for (Object o : tcs) {
                n.tcs.add(String.valueOf(o));
            }
        }
        if (m.get("w") instanceof Number) {
            n.rect = new int[] {(int) Json.integer(m, "x", 100), (int) Json.integer(m, "y", 100),
                (int) Json.integer(m, "w", 600), (int) Json.integer(m, "h", 400)};
        }
        return n;
    }

    LayoutModel copy() {
        return fromJson(toJson());
    }
}
