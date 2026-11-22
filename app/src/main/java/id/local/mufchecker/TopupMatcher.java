package id.local.mufchecker;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Resolves a named menu tile to one accessible click target, never a fixed screen coordinate. */
public final class TopupMatcher {
    private TopupMatcher() {}
    public static final class Node {
        public final String text, description;
        public final int parent, left, top, right, bottom;
        public final boolean visible, enabled, clickable, buttonRole;
        public Node(String text, String description, int parent, int left, int top, int right, int bottom,
                    boolean visible, boolean enabled, boolean clickable) {
            this(text, description, parent, left, top, right, bottom, visible, enabled, clickable, false);
        }
        public Node(String text, String description, int parent, int left, int top, int right, int bottom,
                    boolean visible, boolean enabled, boolean clickable, boolean buttonRole) {
            this.text = text; this.description = description; this.parent = parent;
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
            this.visible = visible; this.enabled = enabled; this.clickable = clickable;
            this.buttonRole = buttonRole;
        }
    }
    public static final class Result {
        public final int target, textMatches, descriptionMatches, splitMatches, targetCount;
        Result(int target, int textMatches, int descriptionMatches, int splitMatches, int targetCount) {
            this.target = target; this.textMatches = textMatches; this.descriptionMatches = descriptionMatches;
            this.splitMatches = splitMatches; this.targetCount = targetCount;
        }
        public String summary() {
            return "teks=" + textMatches + ", deskripsi=" + descriptionMatches + ", terpisah=" + splitMatches
                + ", target=" + targetCount + ", status=" + (target >= 0 ? "siap klik" : targetCount > 1 ? "ambigu" :
                    textMatches + descriptionMatches + splitMatches > 0 ? "label ada; target klik tidak tersedia" : "label tidak terbaca");
        }
    }
    static String normalized(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
            .replaceAll("[\\p{Cf}]", "").replaceAll("[\u2010-\u2015\u2212\ufe63\uff0d]", "-")
            .replaceAll("[\\p{Z}\\s]+", " ").trim();
    }
    public static boolean isLabel(String s) {
        return normalized(s).matches("top[- ]*up\\s*(?:&|dan)\\s*tagihan(?:,? (?:tombol|button))?");
    }
    private static boolean prefix(String s) { return normalized(s).matches("top[- ]*up(?:\\s*(?:&|dan))?"); }
    private static boolean suffix(String s) { return normalized(s).matches("(?:& |dan )?tagihan"); }
    private static boolean contains(Node outer, Node inner) {
        return outer.left <= inner.left && outer.top <= inner.top && outer.right >= inner.right && outer.bottom >= inner.bottom;
    }
    private static boolean compact(Node target, Node label) {
        long width = label.right - label.left, height = label.bottom - label.top;
        return width > 0 && height > 0 && contains(target, label)
            && target.right - target.left <= Math.max(width * 3, height * 5)
            && target.bottom - target.top <= Math.max(height * 6, width * 3);
    }
    private static boolean alignedAction(Node action, Node label) {
        int overlap = Math.min(action.right, label.right) - Math.max(action.left, label.left);
        int width = Math.min(action.right - action.left, label.right - label.left);
        int height = Math.max(action.bottom - action.top, label.bottom - label.top);
        return width > 0 && overlap >= Math.max(1, width / 3) && action.top <= label.bottom
            && label.top - action.bottom <= height * 2;
    }
    private static boolean adjacent(Node a, Node b) {
        int h = Math.max(a.bottom - a.top, b.bottom - b.top);
        int overlap = Math.min(a.right, b.right) - Math.max(a.left, b.left);
        boolean stacked = overlap >= Math.max(1, Math.min(a.right - a.left, b.right - b.left) / 3)
            && b.top >= a.top && b.top - a.bottom <= h;
        boolean inline = Math.abs((a.top + a.bottom) - (b.top + b.bottom)) <= h
            && b.left >= a.right && b.left - a.right <= h;
        return h > 0 && (stacked || inline);
    }
    private static Node union(Node a, Node b) {
        return new Node("", "", -1, Math.min(a.left, b.left), Math.min(a.top, b.top),
            Math.max(a.right, b.right), Math.max(a.bottom, b.bottom), true, true, false);
    }
    private static boolean descendant(List<Node> nodes, int child, int ancestor) {
        for (int steps = 0; child >= 0 && child < nodes.size() && steps < 64; steps++) {
            if (child == ancestor) return true;
            child = nodes.get(child).parent;
        }
        return false;
    }
    private static int ancestorTarget(List<Node> nodes, int index, Node label) {
        for (int depth = 0; index >= 0 && depth <= 6; depth++) {
            Node n = nodes.get(index);
            if (!n.enabled) return -1;
            if (n.visible && n.clickable && compact(n, label) && smallTarget(nodes, n)) return index;
            index = n.parent;
        }
        return -1;
    }
    private static void addTarget(List<Node> nodes, List<Integer> targets, int index) {
        if (index < 0) return;
        Node candidate = nodes.get(index);
        if (!smallTarget(nodes, candidate)) return;
        for (int i = targets.size() - 1; i >= 0; i--) {
            int old = targets.get(i);
            Node previous = nodes.get(old);
            boolean sameBounds = candidate.left == previous.left && candidate.top == previous.top
                && candidate.right == previous.right && candidate.bottom == previous.bottom;
            if (old == index || sameBounds || descendant(nodes, index, old)) return;
            if (descendant(nodes, old, index)) targets.remove(i);
        }
        targets.add(index);
    }
    private static boolean smallTarget(List<Node> nodes, Node candidate) {
        Node screen = nodes.get(0);
        return candidate.right > candidate.left && candidate.bottom > candidate.top
            && candidate.right - candidate.left <= (screen.right - screen.left) / 2
            && candidate.bottom - candidate.top <= (screen.bottom - screen.top) / 3;
    }
    private static int tileTarget(List<Node> nodes, int anchor, Node label, int second) {
        int direct = ancestorTarget(nodes, anchor, label);
        if (direct >= 0) return direct;
        int group = second < 0 ? anchor : nodes.get(anchor).parent;
        // A label can be a sibling of the only clickable icon in a small tile.
        for (int depth = 0; group >= 0 && depth < 6; depth++) {
            Node container = nodes.get(group);
            if (!container.enabled) return -1;
            if (compact(container, label) && (second < 0 || descendant(nodes, second, group))) {
                List<Integer> actions = new ArrayList<>();
                for (int i = 0; i < nodes.size(); i++) {
                    Node child = nodes.get(i);
                    if (child.visible && child.enabled && child.clickable && contains(container, child) && alignedAction(child, label)
                        && descendant(nodes, i, group) && !hasDisabledAncestor(nodes, i)) addTarget(nodes, actions, i);
                }
                if (actions.size() == 1) return actions.get(0);
                // Do not climb to an unrelated card after finding multiple controls inside the tile.
                if (actions.size() > 1) return -1;
            }
            group = container.parent;
        }
        return -1;
    }
    private static boolean hasDisabledAncestor(List<Node> nodes, int index) {
        for (int depth = 0; index >= 0 && depth < 64; depth++) {
            Node n = nodes.get(index);
            if (!n.enabled) return true;
            index = n.parent;
        }
        return false;
    }
    public static Result find(List<Node> nodes) {
        int text = 0, description = 0, split = 0;
        List<Integer> targets = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            if (!n.visible || !n.enabled || hasDisabledAncestor(nodes, i)) continue;
            boolean textMatch = isLabel(n.text), descriptionMatch = isLabel(n.description);
            if (textMatch) text++;
            if (descriptionMatch) description++;
            if (textMatch || descriptionMatch) addTarget(nodes, targets, tileTarget(nodes, i, n, -1));
            if (!(prefix(n.text) || prefix(n.description))) continue;
            for (int j = 0; j < nodes.size(); j++) {
                Node end = nodes.get(j);
                if (i == j || !end.visible || !end.enabled || hasDisabledAncestor(nodes, j)
                    || !(suffix(end.text) || suffix(end.description)) || !adjacent(n, end)) continue;
                // Require a compact common ancestor, so words from different menu tiles cannot be joined.
                Node label = union(n, end);
                int group = n.parent;
                for (int depth = 0; group >= 0 && depth < 6; depth++) {
                    if (descendant(nodes, j, group) && compact(nodes.get(group), label)) {
                        split++;
                        addTarget(nodes, targets, tileTarget(nodes, i, label, j));
                        break;
                    }
                    group = nodes.get(group).parent;
                }
            }
        }
        return new Result(targets.size() == 1 ? targets.get(0) : -1, text, description, split, targets.size());
    }
}
