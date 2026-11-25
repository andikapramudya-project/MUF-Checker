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
    // ---- 0.9.0 motion and gesture math (pure; no Android types, so host tests can pin the numbers) ----
    /** Spring: mass 1, stiffness 320, damping ratio 0.72; played over MufTheme.MOTION_SPRING (450 ms). */
    static final double SPRING_STIFFNESS = 320, SPRING_DAMPING = .72, SPRING_SECONDS = .45;
    /**
     * Closed-form under-damped spring from 0 to 1 for an animator fraction 0..1. Peaks about 3.8% past the target,
     * then settles; the last frame is exactly 1 so views always rest on their end state.
     */
    public static float spring(float fraction) {
        if (fraction <= 0f) return 0f;
        if (fraction >= 1f) return 1f;
        double omega = Math.sqrt(SPRING_STIFFNESS), damped = omega * Math.sqrt(1 - SPRING_DAMPING * SPRING_DAMPING);
        double t = fraction * SPRING_SECONDS, decay = Math.exp(-SPRING_DAMPING * omega * t);
        return (float) (1 - decay * (Math.cos(damped * t) + SPRING_DAMPING * omega / damped * Math.sin(damped * t)));
    }
    /**
     * Travel lag of the navigation indicator (0..1): zero at rest and at both ends of a move, largest mid-travel,
     * proportional to how many tabs it crosses (capped at one).
     */
    public static float travelLag(float fraction, float tabsTravelled) {
        float f = Math.max(0f, Math.min(1f, fraction)), distance = Math.max(0f, Math.min(1f, Math.abs(tabsTravelled)));
        return 4f * f * (1f - f) * distance;
    }
    /** Stretch while travelling: wider and slightly flatter, back to 1 at rest. */
    public static float stretchX(float lag) { return 1f + Math.max(0f, Math.min(1f, lag)) * .16f; }
    public static float stretchY(float lag) { return 1f - Math.max(0f, Math.min(1f, lag)) * .08f; }
    /** Which of {@code count} equal tabs/segments sits under x; outside positions clamp to the nearest end. */
    public static int segmentAt(float x, float width, int count) {
        if (count <= 0 || width <= 0) return 0;
        return Math.max(0, Math.min(count - 1, (int) Math.floor(x / (width / count))));
    }
    /** A sheet is dismissed after a drag past 25% of its height or a downward fling faster than 1000 dp/s. */
    public static boolean dismissSheet(float dragPx, float heightPx, float velocityPxPerSecond, float density) {
        if (dragPx <= 0f) return false;
        return dragPx > heightPx * .25f || velocityPxPerSecond > 1000f * density;
    }
}
