package id.local.mufchecker;

/** Compact product copy and rendering keys, independent of Android and technical diagnosis. */
public final class UiPresentation {
    private UiPresentation() { }
    public static String panel(boolean preparing, boolean ready, int index, int total,
                               InquirySession.Stage stage, boolean pauseRequested) {
        if (preparing) return ready ? "MUF · " + total + " kontrak" : "Menunggu form";
        String label = pauseRequested ? "Menyelesaikan" : stage == InquirySession.Stage.READ_DETAILS ? "Membaca"
            : stage == InquirySession.Stage.RETURN_FORM || stage == InquirySession.Stage.OPEN_LINK
                || stage == InquirySession.Stage.FIND_FORM ? "Menyiapkan" : "Memeriksa";
        return Math.max(1, index + 1) + "/" + total + " · " + label;
    }
    public static String duration(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        return seconds < 60 ? seconds + "d" : seconds / 60 + "m " + seconds % 60 + "d";
    }
    /** Labels always carry meaning in text, including for monochrome and screen readers. */
    public static String statusLabel(String status) {
        if ("SUDAH BAYAR".equals(status)) return "✓ Sudah bayar";
        if ("BELUM BAYAR".equals(status)) return "○ Belum bayar";
        if ("DIPROSES".equals(status)) return "… Diproses";
        if ("MENUNGGU".equals(status)) return "· Menunggu";
        return "! Error";
    }
    /** Compact table copy; error, processing and waiting never imply a payment result. */
    public static String tableStatusLabel(String status) {
        if("SUDAH BAYAR".equals(status))return "Bayar";
        if("BELUM BAYAR".equals(status))return "Belum";
        if("DIPROSES".equals(status))return "Proses";
        if("MENUNGGU".equals(status))return "Tunggu";
        return "Error";
    }
    public static boolean matchesResult(String filter, String query, String contract, String name, String status) {
        if ("Belum".equals(filter) && !"BELUM BAYAR".equals(status)
            || "Sudah".equals(filter) && !"SUDAH BAYAR".equals(status)
            || "Error".equals(filter) && !"ERROR".equals(status)) return false;
        String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        return ((contract == null ? "" : contract) + " " + (name == null ? "" : name))
            .toLowerCase(java.util.Locale.ROOT).contains(needle);
    }
    /** Timing checkpoints never invalidate unchanged result rows. Content changes always do. */
    public static String tableKey(BulkQueue queue) {
        if (queue == null) return "empty";
        StringBuilder key = new StringBuilder(queue.sessionId()).append('|').append(queue.state()).append('|').append(queue.currentIndex());
        for (BulkQueue.Row row : queue.rows()) key.append('|').append(row.state()).append(':')
            .append(row.result().length()).append(':').append(row.result()).append(':').append(row.reason().length()).append(':').append(row.reason());
        return key.toString();
    }
}
