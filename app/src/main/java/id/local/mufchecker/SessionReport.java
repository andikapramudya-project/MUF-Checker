package id.local.mufchecker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Report contains ordinals, timings and sanitized technical traces; never raw result JSON. */
public final class SessionReport {
    private SessionReport() { }
    public static String render(BulkQueue queue, List<String> reports) {
        StringBuilder out = new StringBuilder("MUF Checker 0.8.6 — DIAGNOSIS SESI\n");
        out.append("Periode: ").append(queue.period).append("\nStatus: ").append(queue.state());
        out.append("\nMode: ").append(queue.startMode() == BulkQueue.StartMode.READY_PAGE ? "HALAMAN_TOKOPEDIA_SIAP" : "TAUTAN_MUF_LANGSUNG");
        out.append("\nJumlah kontrak: ").append(queue.rows().size()).append("; berhasil: ").append(queue.completedCount() - queue.errorCount()).append("; error: ").append(queue.errorCount());
        out.append("\nDisiapkan: ").append(FlowSupport.timestamp(queue.createdAt()));
        out.append("\nMulai: ").append(FlowSupport.timestamp(queue.startedAt())).append("\nSelesai: ").append(FlowSupport.timestamp(queue.finishedAt()));
        if (queue.startMode() == BulkQueue.StartMode.READY_PAGE) {
            out.append("\nWaktu persiapan awal: ").append(queue.startedAt() > 0
                ? Math.max(0, queue.startedAt() - queue.createdAt()) + " ms" : "Belum menekan Mulai");
            out.append("\nPersiapan/menunggu Mulai tidak masuk waktu siklus aktif; total kalender tetap mencakup persiapan.");
        }
        out.append("\nWaktu siklus aktif tercatat: ").append(queue.activeMs()).append(" ms");
        if (queue.createdAt() > 0 && queue.finishedAt() > 0) {
            long total = Math.max(0, queue.finishedAt() - queue.createdAt());
            out.append("\nTotal kalender: ").append(total).append(" ms\nJeda/di luar siklus: ").append(Math.max(0, total - queue.activeMs())).append(" ms");
        }
        out.append("\nDurasi mencakup menunggu Tokopedia dan percobaan ulang dalam sesi. Durasi sebelum v0.5.0 tidak tersedia; waktu saat proses terputus mungkin belum tercatat.\n");
        List<Long> measured = new ArrayList<>(); long sum = 0; int recoveries = 0, index = 0;
        for (BulkQueue.Row row : queue.rows()) {
            recoveries += row.recoveries(); index++;
            if ((row.state() == BulkQueue.RowState.SUCCESS || row.state() == BulkQueue.RowState.ERROR) && row.durationMs() > 0) {
                measured.add(row.durationMs()); sum += row.durationMs();
            }
            out.append("\n--- Antrean ").append(index).append(" / ").append(queue.rows().size()).append(" ---\nStatus: ").append(row.state());
            out.append("\nDurasi aktif kumulatif: ").append(row.durationMs() > 0 ? row.durationMs() + " ms" : "Tidak tercatat");
            out.append("\nPemulihan form: ").append(row.recoveries()).append('\n');
            for (Map.Entry<String, Long> stage : row.stageMs().entrySet()) out.append("Tahap ").append(stage.getKey()).append(": ").append(stage.getValue()).append(" ms\n");
            String report = index <= reports.size() ? reports.get(index - 1) : "";
            out.append(report == null || report.isEmpty() ? "Diagnosis belum tersedia." : report).append('\n');
        }
        Collections.sort(measured);
        if (measured.isEmpty()) out.append("\nStatistik waktu: Tidak tersedia");
        else {
            int n = measured.size(); long median = n % 2 == 1 ? measured.get(n / 2) : measured.get(n / 2 - 1) + (measured.get(n / 2) - measured.get(n / 2 - 1)) / 2;
            out.append("\nJumlah kontrak selesai dengan durasi: ").append(n);
            out.append("\nRata-rata aktif: ").append(sum / n).append(" ms\nMedian aktif: ").append(median);
            out.append(" ms\nTercepat: ").append(measured.get(0)).append(" ms\nTerlama: ").append(measured.get(n - 1)).append(" ms");
        }
        out.append("\nTotal pemulihan: ").append(recoveries);
        return FlowSupport.redact(out.toString(), queue.rows());
    }
}
