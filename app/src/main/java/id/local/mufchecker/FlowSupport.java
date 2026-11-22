package id.local.mufchecker;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Deterministic rules shared by event reader, diagnosis and regression tests. */
public final class FlowSupport {
    private FlowSupport() { }
    public static boolean branchRestriction(String text) {
        String s = InquiryRules.normalized(text).replaceAll("[.,;:!?]", " ").replaceAll("\\s+", " ");
        return (s.contains("pembayaran tidak dapat dilakukan") || s.contains("pembayaran tidak bisa dilakukan")
            || s.contains("transaksi tidak dapat dilakukan")) && s.contains("hubungi") && s.contains("cabang");
    }
    public static boolean belongsToInquiry(boolean active, boolean submitted, boolean observed, long submittedAt, long eventAt, long now) {
        return active && submitted && !observed && submittedAt >= 0 && eventAt >= submittedAt && eventAt <= now && now - eventAt <= 5000;
    }
    public static String timestamp(long millis) {
        return millis <= 0 ? "Tidak tersedia (sesi lama/belum selesai)" : Instant.ofEpochMilli(millis).atZone(ZoneId.of("Asia/Makassar"))
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss 'WITA'"));
    }
    public static String redact(String report, List<BulkQueue.Row> rows) {
        String safe = report;
        for (BulkQueue.Row row : rows) safe = safe.replace(row.contract, "[kontrak]");
        return safe.replaceAll("(?<![0-9])[0-9]{8,20}(?![0-9])", "[angka disamarkan]");
    }
}
