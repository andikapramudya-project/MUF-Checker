import id.local.mufchecker.UiRules;
import id.local.mufchecker.InquirySession;
import id.local.mufchecker.InquirySession.Stage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Synthetic accessibility fixtures approximate the two supplied form layouts, not a device UI test. */
public final class UiFlowTest {
    private static int checks;
    private static void ok(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static UiRules.Node label(String text, int top, int bottom) {
        return new UiRules.Node(text, "", "", "", "", 20, top, 300, bottom, false);
    }
    private static UiRules.Node input(String hint, int top, int bottom) {
        return new UiRules.Node("", hint, "", "", "", 20, top, 540, bottom, true);
    }
    private static List<UiRules.Node> form(String providerLabel, String provider, String contractLabel) {
        return new ArrayList<>(Arrays.asList(
            label(providerLabel, 180, 205), label(provider, 230, 260),
            label(contractLabel, 290, 315), input("", 330, 370)));
    }
    public static void main(String[] args) {
        List<UiRules.Node> direct = form("Penyedia Pinjaman", "Mandiri Utama Finance", "Nomor Kontrak");
        ok(UiRules.selectedMuf(direct), "Screenshot 1 provider row recognized");
        ok(UiRules.contractField(direct) == 3, "Screenshot 1 spatial contract input");
        List<UiRules.Node> combinedProvider = Arrays.asList(label("Penyedia Pinjaman\nMandiri Utama Finance", 180, 260),
            label("Nomor Kontrak", 290, 315), input("", 330, 370));
        ok(UiRules.selectedMuf(combinedProvider), "WebView may combine provider label and value");
        List<UiRules.Node> providerTree = new ArrayList<>(combinedProvider);
        providerTree.add(label("Penyedia Pinjaman", 180, 205));
        providerTree.add(label("Mandiri Utama Finance", 230, 260));
        ok(UiRules.selectedMuf(providerTree), "Combined parent plus individual children are the same provider row");
        ok(!UiRules.selectedMuf(Arrays.asList(label("Penyedia Layanan: Kredivo", 180, 260),
            label("Nomor Kontrak", 290, 315), input("", 330, 370))), "Combined provider label still rejects Kredivo");
        // Extra input in the page must not prevent using the uniquely labelled contract field.
        direct.add(input("Cari promo", 600, 640));
        ok(UiRules.contractField(direct) == 3, "Additional editable promo/search field does not block inquiry");
        direct.add(label("\u2304", 230, 260));
        ok(UiRules.selectedMuf(direct), "Dropdown arrow is not another provider value");
        List<UiRules.Node> nativeForm = form("Penyedia Layanan", "Kredivo", "Nomor Kontrak / Virtual Account");
        ok(!UiRules.selectedMuf(nativeForm), "Screenshot 5 initially Kredivo");
        ok(UiRules.providerValue(nativeForm) == 1, "Kredivo dropdown value located");
        ok(UiRules.contractField(nativeForm) == 3, "Screenshot 5 contract / virtual account label");
        nativeForm.add(label("Mandiri Utama Finance", 800, 830));
        ok(!UiRules.selectedMuf(nativeForm), "MUF elsewhere on screen is not selected provider");
        nativeForm.set(1, label("Mandiri Utama Finance", 230, 260));
        ok(UiRules.selectedMuf(nativeForm), "Selection rechecked in provider row");
        nativeForm.add(label("Penyedia Pinjaman", 950, 975));
        nativeForm.add(label("Mandiri Utama Finance", 1000, 1030));
        nativeForm.add(label("Nomor Kontrak", 1070, 1100));
        ok(!UiRules.selectedMuf(nativeForm), "Multiple provider forms rejected");
        List<UiRules.Node> hinted = Arrays.asList(input("Cari", 0, 40), input("Masukkan Nomor Kontrak", 100, 140));
        ok(UiRules.contractField(hinted) == 1, "Hint-only WebView input without separate label");
        List<UiRules.Node> described = Arrays.asList(new UiRules.Node("", "", "Nomor Kontrak", "", "", 20, 100, 540, 140, true));
        ok(UiRules.contractField(described) == 0, "Content description supplies contract semantics");
        List<UiRules.Node> labelled = Arrays.asList(new UiRules.Node("", "", "", "Nomor Kontrak", "", 20, 100, 540, 140, true));
        ok(UiRules.contractField(labelled) == 0, "LabeledBy relationship supplies contract semantics");
        List<UiRules.Node> ambiguous = Arrays.asList(input("Nomor Kontrak", 100, 140), input("Nomor Kontrak", 200, 240));
        ok(UiRules.contractField(ambiguous) < 0, "Two distinct contract inputs rejected");
        List<UiRules.Node> duplicates = Arrays.asList(input("Nomor Kontrak", 100, 140), input("Nomor Kontrak", 100, 140));
        ok(UiRules.contractField(duplicates) == 0, "Duplicate WebView node at same bounds deduplicated");
        ok(UiRules.contractField(Arrays.asList(label("Nomor Kontrak", 100, 130), input("Cari", 140, 180))) < 0,
            "Nearby search field is not a contract input");
        ok(UiRules.contractField(Arrays.asList(label("Nomor Kontrak", 100, 130), label("Masukkan Nomor Kontrak", 140, 180))) < 0,
            "Read-only text does not become an input");
        for (String title : new String[]{"Top-up &\nTagihan", "TOP UP & TAGIHAN", "Top-Up dan Tagihan", "Top\u2011up & Tagihan"})
            ok(UiRules.topupLabel(title), "Topup wrapping and dash variants: " + title);
        ok(!UiRules.topupLabel("Promo Top-up & Tagihan"), "Promo mention not a menu");
        for (String title : new String[]{"Lihat Detail", "lihat detail tagihan", "Lihat Detail \u2304"})
            ok(UiRules.detailLabel(title), "Details action variant");
        for (String title : new String[]{"Tutup Detail", "Pilih pembayaran", "Bayar", "Lihat Detail Promo"})
            ok(!UiRules.detailLabel(title), "Only expand bill details: " + title);
        ok(UiRules.navigation(Stage.FIND_FORM, true, true) == UiRules.Navigation.TOPUP, "Home prioritizes Top-up & Tagihan");
        ok(UiRules.navigation(Stage.OPEN_CREDIT, true, true) == UiRules.Navigation.CREDIT, "No repeat topup click during page transition");
        ok(UiRules.navigation(Stage.FIND_FORM, false, true) == UiRules.Navigation.CREDIT, "Already in topup page");
        ok(UiRules.navigation(Stage.OPEN_FORM, false, true) == UiRules.Navigation.NONE, "Form title is not clicked again");
        ok(UiRules.navigation(Stage.VERIFY_PROVIDER, true, true) == UiRules.Navigation.NONE, "Provider verification never navigates away");
        ok(UiRules.providerSearch(Arrays.asList(input("Cari penyedia layanan", 0, 40))) == 0, "Provider list search");
        ok(UiRules.providerSearch(hinted) == 0, "Contract input never used as provider search");
        InquirySession session = new InquirySession(1000);
        ok(!session.expired(20999), "Navigation can wait within stage limit");
        session.move(Stage.FIND_FORM, 20000);
        ok(session.expired(21000), "Repeated same stage does not extend timeout");
        session.move(Stage.SELECT_PROVIDER, 22000);
        ok(!session.expired(22001), "New stage has its own bounded wait");
        ok(session.expired(42000), "Provider lookup times out");
        for (int i = 0; i < 6; i++) ok(session.allowScroll(), "Bounded scroll " + i);
        ok(!session.allowScroll(), "No unlimited scrolling");
        session.move(Stage.VERIFY_PROVIDER, 43000);
        ok(session.scrolls() == 0, "Scroll budget resets only with stage change");
        String number = "012345678901";
        ok(!session.markSubmitted(true, true, false, number, number, 44000), "Cannot submit before input verification stage");
        session.move(Stage.VERIFY_CONTRACT, 44000);
        ok(!session.markSubmitted(false, true, false, number, number, 45000), "Kredivo cannot be submitted");
        ok(!session.markSubmitted(true, true, false, number, "999945678901", 45000), "Wrong number blocks inquiry");
        ok(!session.markSubmitted(true, true, true, number, number, 45000), "Existing checkout blocks inquiry");
        ok(!session.markDetailsOpened(45000), "Cannot expand stale details before fresh inquiry");
        ok(session.markSubmitted(true, true, false, number, number, 45000), "Fresh verified inquiry accepted once");
        ok(session.stage() == Stage.WAIT_RESULT, "Submission enters result wait");
        ok(!session.markSubmitted(true, true, false, number, number, 46000), "No repeated inquiry while waiting");
        ok(session.markDetailsOpened(46000), "Collapsed details expand after fresh inquiry");
        ok(session.stage() == Stage.READ_DETAILS, "Details read has its own timeout");
        ok(!session.markDetailsOpened(47000), "Never toggle details closed again");
        ok(session.expired(71000), "Missing due date terminates details stage");
        session.move(Stage.FIND_FORM, 120000);
        ok(session.expired(121000), "Global two minute cap survives stage changes");
        System.out.println("PASS: " + checks + " UI/flow checks (two form fixtures, provider selection, input ambiguity, navigation, details, timeouts, one-shot inquiry)");
    }
}
