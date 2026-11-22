package id.local.mufchecker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded first/latest stage snapshots and action history. Callers supply metadata, never screen values. */
public final class DiagnosticTrace {
    private static final class Frame { String first, latest; Frame(String report) { first = latest = report; } }
    private final Map<String, Frame> stages = new LinkedHashMap<>();
    private final List<String> events = new ArrayList<>();
    private String outcome = "Pemeriksaan masih berjalan";
    private final String route;
    public DiagnosticTrace() { this(false); }
    public DiagnosticTrace(boolean readyPage) { route = readyPage ? "HALAMAN_TOKOPEDIA_SIAP" : "TAUTAN_MUF_LANGSUNG"; }
    public void record(String stage, String report) {
        Frame frame = stages.get(stage);
        if (frame != null) frame.latest = report;
        else if (stages.size() < 12) stages.put(stage, new Frame(report));
    }
    public void event(String stage, String action) {
        String value = stage + ": " + action;
        if (!events.isEmpty() && value.equals(events.get(events.size() - 1))) return;
        if (events.size() >= 32) events.remove(1); // Keep the initial launch event.
        events.add(value);
    }
    public void finish(String reason) { outcome = reason; }
    public String render() {
        StringBuilder out = new StringBuilder("DIAGNOSIS PER TAHAP\nJalur: " + route + "\n");
        for (Map.Entry<String, Frame> entry : stages.entrySet()) {
            Frame frame = entry.getValue();
            out.append("\n--- ").append(entry.getKey()).append(" / awal ---\n").append(frame.first).append('\n');
            if (!frame.latest.equals(frame.first))
                out.append("\n--- ").append(entry.getKey()).append(" / terakhir ---\n").append(frame.latest).append('\n');
        }
        out.append("\n--- Riwayat aksi ---\n");
        for (String event : events) out.append(event).append('\n');
        return out.append("\nHasil sesi: ").append(outcome).toString();
    }
}
