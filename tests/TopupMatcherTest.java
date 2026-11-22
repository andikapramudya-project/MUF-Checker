import id.local.mufchecker.TopupMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Accessibility tree fixtures; screenshot positions are synthetic and never shipped as tap coordinates. */
public final class TopupMatcherTest {
    private static int checks;
    private static void ok(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static TopupMatcher.Node n(String text, String description, int parent,
                                      int l, int t, int r, int b, boolean visible, boolean enabled, boolean click) {
        return new TopupMatcher.Node(text, description, parent, l, t, r, b, visible, enabled, click);
    }
    private static TopupMatcher.Node root() { return n("", "", -1, 0, 0, 576, 1280, true, true, false); }
    private static TopupMatcher.Node tile(int parent, boolean click) { return n("", "", parent, 214, 430, 305, 560, true, true, click); }
    private static TopupMatcher.Node label(String text, String desc, int parent) { return n(text, desc, parent, 223, 519, 296, 552, true, true, false); }
    public static void main(String[] args) {
        for (String s : new String[]{"Top-up &\nTagihan", "Top-up\u00a0&\u00a0Tagihan", "Top\u2010up & Tagihan",
            "Top\u2011up & Tagihan", "Top\u2013up & Tagihan", "Top\u2212up & Tagihan", "Top\u200b-up & Tagihan",
            "Top-up &\u2028Tagihan", "TOP UP DAN TAGIHAN", "Topup & Tagihan", "Top-up & Tagihan, tombol"})
            ok(TopupMatcher.isLabel(s), "Menu label variant " + s);
        for (String s : new String[]{"Promo Top-up & Tagihan", "Diskon Top-up & Tagihan 10%", "Tagihan", "Top-up", "Bayar", "Pilih pembayaran"})
            ok(!TopupMatcher.isLabel(s), "Unrelated label rejected " + s);
        List<TopupMatcher.Node> simple = Arrays.asList(root(), tile(0, true), label("Top-up &\nTagihan", "", 1));
        TopupMatcher.Result found = TopupMatcher.find(simple);
        ok(found.target == 1 && found.textMatches == 1, "Click menu tile above label, not text coordinates");
        List<TopupMatcher.Node> description = Arrays.asList(root(), tile(0, true), label("ikon", "Top-up & Tagihan", 1));
        found = TopupMatcher.find(description);
        ok(found.target == 1 && found.descriptionMatches == 1, "Description checked even when text is nonempty");
        List<TopupMatcher.Node> duplicates = Arrays.asList(root(), tile(0, true),
            label("Top-up & Tagihan", "", 1), label("Top-up & Tagihan", "Top-up & Tagihan", 1),
            n("", "Top-up & Tagihan", 1, 239, 440, 278, 508, true, true, false));
        found = TopupMatcher.find(duplicates);
        ok(found.target == 1 && found.targetCount == 1 && found.textMatches == 2, "Icon and text aliases resolve to same menu action");
        List<TopupMatcher.Node> split = Arrays.asList(root(), tile(0, true),
            n("Top-up &", "", 1, 223, 519, 296, 535, true, true, false),
            n("Tagihan", "", 1, 232, 536, 288, 552, true, true, false));
        found = TopupMatcher.find(split);
        ok(found.target == 1 && found.splitMatches == 1, "Screenshot two-line label in distinct accessibility nodes");
        List<TopupMatcher.Node> inline = Arrays.asList(root(), tile(0, true),
            n("Top-up", "", 1, 218, 520, 259, 537, true, true, false),
            n("& Tagihan", "", 1, 263, 520, 303, 537, true, true, false));
        ok(TopupMatcher.find(inline).target == 1, "Inline split nodes");
        List<TopupMatcher.Node> splitDescription = Arrays.asList(root(), tile(0, true),
            n("", "Top-up &", 1, 223, 519, 296, 535, true, true, false),
            n("", "Tagihan", 1, 232, 536, 288, 552, true, true, false));
        ok(TopupMatcher.find(splitDescription).target == 1, "Split descriptions");
        List<TopupMatcher.Node> wrappers = new ArrayList<>();
        wrappers.add(root()); wrappers.add(tile(0, true));
        for (int i = 0; i < 5; i++) wrappers.add(tile(wrappers.size() - 1, false));
        wrappers.add(label("Top-up & Tagihan", "", wrappers.size() - 1));
        ok(TopupMatcher.find(wrappers).target == 1, "Click target six parent levels above label");
        List<TopupMatcher.Node> icon = Arrays.asList(root(), tile(0, false),
            n("", "", 1, 239, 440, 278, 508, true, true, true), label("Top-up & Tagihan", "", 1));
        ok(TopupMatcher.find(icon).target == 2, "Clickable icon is a sibling of label");
        List<TopupMatcher.Node> labelledContainer = Arrays.asList(root(),
            n("", "Top-up & Tagihan", 0, 214, 430, 305, 560, true, true, false),
            n("", "", 1, 239, 440, 278, 508, true, true, true));
        ok(TopupMatcher.find(labelledContainer).target == 2, "Description on container with a unique icon action");
        List<TopupMatcher.Node> splitIcon = new ArrayList<>(split);
        splitIcon.set(1, tile(0, false));
        splitIcon.add(n("", "", 1, 239, 440, 278, 508, true, true, true));
        ok(TopupMatcher.find(splitIcon).target == 4, "Split label targets unique icon in same tile");
        List<TopupMatcher.Node> twoIcons = new ArrayList<>(icon);
        twoIcons.add(n("", "", 1, 280, 440, 300, 508, true, true, true));
        ok(TopupMatcher.find(twoIcons).target < 0, "Two unlabelled sibling actions are ambiguous");
        List<TopupMatcher.Node> distinct = new ArrayList<>(simple);
        distinct.add(n("Top-up & Tagihan", "", 0, 350, 430, 450, 550, true, true, true));
        found = TopupMatcher.find(distinct);
        ok(found.target < 0 && found.targetCount == 2 && found.summary().contains("ambigu"), "Distinct menu actions rejected");
        List<TopupMatcher.Node> disabled = Arrays.asList(root(),
            n("", "", 0, 214, 430, 305, 560, true, false, true), label("Top-up & Tagihan", "", 1));
        ok(TopupMatcher.find(disabled).target < 0, "Disabled tile cannot be clicked via enabled child");
        List<TopupMatcher.Node> hidden = new ArrayList<>(simple);
        hidden.add(n("Top-up & Tagihan", "", 0, 350, 430, 450, 550, false, true, true));
        ok(TopupMatcher.find(hidden).target == 1, "Invisible duplicate does not block visible menu");
        List<TopupMatcher.Node> noAction = Arrays.asList(root(), tile(0, false), label("Top-up & Tagihan", "", 1));
        found = TopupMatcher.find(noAction);
        ok(found.target < 0 && found.textMatches == 1 && found.summary().contains("label ada"), "Separate label-present from action-unavailable diagnosis");
        ok(TopupMatcher.find(Arrays.asList(root())).summary().contains("label tidak terbaca"), "No-label diagnosis");
        List<TopupMatcher.Node> huge = Arrays.asList(
            n("", "", -1, 0, 0, 576, 1280, true, true, true), label("Top-up & Tagihan", "", 0));
        ok(TopupMatcher.find(huge).target < 0, "Never click whole activity as menu ancestor");
        List<TopupMatcher.Node> crossTiles = Arrays.asList(root(),
            n("", "", 0, 0, 430, 576, 560, true, true, true),
            n("Top-up &", "", 1, 220, 520, 290, 538, true, true, false),
            n("Tagihan", "", 1, 310, 520, 370, 538, true, true, false));
        ok(TopupMatcher.find(crossTiles).target < 0, "Fragments in different menu tiles cannot be joined");
        List<TopupMatcher.Node> neighborIcon = Arrays.asList(root(),
            n("", "", 0, 214, 430, 405, 560, true, true, false), label("Top-up & Tagihan", "", 1),
            n("", "", 1, 350, 440, 390, 508, true, true, true));
        ok(TopupMatcher.find(neighborIcon).target < 0, "A nearby icon in another column is not the Top-up action");
        List<TopupMatcher.Node> aliases = Arrays.asList(root(), tile(0, true),
            n("Top-up & Tagihan", "", 1, 239, 440, 278, 508, true, true, true),
            label("Top-up & Tagihan", "", 1));
        ok(TopupMatcher.find(aliases).target == 1, "Clickable icon and clickable parent collapse to same tile");
        // Moving or scaling the complete tile must not change matching by meaning and structure.
        for (int scale : new int[]{1, 2, 3}) {
            List<TopupMatcher.Node> moved = new ArrayList<>();
            for (int i = 0; i < split.size(); i++) {
                TopupMatcher.Node old = split.get(i);
                int dx = i == 0 ? 0 : 90, dy = i == 0 ? 0 : -200;
                moved.add(n(old.text, old.description, old.parent, (old.left + dx) * scale,
                    (old.top + dy) * scale, (old.right + dx) * scale, (old.bottom + dy) * scale,
                    old.visible, old.enabled, old.clickable));
            }
            ok(TopupMatcher.find(moved).target == 1, "Shifted/scaled menu still recognized " + scale);
        }
        ok(!TopupMatcher.find(Arrays.asList(root())).summary().contains("nomor"), "Diagnosis emits counters, not screen text");
        System.out.println("PASS: " + checks + " Top-up tree checks (text/description, Unicode, split labels, duplicate aliases, click ancestors/icons, ambiguity, visibility, shifted layout)");
    }
}
