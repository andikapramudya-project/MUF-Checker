import id.local.mufchecker.BillReader;
import id.local.mufchecker.DiagnosticTrace;
import id.local.mufchecker.InquirySession;
import id.local.mufchecker.InquirySession.Stage;
import id.local.mufchecker.UiRules;
import id.local.mufchecker.TopupMatcher;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Direct-link form fixtures and polling sequence. No Android device or live customer data. */
public final class DirectFlowTest {
    private static int checks;
    private static void ok(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static UiRules.Node n(String text, String hint, String desc, String labelled,
                                  int top, int bottom, boolean writable) {
        return new UiRules.Node(text, hint, desc, labelled, "", 14, top, 560, bottom, writable);
    }
    public static void main(String[] args) {
        ok(!UiRules.writable(true, false, true, "android.widget.Button"), "Button advertising SET_TEXT is not an input; preserve Bayar label");
        ok(UiRules.writable(true, true, true, "android.widget.EditText"), "Contract input remains writable");
        ok(UiRules.writable(true, false, true, "android.view.View"), "Web input with SET_TEXT but no editable flag remains usable");
        List<TopupMatcher.Node> button = Arrays.asList(
            new TopupMatcher.Node("", "", -1, 0, 0, 576, 1280, true, true, false),
            new TopupMatcher.Node("", "Bayar", 0, 14, 717, 560, 790, true, true, true),
            new TopupMatcher.Node("Bayar", "", 1, 250, 740, 310, 765, true, true, false),
            new TopupMatcher.Node("Bayar", "", 1, 250, 740, 310, 765, true, true, false));
        ok(UiRules.inquiryAction(button).target == 1 && UiRules.inquiryAction(button).count == 1,
            "WebView button plus two text aliases resolve to one inquiry action");
        ok(UiRules.inquiryAction(button).gestureTarget == 2 && UiRules.inquiryAction(button).summary().contains("GESTURE_LABEL"),
            "Regression 0.2.3: ACTION_CLICK advertised but inert; prefer one physical tap on the enabled label from the outset");
        List<TopupMatcher.Node> twoActions = new java.util.ArrayList<>(button);
        twoActions.add(new TopupMatcher.Node("Cek", "", 0, 14, 800, 560, 870, true, true, true));
        ok(UiRules.inquiryAction(twoActions).target < 0, "Two different inquiry controls remain ambiguous");
        ok(UiRules.inquiryAction(twoActions).gestureTarget < 0, "Do not prefer a gesture when distinct inquiry buttons are ambiguous");
        List<TopupMatcher.Node> disabled = new java.util.ArrayList<>(button);
        disabled.set(1, new TopupMatcher.Node("Bayar", "", 0, 14, 717, 560, 790, true, false, true));
        ok(UiRules.inquiryAction(disabled).target < 0, "Enabled text aliases cannot bypass disabled Bayar");
        List<TopupMatcher.Node> nested = new java.util.ArrayList<>(button);
        nested.set(2, new TopupMatcher.Node("Bayar", "", 1, 250, 740, 310, 765, true, true, true));
        ok(UiRules.inquiryAction(nested).count == 1, "Nested clickable aliases are still one button");
        List<TopupMatcher.Node> noClick = Arrays.asList(button.get(0),
            new TopupMatcher.Node("Bayar", "", 0, 250, 740, 310, 765, true, true, false));
        UiRules.ButtonMatch noClickMatch = UiRules.inquiryAction(noClick, 576, 1280);
        ok(noClickMatch.target < 0 && noClickMatch.gestureTarget == 1 && noClickMatch.gestureCount == 1,
            "Enabled Bayar label without ACTION_CLICK resolves to one live tap region");
        ok(noClickMatch.summary().contains("teks=1") && noClickMatch.summary().contains("GESTURE_LABEL"), "Gesture diagnostics distinguish label from click action");
        List<TopupMatcher.Node> descTap = Arrays.asList(button.get(0),
            new TopupMatcher.Node("", "Bayar", 0, 14, 717, 560, 790, true, true, false, true));
        ok(UiRules.inquiryAction(descTap).gestureTarget == 1, "Button description supports live tap region without click flag");
        List<TopupMatcher.Node> noClickAliases = Arrays.asList(button.get(0),
            new TopupMatcher.Node("", "Bayar", 0, 14, 717, 560, 790, true, true, false, true),
            new TopupMatcher.Node("Bayar", "", 1, 250, 740, 310, 765, true, true, false),
            new TopupMatcher.Node("Bayar", "", 1, 250, 740, 310, 765, true, true, false));
        ok(UiRules.inquiryAction(noClickAliases).gestureTarget == 2, "Button/text duplicates collapse to smaller labelled child for tap");
        List<TopupMatcher.Node> ambiguousTap = new java.util.ArrayList<>(noClick);
        ambiguousTap.add(new TopupMatcher.Node("Bayar", "", 0, 250, 840, 310, 865, true, true, false));
        ok(UiRules.inquiryAction(ambiguousTap).gestureTarget < 0, "Two distinct Bayar regions never produce an arbitrary tap");
        List<TopupMatcher.Node> disabledRole = Arrays.asList(button.get(0),
            new TopupMatcher.Node("", "", 0, 14, 717, 560, 790, true, false, false, true),
            new TopupMatcher.Node("Bayar", "", 1, 250, 740, 310, 765, true, true, false));
        ok(UiRules.inquiryAction(disabledRole).gestureTarget < 0, "Disabled HTML button blocks gesture even when child text is enabled");
        List<TopupMatcher.Node> inertContainer = Arrays.asList(
            new TopupMatcher.Node("", "", -1, 0, 0, 576, 1280, true, false, false),
            new TopupMatcher.Node("Bayar", "", 0, 14, 717, 560, 790, true, true, true));
        ok(UiRules.inquiryAction(inertContainer).target == 1, "Noninteractive container enabled flag does not override enabled child button");
        List<TopupMatcher.Node> invalidRootBounds = Arrays.asList(
            new TopupMatcher.Node("", "", -1, 0, 0, 0, 0, true, true, false), button.get(1));
        ok(UiRules.inquiryAction(invalidRootBounds, 576, 1280).target == 1, "Physical viewport works when root has empty bounds");
        for (TopupMatcher.Node bad : Arrays.asList(
            new TopupMatcher.Node("Bayar", "", 0, 14, 717, 560, 790, false, true, false),
            new TopupMatcher.Node("Bayar", "", 0, 14, 717, 560, 790, true, false, false),
            new TopupMatcher.Node("Bayar", "", 0, 14, 1300, 560, 1390, true, true, false),
            new TopupMatcher.Node("Bayar", "", 0, 0, 0, 576, 1280, true, true, false),
            new TopupMatcher.Node("Bayar", "", 0, 250, 740, 250, 765, true, true, false))) {
            ok(UiRules.inquiryAction(Arrays.asList(button.get(0), bad), 576, 1280).gestureTarget < 0,
                "Invisible/disabled/offscreen/oversized/empty regions cannot be tapped");
        }
        for (int scale : new int[]{1, 2, 3}) {
            List<TopupMatcher.Node> movedTap = Arrays.asList(
                new TopupMatcher.Node("", "", -1, 0, 0, 576 * scale, 1280 * scale, true, true, false),
                new TopupMatcher.Node("Bayar", "", 0, 210 * scale, 640 * scale, 270 * scale, 665 * scale, true, true, false));
            ok(UiRules.inquiryAction(movedTap).gestureTarget == 1, "Live label matching survives menu movement and device scale " + scale);
        }
        for (String forbidden : new String[]{"Pilih pembayaran", "Bayar sekarang", "Bayar Rp4.098.000", "Konfirmasi"}) {
            List<TopupMatcher.Node> payment = Arrays.asList(button.get(0),
                new TopupMatcher.Node(forbidden, "", 0, 14, 717, 560, 790, true, true, true));
            ok(UiRules.inquiryAction(payment).target < 0, "Payment action never matched: " + forbidden);
            ok(UiRules.inquiryAction(payment).gestureTarget < 0, "No gesture on payment action: " + forbidden);
        }
        List<UiRules.Node> web = Arrays.asList(
            n("Penyedia Pinjaman", "", "", "", 215, 240, false),
            n("Mandiri Utama Finance", "", "", "", 268, 310, true),
            n("Nomor Kontrak", "", "", "", 340, 365, false),
            n("", "Masukkan Nomor Kontrak", "", "", 376, 424, true));
        ok(UiRules.selectedMuf(web), "Direct WebView provider can itself be exposed as an editable input");
        ok(UiRules.contractField(web) == 3, "Two editable fields still identify only contract field");
        List<UiRules.Node> described = Arrays.asList(
            n("Mandiri Utama Finance", "", "Penyedia Pinjaman", "", 215, 310, true),
            n("012345678901", "", "Nomor Kontrak", "", 376, 424, true));
        ok(UiRules.selectedMuf(described), "Provider label in description; populated contract description anchors row");
        ok(UiRules.contractField(described) == 1, "Populated field retains contract semantics through description");
        List<UiRules.Node> labelled = Arrays.asList(
            n("Penyedia Pinjaman", "", "Mandiri Utama Finance", "", 215, 310, false),
            n("012345678901", "", "", "Nomor Kontrak", 376, 424, true));
        ok(UiRules.selectedMuf(labelled), "Provider value in description and labelledBy-only contract");
        ok(UiRules.contractField(labelled) == 1, "LabeledBy still resolves populated field");
        List<UiRules.Node> wrongProvider = Arrays.asList(
            n("Kredivo", "", "Penyedia Layanan", "", 215, 310, false),
            n("", "Nomor Kontrak / Virtual Account", "", "", 376, 424, true),
            n("Mandiri Utama Finance", "", "", "", 800, 830, false));
        ok(!UiRules.selectedMuf(wrongProvider), "Direct URL does not substitute for verifying selected MUF");
        ok(UiRules.contractLabel("Nomor\u00a0Kontrak"), "NBSP in web form contract label");
        ok(UiRules.providerLabel("Penyedia\u00a0Pinjaman"), "NBSP in web form provider label");
        ok(UiRules.verification(true, false, false) == UiRules.Verification.CLOSE_KEYBOARD,
            "Keyboard hides provider and field: dismiss first rather than reject form");
        ok(UiRules.verification(true, true, true) == UiRules.Verification.CLOSE_KEYBOARD, "Visible fields still wait for keyboard dismissal");
        ok(UiRules.verification(false, false, true) == UiRules.Verification.WAIT_FORM, "Await provider after keyboard animation");
        ok(UiRules.verification(false, true, true) == UiRules.Verification.READ_NUMBER, "Verify number only on restored form");
        InquirySession session = new InquirySession(0);
        session.move(Stage.OPEN_LINK, 0);
        ok(UiRules.navigation(session.stage(), true, true) == UiRules.Navigation.NONE, "Direct route does not depend on homepage menu");
        ok(!session.markInputAttempt(), "No writes before verified form stage");
        String expected = "012345678901";
        ok(session.tapState() == InquirySession.TapState.NONE && !session.resultObserved(), "Opening form is not an inquiry attempt or result");
        ok(!session.markTapDispatched() && !session.markTapCompleted() && !session.markResultObserved(), "No gesture callbacks or results before reserved attempt");
        ok(!session.markSubmitted(true, true, false, expected, expected, 1000), "Cannot submit directly from OPEN_LINK");
        ok(!session.markDetailsOpened(1000), "Old checkout cannot open details before fresh inquiry");
        session.move(Stage.FILL_CONTRACT, 2000);
        ok(session.markInputAttempt(), "First write allowed on verified form");
        session.move(Stage.VERIFY_CONTRACT, 2100);
        ok(!session.retryEmptyInput("", 3000), "Allow asynchronous text update to settle");
        ok(!session.retryEmptyInput("999945678901", 4200), "Different nonempty number is never silently overwritten");
        ok(session.retryEmptyInput("", 4200), "An empty field allows one bounded retry after settle");
        ok(session.markInputAttempt(), "Second write after focus retry");
        session.move(Stage.VERIFY_CONTRACT, 4300);
        ok(!session.retryEmptyInput("", 7000), "No third write even if field remains empty");
        ok(session.inputAttempts() == 2, "Write limit tracks both attempts");
        ok(!session.markSubmitted(false, true, false, expected, expected, 7000), "Wrong provider still blocks inquiry");
        ok(!session.markSubmitted(true, true, true, expected, expected, 7000), "Checkout cannot be resubmitted");
        ok(session.markSubmitted(true, true, false, expected, expected, 7000), "Verified contract submits once");
        ok(session.tapState() == InquirySession.TapState.RESERVED && !session.resultObserved(), "Reserving one tap does not claim delivery or a result");
        ok(!session.markTapCompleted(), "Completion cannot precede dispatch acceptance");
        ok(session.markTapDispatched() && !session.markTapDispatched(), "Android dispatch acceptance recorded once");
        ok(!session.resultObserved(), "Dispatch acceptance is not proof of a bill response");
        ok(session.markTapCompleted() && session.tapState() == InquirySession.TapState.COMPLETED,
            "Android completion is distinct from result page observation");
        ok(!session.resultObserved(), "Regression: successful gesture callback with unchanged form is not reported as opened results");
        ok(!session.markTapCompleted() && !session.markTapCancelled() && !session.markTapRejected(), "Late callbacks cannot overwrite completed tap state");
        ok(!session.markInputAttempt(), "No edits after inquiry");
        ok(!session.markSubmitted(true, true, false, expected, expected, 7100), "No second inquiry during results wait");
        ok(session.markResultObserved() && session.resultObserved(), "Record results only when a fresh result screen is actually observed");
        ok(session.markDetailsOpened(8000), "Fresh result can expand details");
        ok(!session.markDetailsOpened(9000), "Do not collapse details by repeated toggle");
        Map<String, String> result = new HashMap<>();
        result.put("nomor pelanggan", "01234567****"); result.put("jatuh tempo", "02-OCT-26");
        ok(BillReader.verifiedDue(result, expected).equals(LocalDate.of(2026, 10, 2)), "Direct flow retains masked identity and labelled date verification");
        session.move(Stage.FILL_CONTRACT, 10000);
        ok(!session.markInputAttempt(), "Moving back to input stage cannot bypass submitted guard");
        InquirySession cancelled = new InquirySession(0);
        cancelled.move(Stage.VERIFY_CONTRACT, 0);
        ok(cancelled.markSubmitted(true, true, false, expected, expected, 0) && cancelled.markTapDispatched(), "Prepare cancelled tap");
        ok(cancelled.markTapCancelled() && cancelled.tapState() == InquirySession.TapState.CANCELLED, "Cancellation recorded independently from dispatch");
        ok(!cancelled.markResultObserved() && !cancelled.markTapCompleted(), "Cancelled tap does not create a result or complete afterwards");
        ok(!cancelled.markSubmitted(true, true, false, expected, expected, 1000), "No second inquiry after gesture cancellation");
        InquirySession rejected = new InquirySession(0);
        rejected.move(Stage.VERIFY_CONTRACT, 0);
        rejected.markSubmitted(true, true, false, expected, expected, 0);
        ok(rejected.markTapRejected() && rejected.tapState() == InquirySession.TapState.REJECTED, "Dispatch rejection recorded");
        ok(!rejected.markTapDispatched() && !rejected.markResultObserved(), "Rejected dispatch cannot imply a tap or a result");
        ok(!rejected.markSubmitted(true, true, false, expected, expected, 1000), "No repeated attempt after rejected dispatch");
        InquirySession timeout = new InquirySession(100);
        timeout.move(Stage.OPEN_LINK, 100);
        ok(!timeout.expired(20099) && timeout.expired(20100), "Direct link has bounded load time");
        DiagnosticTrace trace = new DiagnosticTrace();
        trace.event("OPEN_LINK", "Membuka tautan MUF langsung");
        trace.record("OPEN_LINK", "FIRST: root tersedia; form belum terbaca");
        trace.record("OPEN_LINK", "LAST: penyedia MUF dan form terbaca");
        trace.record("VERIFY_CONTRACT", "Nomor cocok: true");
        trace.event("WAIT_RESULT", "Inquiry dikirim satu kali");
        trace.record("READ_DETAILS", "Identitas cocok: true; tanggal terbaca: true");
        trace.finish("Selesai");
        String report = trace.render();
        ok(report.contains("FIRST:") && report.contains("LAST:"), "Successful result does not overwrite early form diagnosis");
        ok(report.indexOf("OPEN_LINK / awal") < report.indexOf("READ_DETAILS / awal"), "Stage history remains chronological");
        ok(report.contains("Inquiry dikirim satu kali") && report.contains("Hasil sesi: Selesai"), "Actions and terminal outcome retained");
        ok(!report.contains(expected) && !report.contains("01234567****"), "Metadata trace contains no contract values");
        for (int i = 0; i < 100; i++) trace.event("READ_DETAILS", "Action " + i);
        ok(trace.render().contains("Membuka tautan MUF langsung") && trace.render().contains("Action 99"), "Bounded event history keeps launch and latest action");
        ok(!trace.render().contains("Action 0\n"), "Old repeated actions do not grow trace indefinitely");
        ok(!new DiagnosticTrace().render().contains("FIRST:"), "Next session cannot inherit previous diagnostic trace");
        System.out.println("PASS: " + checks + " direct-link checks (WebView provider/input semantics, keyboard restoration, bounded writes, fresh inquiry, stage trace)");
    }
}
