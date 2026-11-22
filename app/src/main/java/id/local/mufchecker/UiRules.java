package id.local.mufchecker;

import java.util.ArrayList;
import java.util.List;

/** Screen matching without Android dependencies, shared by the service and fixture tests. */
public final class UiRules {
    private UiRules() {}
    public static boolean buttonRole(String className) { return className != null && className.endsWith("Button"); }
    public static boolean writable(boolean enabled, boolean editable, boolean setText, String className) {
        return enabled && !buttonRole(className) && (editable || setText || (className != null && className.endsWith("EditText")));
    }
    public enum Navigation { TOPUP, CREDIT, NONE }
    public enum Verification { CLOSE_KEYBOARD, WAIT_FORM, READ_NUMBER }
    public static Verification verification(boolean keyboard, boolean provider, boolean field) {
        if (keyboard) return Verification.CLOSE_KEYBOARD;
        return provider && field ? Verification.READ_NUMBER : Verification.WAIT_FORM;
    }
    public static final class ButtonMatch {
        public final int target, count, gestureTarget, gestureCount, textMatches, descriptionMatches, disabledLabels;
        ButtonMatch(int target, int count, int gestureTarget, int gestureCount, int textMatches, int descriptionMatches, int disabledLabels) {
            this.target = target; this.count = count; this.gestureTarget = gestureTarget; this.gestureCount = gestureCount;
            this.textMatches = textMatches; this.descriptionMatches = descriptionMatches; this.disabledLabels = disabledLabels;
        }
        public String summary() {
            return "teks=" + textMatches + ", deskripsi=" + descriptionMatches + ", dinonaktifkan=" + disabledLabels
                + ", aksiKlik=" + count + ", lokasiLabel=" + gestureCount + ", metode="
                + (gestureTarget >= 0 ? "GESTURE_LABEL" : "belum unik/tersedia");
        }
    }
    /** Multiple accessible labels may describe the same inquiry button. Distinct actions remain ambiguous. */
    public static ButtonMatch inquiryAction(List<TopupMatcher.Node> nodes) {
        int width = nodes.isEmpty() ? 0 : nodes.get(0).right - nodes.get(0).left;
        int height = nodes.isEmpty() ? 0 : nodes.get(0).bottom - nodes.get(0).top;
        return inquiryAction(nodes, width, height);
    }
    public static ButtonMatch inquiryAction(List<TopupMatcher.Node> nodes, int width, int height) {
        List<Integer> targets = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();
        int textMatches = 0, descriptionMatches = 0, disabled = 0;
        for (int i = 0; i < nodes.size(); i++) {
            TopupMatcher.Node label = nodes.get(i);
            if (!label.visible) continue;
            boolean textMatch = InquiryRules.isInquiryButton(TopupMatcher.normalized(label.text));
            boolean descriptionMatch = InquiryRules.isInquiryButton(TopupMatcher.normalized(label.description));
            if (!textMatch && !descriptionMatch) continue;
            if (textMatch) textMatches++;
            if (descriptionMatch) descriptionMatches++;
            if (!label.enabled || disabledControl(nodes, i)) { disabled++; continue; }
            if (onScreenButton(label, width, height)) addLabelRegion(nodes, labels, i);
            int index = i;
            for (int depth = 0; index >= 0 && depth <= 10; depth++) {
                TopupMatcher.Node candidate = nodes.get(index);
                if (!candidate.enabled && (candidate.clickable || candidate.buttonRole)) break;
                int candidateHeight = candidate.bottom - candidate.top;
                int labelHeight = label.bottom - label.top, labelWidth = label.right - label.left;
                if (candidate.visible && candidate.enabled && candidate.clickable && onScreenButton(candidate, width, height)
                    && candidateHeight <= Math.max(labelHeight * 4, labelWidth * 2)
                    && candidate.left <= label.left && candidate.top <= label.top
                    && candidate.right >= label.right && candidate.bottom >= label.bottom) {
                    boolean duplicate = false;
                    for (int priorIndex = targets.size() - 1; priorIndex >= 0; priorIndex--) {
                        int prior = targets.get(priorIndex);
                        TopupMatcher.Node other = nodes.get(prior);
                        if (index == prior || (candidate.left == other.left && candidate.top == other.top
                            && candidate.right == other.right && candidate.bottom == other.bottom)
                            || actionDescendant(nodes, index, prior)) { duplicate = true; break; }
                        if (actionDescendant(nodes, prior, index)) targets.remove(priorIndex);
                    }
                    if (!duplicate) targets.add(index);
                    break;
                }
                index = candidate.parent;
            }
        }
        return new ButtonMatch(targets.size() == 1 ? targets.get(0) : -1, targets.size(),
            targets.size() <= 1 && labels.size() == 1 ? labels.get(0) : -1, labels.size(), textMatches, descriptionMatches, disabled);
    }
    private static boolean disabledControl(List<TopupMatcher.Node> nodes, int index) {
        for (int depth = 0; index >= 0 && depth < 64; depth++) {
            TopupMatcher.Node node = nodes.get(index);
            if (!node.enabled && (node.clickable || node.buttonRole)) return true;
            index = node.parent;
        }
        return false;
    }
    private static boolean onScreenButton(TopupMatcher.Node node, int width, int height) {
        return width > 0 && height > 0 && node.left >= 0 && node.top >= 0 && node.right <= width && node.bottom <= height
            && node.right > node.left && node.bottom > node.top && node.bottom - node.top <= height / 4;
    }
    private static void addLabelRegion(List<TopupMatcher.Node> nodes, List<Integer> labels, int index) {
        TopupMatcher.Node candidate = nodes.get(index);
        for (int i = labels.size() - 1; i >= 0; i--) {
            int old = labels.get(i);
            TopupMatcher.Node prior = nodes.get(old);
            boolean sameBounds = candidate.left == prior.left && candidate.top == prior.top
                && candidate.right == prior.right && candidate.bottom == prior.bottom;
            if (sameBounds) return;
            // Use the smaller labelled child when both the button and its text expose the same exact name.
            if (actionDescendant(nodes, index, old) && candidate.left >= prior.left && candidate.top >= prior.top
                && candidate.right <= prior.right && candidate.bottom <= prior.bottom) labels.remove(i);
            else if (actionDescendant(nodes, old, index) && prior.left >= candidate.left && prior.top >= candidate.top
                && prior.right <= candidate.right && prior.bottom <= candidate.bottom) return;
        }
        labels.add(index);
    }
    private static boolean actionDescendant(List<TopupMatcher.Node> nodes, int child, int ancestor) {
        for (int depth = 0; child >= 0 && depth < 64; depth++) {
            if (child == ancestor) return true;
            child = nodes.get(child).parent;
        }
        return false;
    }
    public static Navigation navigation(InquirySession.Stage stage, boolean topup, boolean credit) {
        if (stage == InquirySession.Stage.FIND_FORM && topup) return Navigation.TOPUP;
        if ((stage == InquirySession.Stage.FIND_FORM || stage == InquirySession.Stage.OPEN_CREDIT) && credit) return Navigation.CREDIT;
        return Navigation.NONE;
    }
    public static final class Node {
        public final String text, hint, description, labelledBy, viewId;
        public final int left, top, right, bottom;
        public final boolean writable;
        public Node(String text, String hint, String description, String labelledBy, String viewId,
                    int left, int top, int right, int bottom, boolean writable) {
            this.text = clean(text); this.hint = clean(hint); this.description = clean(description);
            this.labelledBy = clean(labelledBy); this.viewId = clean(viewId);
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
            this.writable = writable;
        }
        private static String clean(String s) { return s == null ? "" : s; }
        public String label() { return text.isEmpty() ? description : text; }
    }
    public static boolean contractLabel(String s) {
        String n = TopupMatcher.normalized(s);
        return n.startsWith("nomor kontrak") || n.startsWith("masukkan nomor kontrak")
            || n.startsWith("nomor virtual account");
    }
    public static boolean providerLabel(String s) {
        String n = TopupMatcher.normalized(s).replaceAll(":$", "");
        return n.equals("penyedia layanan") || n.equals("penyedia pinjaman");
    }
    public static String combinedProvider(String s) {
        String n = TopupMatcher.normalized(s);
        String value = n.replaceFirst("^penyedia (?:layanan|pinjaman)[\\s:,]+", "");
        if (value.equals(n) || value.isEmpty() || value.length() > 80 || value.contains("nomor kontrak")) return "";
        return value;
    }
    public static String providerName(Node node) {
        String source = node.text;
        if (source.isEmpty() || providerLabel(source)) source = node.description;
        String combined = combinedProvider(source);
        return combined.isEmpty() ? source : combined;
    }
    public static boolean contractAnchor(Node node) {
        return contractLabel(node.text) || contractLabel(node.description) || contractLabel(node.hint) || contractLabel(node.labelledBy);
    }
    public static boolean topupLabel(String s) {
        return TopupMatcher.isLabel(s);
    }
    public static boolean detailLabel(String s) {
        return InquiryRules.normalized(s).replaceAll("[\\s\u2304\u2303\u25bc\u25b2\u2228\u2227^]+$", "")
            .matches("lihat detail(?: tagihan)?");
    }
    private static boolean sameBounds(Node a, Node b) {
        return a.left == b.left && a.top == b.top && a.right == b.right && a.bottom == b.bottom;
    }
    private static boolean overlapsX(Node a, Node b) {
        return Math.min(a.right, b.right) - Math.max(a.left, b.left)
            >= Math.max(1, Math.min(a.right - a.left, b.right - b.left) / 3);
    }
    private static boolean nearBelow(Node label, Node field) {
        int height = Math.max(1, Math.max(label.bottom - label.top, field.bottom - field.top));
        return overlapsX(label, field) && field.top >= label.top - height / 3
            && field.top - label.bottom <= height * 3;
    }
    /** Returns a unique semantically labelled input; unrelated search inputs are ignored. */
    public static int contractField(List<Node> nodes) {
        List<Integer> explicit = new ArrayList<>(), spatial = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            Node field = nodes.get(i);
            if (!field.writable) continue;
            if (contractLabel(field.hint) || contractLabel(field.description)
                || contractLabel(field.labelledBy) || contractLabel(field.text)) {
                addUnique(nodes, explicit, i);
                continue;
            }
            // A search box may sit immediately above a contract label in a WebView.
            String metadata = InquiryRules.normalized(field.hint + " " + field.description + " " + field.viewId);
            if (metadata.contains("cari") || metadata.contains("search")) continue;
            for (Node label : nodes) {
                if (!label.writable && contractAnchor(label) && nearBelow(label, field)) {
                    addUnique(nodes, spatial, i);
                    break;
                }
            }
        }
        if (!explicit.isEmpty()) return explicit.size() == 1 ? explicit.get(0) : -1;
        return spatial.size() == 1 ? spatial.get(0) : -1;
    }
    private static void addUnique(List<Node> nodes, List<Integer> indices, int index) {
        for (int prior : indices) if (sameBounds(nodes.get(prior), nodes.get(index))) return;
        indices.add(index);
    }
    private static void addProvider(List<Node> nodes, List<Integer> indices, int index) {
        Node candidate = nodes.get(index);
        for (int prior : indices) {
            Node previous = nodes.get(prior);
            boolean contains = (previous.left <= candidate.left && previous.top <= candidate.top
                && previous.right >= candidate.right && previous.bottom >= candidate.bottom)
                || (candidate.left <= previous.left && candidate.top <= previous.top
                && candidate.right >= previous.right && candidate.bottom >= previous.bottom);
            if (contains && InquiryRules.normalized(providerName(previous)).equals(InquiryRules.normalized(providerName(candidate)))) return;
        }
        addUnique(nodes, indices, index);
    }
    /** Provider value must be in the provider row above the contract label, not elsewhere on screen. */
    public static int providerValue(List<Node> nodes) {
        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            Node combined = nodes.get(i);
            boolean inline = (providerLabel(combined.text) && !combined.description.isEmpty() && !providerLabel(combined.description))
                || (providerLabel(combined.description) && !combined.text.isEmpty() && !providerLabel(combined.text));
            if (!inline && combinedProvider(combined.text).isEmpty() && combinedProvider(combined.description).isEmpty()) continue;
            // Combined label/value must still precede the contract area on the same form.
            for (Node contract : nodes) if (contractAnchor(contract)
                && contract.top >= combined.bottom && overlapsX(combined, contract)) {
                addProvider(nodes, matches, i); break;
            }
        }
        for (Node label : nodes) {
            if (!providerLabel(label.text) && !providerLabel(label.description)) continue;
            int contractTop = Integer.MAX_VALUE;
            for (Node contract : nodes) {
                if (contractAnchor(contract)
                    && contract.top > label.top && overlapsX(label, contract))
                    contractTop = Math.min(contractTop, contract.top);
            }
            if (contractTop == Integer.MAX_VALUE) continue;
            for (int i = 0; i < nodes.size(); i++) {
                Node value = nodes.get(i);
                String name = InquiryRules.normalized(value.label());
                // Search fields, instruction containers and the label itself are not a selected provider.
                if (value.top < label.bottom - 4 || value.bottom > contractTop || !overlapsX(label, value)
                    || name.isEmpty() || !name.matches(".*[a-z].*") || name.length() > 80 || providerLabel(name) || contractLabel(name)
                    || name.contains("cari") || name.contains("pilih penyedia")) continue;
                addProvider(nodes, matches, i);
            }
        }
        return matches.size() == 1 ? matches.get(0) : -1;
    }
    public static boolean selectedMuf(List<Node> nodes) {
        int index = providerValue(nodes);
        return index >= 0 && InquiryRules.isProvider(providerName(nodes.get(index)));
    }
    public static int providerSearch(List<Node> nodes) {
        int found = -1;
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            String label = InquiryRules.normalized(n.hint + " " + n.description + " " + n.labelledBy);
            if (!n.writable || !(label.contains("cari") || label.contains("search"))) continue;
            if (found >= 0 && !sameBounds(nodes.get(found), n)) return -1;
            found = i;
        }
        return found;
    }
}
