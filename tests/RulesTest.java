import id.local.mufchecker.InquiryRules;
import id.local.mufchecker.BillReader;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Run without Android or network. Synthetic fixtures only: no live debtor inquiry. */
public final class RulesTest {
    private static int checks;
    private static void ok(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void rejected(Runnable action, String message) {
        checks++;
        try { action.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError(message);
    }
    public static void main(String[] args) {
        String number = "012345678901";
        ok(InquiryRules.validContract(number), "Leading zero preserved");
        ok(!InquiryRules.validContract("1e10"), "No numeric coercion");
        ok(!InquiryRules.validContract("0123 45678901"), "No whitespace inside contract");
        ok(!InquiryRules.validContract("1234567"), "Reject too short");
        for (String date : new String[]{"02-OCT-26", "02-OKT-2026", "02 Oktober 2026", "02/10/2026"})
            ok(InquiryRules.parseDate(date).equals(LocalDate.of(2026, 10, 2)), "Date alias " + date);
        rejected(() -> InquiryRules.parseDate("31-NOV-26"), "Invalid calendar date");
        rejected(() -> InquiryRules.parseDate("02-OCT-26 02-NOV-26"), "Ambiguous dates rejected");
        rejected(() -> InquiryRules.parseDate("tagihan tidak tersedia"), "No date must not mean paid");
        rejected(() -> InquiryRules.parseDate("02/13/2026"), "Invalid month rejected");
        YearMonth october = YearMonth.of(2026, 10);
        ok(InquiryRules.status(LocalDate.of(2026, 10, 2), october).equals("TERINDIKASI BELUM BAYAR"), "Same month");
        ok(InquiryRules.status(LocalDate.of(2026, 11, 2), october).equals("TERINDIKASI SUDAH BAYAR"), "Future month");
        ok(InquiryRules.status(LocalDate.of(2026, 9, 2), october).equals("JATUH TEMPO TERTINGGAL"), "Arrears");
        ok(InquiryRules.status(LocalDate.of(2027, 1, 2), YearMonth.of(2026, 12)).equals("TERINDIKASI SUDAH BAYAR"), "Year rollover");
        ok(InquiryRules.status(LocalDate.of(2025, 11, 2), october).equals("JATUH TEMPO TERTINGGAL"), "Compare years, not month name only");
        ok(InquiryRules.identityMatches(number, "01234567****"), "Masked prefix");
        ok(InquiryRules.identityMatches(number, "012345******901"), "Fixed-width mask");
        ok(!InquiryRules.identityMatches(number, "99994567****"), "Wrong contract rejected");
        ok(!InquiryRules.identityMatches(number, "********8901"), "Too little identity to verify");
        ok(!InquiryRules.identityMatches(number, null), "Missing identity rejected");
        ok(!InquiryRules.identityMatches(number, "012345678902"), "Full wrong contract rejected");
        ok(InquiryRules.maySubmit(true, true, false, number, number, false), "Verified inquiry allowed");
        ok(!InquiryRules.maySubmit(false, true, false, number, number, false), "Wrong provider blocks submission");
        ok(!InquiryRules.maySubmit(true, false, false, number, number, false), "No form blocks submission");
        ok(!InquiryRules.maySubmit(true, true, true, number, number, false), "Never submit checkout");
        ok(!InquiryRules.maySubmit(true, true, false, number, "012345678902", false), "Wrong field blocks submission");
        ok(!InquiryRules.maySubmit(true, true, false, number, number, true), "Never submit twice");
        for (String forbidden : new String[]{"Pilih pembayaran", "Lanjut", "Bayar sekarang", "Konfirmasi", "Bayar Rp4.098.000"})
            ok(!InquiryRules.isInquiryButton(forbidden), "Payment action not allowlisted: " + forbidden);

        List<BillReader.Cell> cells = new ArrayList<>(Arrays.asList(
            new BillReader.Cell("Nomor Pelanggan", 20, 100, 200, 130),
            new BillReader.Cell("01234567****", 300, 101, 450, 131),
            new BillReader.Cell("Jatuh Tempo", 20, 200, 200, 230),
            new BillReader.Cell("02-OCT-26", 300, 201, 450, 231),
            new BillReader.Cell("Total Tagihan", 20, 300, 200, 330),
            new BillReader.Cell("Rp4.098.000", 300, 301, 450, 331),
            new BillReader.Cell("PROMO 02-NOV-26", 20, 450, 450, 480)));
        Map<String, String> fields = BillReader.fields(cells);
        ok(BillReader.verifiedDue(fields, number).equals(LocalDate.of(2026, 10, 2)), "Labelled due ignores unrelated promo date");
        rejected(() -> BillReader.verifiedDue(fields, "999945678901"), "Stale result from other contract rejected");
        cells.add(new BillReader.Cell("Jatuh Tempo: 02-NOV-26", 20, 500, 450, 530));
        rejected(() -> BillReader.verifiedDue(BillReader.fields(cells), number), "Conflicting labelled dates rejected");
        Map<String, String> combined = BillReader.fields(Arrays.asList(
            new BillReader.Cell("Nomor Pelanggan: 01234567****", 0, 0, 500, 40),
            new BillReader.Cell("Jatuh Tempo\n02-NOV-26", 0, 50, 500, 100)));
        ok(BillReader.verifiedDue(combined, number).equals(LocalDate.of(2026, 11, 2)), "Combined accessible nodes");
        System.out.println("PASS: " + checks + " checks (date, period, identity, stale result, inquiry guards, spatial extraction)");
    }
}
