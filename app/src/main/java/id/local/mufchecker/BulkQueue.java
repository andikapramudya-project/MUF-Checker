package id.local.mufchecker;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** Small sequential queue. Only explicitly resumed unfinished rows may be attempted again. */
public final class BulkQueue {
    public static final int LIMIT = 100;
    public enum RunState { PREPARED, RUNNING, PAUSED, STOPPED, COMPLETED }
    public enum StartMode { DIRECT_LINK, READY_PAGE }
    public enum RowState { WAITING, PROCESSING, SUCCESS, ERROR }
    public static final class Input {
        public final List<String> contracts;
        public final int duplicates;
        Input(List<String> contracts, int duplicates) { this.contracts = Collections.unmodifiableList(contracts); this.duplicates = duplicates; }
    }
    public static final class Row {
        public final String contract;
        private RowState state = RowState.WAITING;
        private String result = "", reason = "", diagnostic = "";
        private long durationMs;
        private final java.util.Map<String, Long> stageMs = new java.util.LinkedHashMap<>();
        private int recoveries;
        public long durationMs() { return durationMs; }
        public java.util.Map<String, Long> stageMs() { return Collections.unmodifiableMap(stageMs); }
        public int recoveries() { return recoveries; }
        Row(String contract) { this.contract = contract; }
        public RowState state() { return state; }
        public String result() { return result; }
        public String reason() { return reason; }
        public String diagnostic() { return diagnostic; }
    }
    public final YearMonth period;
    private String sessionId = java.util.UUID.randomUUID().toString();
    private long createdAt = System.currentTimeMillis(), finishedAt;
    private long startedAt = createdAt;
    private StartMode startMode = StartMode.DIRECT_LINK;
    public String sessionId() { return sessionId; }
    public long createdAt() { return createdAt; }
    public long startedAt() { return startedAt; }
    public StartMode startMode() { return startMode; }
    public long finishedAt() { return finishedAt; }
    public long activeMs() { long total = 0; for (Row r : rows) total += r.durationMs; return total; }
    public void addCurrentDuration(long millis) { addCurrentDuration("OTHER", millis); }
    public void addCurrentDuration(String stage, long millis) {
        if (current() == null || millis <= 0) return;
        if (!stage.matches("[A-Z_]{1,32}")) throw new IllegalArgumentException("Tahap tidak valid");
        current().durationMs += millis;
        current().stageMs.put(stage, current().stageMs.getOrDefault(stage, 0L) + millis);
    }
    public void recoveredForm() { if (current() != null) current().recoveries++; }
    private final List<Row> rows = new ArrayList<>();
    private RunState state = RunState.RUNNING;
    private boolean pauseRequested;
    private String reason = "";
    private int current = -1;
    public static Input parseLines(String input) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        int duplicates = 0, line = 0;
        for (String raw : (input == null ? "" : input).replaceFirst("^\uFEFF", "").split("\\r?\\n", -1)) {
            line++;
            String value = raw.trim();
            if (value.isEmpty()) continue;
            if (!InquiryRules.validContract(value)) throw new IllegalArgumentException("Baris " + line + ": gunakan 8–20 angka tanpa spasi di dalam nomor.");
            if (!unique.add(value)) duplicates++;
            if (unique.size() > LIMIT) throw new IllegalArgumentException("Maksimal " + LIMIT + " kontrak berbeda per antrean.");
        }
        if (unique.isEmpty()) throw new IllegalArgumentException("Masukkan 1–" + LIMIT + " nomor kontrak, satu nomor per baris.");
        return new Input(new ArrayList<>(unique), duplicates);
    }
    public BulkQueue(List<String> contracts, YearMonth period) {
        if (contracts == null || contracts.isEmpty() || contracts.size() > LIMIT || period == null) throw new IllegalArgumentException("Antrean tidak valid");
        this.period = period;
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : contracts) {
            if (!InquiryRules.validContract(value) || !unique.add(value)) throw new IllegalArgumentException("Nomor antrean tidak valid/duplikat");
            rows.add(new Row(value));
        }
    }
    public BulkQueue(List<String> contracts, YearMonth period, StartMode mode) {
        this(contracts, period);
        if (mode == null) throw new IllegalArgumentException("Mode tidak valid");
        startMode = mode;
        if (mode == StartMode.READY_PAGE) { state = RunState.PREPARED; startedAt = 0; }
    }
    /** Re-arming never starts a row or repeats a completed result. */
    public boolean prepareReadyPage() {
        if (startMode != StartMode.READY_PAGE || state == RunState.RUNNING || current >= 0 || !hasUnfinished()) return false;
        state = RunState.PREPARED; pauseRequested = false;
        reason = "Antrean disiapkan. Buka form MUF di Tokopedia, lalu tekan Mulai pada panel mengambang.";
        return true;
    }
    public boolean startPrepared() {
        if (startMode != StartMode.READY_PAGE || state != RunState.PREPARED || current >= 0 || !hasUnfinished()) return false;
        state = RunState.RUNNING; pauseRequested = false; reason = "";
        if (startedAt == 0) startedAt = System.currentTimeMillis();
        return true;
    }
    public List<Row> rows() { return Collections.unmodifiableList(rows); }
    public RunState state() { return state; }
    public String reason() { return reason; }
    public boolean pauseRequested() { return pauseRequested; }
    public int currentIndex() { return current; }
    public Row current() { return current < 0 ? null : rows.get(current); }
    public int completedCount() { int n = 0; for (Row row : rows) if (row.state == RowState.SUCCESS || row.state == RowState.ERROR) n++; return n; }
    public boolean hasUnfinished() { return completedCount() < rows.size(); }
    /** A draft may be released only if explicit Start has never armed an inquiry. */
    public boolean unstartedPreparation() {
        if (startMode != StartMode.READY_PAGE || startedAt != 0 || current >= 0 || state == RunState.RUNNING) return false;
        for (Row row : rows) if (row.state != RowState.WAITING || !row.result.isEmpty()
            || row.durationMs != 0 || row.recoveries != 0 || !row.stageMs.isEmpty()) return false;
        return true;
    }
    public boolean cancelledPreparation() {
        return unstartedPreparation() && (state == RunState.PAUSED || state == RunState.STOPPED)
            && reason.startsWith("Persiapan dibatalkan.");
    }
    public Row startNext() {
        if (state != RunState.RUNNING || current >= 0) return null;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).state == RowState.WAITING) {
            current = i; rows.get(i).state = RowState.PROCESSING; return rows.get(i);
        }
        state = RunState.COMPLETED; return null;
    }
    public void requestPause() { if (state == RunState.RUNNING) pauseRequested = true; }
    public void succeed(String verifiedContract, String result, String diagnostic) {
        if (current() == null || !current().contract.equals(verifiedContract)) throw new IllegalStateException("Kontrak hasil tidak sama dengan antrean aktif");
        if (result == null || result.isEmpty()) throw new IllegalArgumentException("Hasil kosong");
        resolve(RowState.SUCCESS, result, "", diagnostic);
    }
    public void fail(String reason, String diagnostic) { resolve(RowState.ERROR, "", reason, diagnostic); }
    private void resolve(RowState status, String result, String note, String diagnostic) {
        Row row = current();
        if (state != RunState.RUNNING || row == null || row.state != RowState.PROCESSING) throw new IllegalStateException("Tidak ada kontrak aktif");
        row.state = status; row.result = result; row.reason = note; row.diagnostic = diagnostic;
        current = -1;
        if (!hasUnfinished()) { state = RunState.COMPLETED; pauseRequested = false; finishedAt = System.currentTimeMillis(); }
        else if (pauseRequested) { state = RunState.PAUSED; reason = "Dijeda setelah kontrak terakhir selesai"; pauseRequested = false; }
    }
    public void pause(String note, String diagnostic) { suspend(RunState.PAUSED, note, diagnostic); }
    public void stop(String note, String diagnostic) { suspend(RunState.STOPPED, note, diagnostic); }
    private void suspend(RunState next, String note, String diagnostic) {
        Row row = current();
        if (row != null) {
            row.state = RowState.WAITING; row.reason = note; row.diagnostic = diagnostic;
            current = -1;
        }
        reason = note; pauseRequested = false;
        state = hasUnfinished() ? next : RunState.COMPLETED;
    }
    public boolean recoverInterrupted() {
        if (state != RunState.RUNNING && current < 0) return false;
        pause("Proses terputus. Kontrak yang belum selesai diperiksa ulang hanya setelah Lanjutkan ditekan.", current() == null ? "" : current().diagnostic);
        return true;
    }
    public boolean resume() {
        if (startMode == StartMode.READY_PAGE) return false; // Requires a fresh form check and explicit floating Start.
        if (state == RunState.RUNNING || !hasUnfinished() || current >= 0) return false;
        state = RunState.RUNNING; pauseRequested = false; reason = ""; return true;
    }
    public int errorCount() { int count = 0; for (Row row : rows) if (row.state == RowState.ERROR) count++; return count; }
    /** Explicit retry preserves successful rows and any waiting work. Never callable while running. */
    public boolean retryErrors() {
        if (state == RunState.RUNNING || state == RunState.PREPARED || current >= 0 || errorCount() == 0) return false;
        for (Row row : rows) if (row.state == RowState.ERROR) {
            row.state = RowState.WAITING; row.reason = "Menunggu pemeriksaan ulang atas permintaan pengguna";
        }
        state = RunState.PAUSED; pauseRequested = false; reason = "Baris ERROR siap diperiksa ulang. Tekan Lanjutkan antrean.";
        finishedAt = 0;
        return true;
    }
    public String serialize() {
        return serialize(true);
    }
    public String serialize(boolean includeDiagnostics) {
        Properties p = new Properties();
        p.setProperty("schema", "1"); p.setProperty("period", period.toString()); p.setProperty("state", state.name());
        p.setProperty("sessionId", sessionId); p.setProperty("createdAt", Long.toString(createdAt)); p.setProperty("finishedAt", Long.toString(finishedAt));
        p.setProperty("startMode", startMode.name()); p.setProperty("startedAt", Long.toString(startedAt));
        p.setProperty("pause", Boolean.toString(pauseRequested)); p.setProperty("reason", reason); p.setProperty("size", Integer.toString(rows.size()));
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i); String key = "row." + i + ".";
            p.setProperty(key + "contract", r.contract); p.setProperty(key + "state", r.state.name());
            p.setProperty(key + "result", r.result); p.setProperty(key + "reason", r.reason);
            p.setProperty(key + "durationMs", Long.toString(r.durationMs)); p.setProperty(key + "recoveries", Integer.toString(r.recoveries));
            for (java.util.Map.Entry<String, Long> timing : r.stageMs.entrySet()) p.setProperty(key + "stage." + timing.getKey(), Long.toString(timing.getValue()));
            if (includeDiagnostics) p.setProperty(key + "diagnostic", r.diagnostic);
        }
        try { StringWriter out = new StringWriter(); p.store(out, "MUF queue v1"); return out.toString(); }
        catch (java.io.IOException impossible) { throw new IllegalStateException(impossible); }
    }
    public static BulkQueue restore(String raw) {
        try {
            Properties p = new Properties(); p.load(new StringReader(raw));
            if (!"1".equals(p.getProperty("schema"))) throw new IllegalArgumentException("Versi antrean tidak dikenal");
            int size = Integer.parseInt(p.getProperty("size"));
            if (size < 1 || size > LIMIT) throw new IllegalArgumentException("Ukuran antrean tidak valid");
            List<String> numbers = new ArrayList<>();
            for (int i = 0; i < size; i++) numbers.add(p.getProperty("row." + i + ".contract"));
            BulkQueue queue = new BulkQueue(numbers, YearMonth.parse(p.getProperty("period")));
            queue.sessionId = p.getProperty("sessionId", "legacy-v040");
            queue.createdAt = Long.parseLong(p.getProperty("createdAt", "0"));
            queue.startMode = StartMode.valueOf(p.getProperty("startMode", "DIRECT_LINK"));
            queue.startedAt = Long.parseLong(p.getProperty("startedAt", Long.toString(queue.createdAt)));
            if (queue.createdAt == 0) queue.startedAt = 0;
            queue.finishedAt = Long.parseLong(p.getProperty("finishedAt", "0"));
            queue.state = RunState.valueOf(p.getProperty("state")); queue.pauseRequested = Boolean.parseBoolean(p.getProperty("pause"));
            if (queue.state == RunState.PREPARED && queue.startMode != StartMode.READY_PAGE) throw new IllegalArgumentException("Mode persiapan tidak konsisten");
            queue.reason = p.getProperty("reason", "");
            // Group once instead of scanning every property again for each row of a large session.
            Map<String, List<String>> stagesByRow = new HashMap<>();
            for (String property : p.stringPropertyNames()) {
                int stageAt = property.indexOf(".stage.");
                if (stageAt < 0) continue;
                String prefix = property.substring(0, stageAt + 1);
                List<String> stages = stagesByRow.get(prefix);
                if (stages == null) { stages = new ArrayList<>(); stagesByRow.put(prefix, stages); }
                stages.add(property);
            }
            for (int i = 0; i < size; i++) {
                Row r = queue.rows.get(i); String key = "row." + i + ".";
                r.state = RowState.valueOf(p.getProperty(key + "state")); r.result = p.getProperty(key + "result", "");
                r.reason = p.getProperty(key + "reason", ""); r.diagnostic = p.getProperty(key + "diagnostic", "");
                r.durationMs = Math.max(0, Long.parseLong(p.getProperty(key + "durationMs", "0")));
                r.recoveries = Math.max(0, Integer.parseInt(p.getProperty(key + "recoveries", "0")));
                for (String property : stagesByRow.getOrDefault(key, Collections.emptyList())) {
                    String stage = property.substring((key + "stage.").length());
                    if (!stage.matches("[A-Z_]{1,32}")) throw new IllegalArgumentException("Tahap tersimpan tidak valid");
                    r.stageMs.put(stage, Math.max(0, Long.parseLong(p.getProperty(property))));
                }
                if (r.state == RowState.SUCCESS && r.result.isEmpty()) throw new IllegalArgumentException("Hasil antrean hilang");
                if (r.state == RowState.PROCESSING) {
                    if (queue.current >= 0 || queue.state != RunState.RUNNING) throw new IllegalArgumentException("Kontrak aktif tidak konsisten");
                    queue.current = i;
                }
            }
            if (queue.state == RunState.COMPLETED && queue.hasUnfinished()) throw new IllegalArgumentException("Antrean selesai tidak konsisten");
            return queue;
        } catch (Exception e) { throw new IllegalArgumentException("Data antrean tidak dapat dibaca", e); }
    }
}
