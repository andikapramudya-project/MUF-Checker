package id.local.mufchecker;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Activity I/O runs serially off the UI thread; callbacks never outlive the Activity. */
final class AppRepository {
    interface Job<T> { T run(QueueStore store) throws Exception; }
    interface Success<T> { void accept(T value); }
    interface Failure { void accept(Exception error); }
    /** Counts and table key computed on the worker, so the UI thread never parses result JSON for a refresh. */
    static final class Summary {
        final int paid, unpaid;
        final String tableKey;
        Summary(int paid, int unpaid, String tableKey) { this.paid = paid; this.unpaid = unpaid; this.tableKey = tableKey; }
    }
    static final class Dashboard {
        final String revision;
        final BulkQueue current;
        final List<BulkQueue> sessions;
        final Map<BulkQueue, Summary> summaries;
        Dashboard(String revision, BulkQueue current, List<BulkQueue> sessions, Map<BulkQueue, Summary> summaries) {
            this.revision = revision; this.current = current; this.sessions = sessions; this.summaries = summaries;
        }
    }
    /** Parsed history keyed by session id; a snapshot is parsed again only when its stored text changes. Worker thread only. */
    private static final class Parsed {
        final String raw; final BulkQueue queue; final Summary summary;
        Parsed(String raw, BulkQueue queue, Summary summary) { this.raw = raw; this.queue = queue; this.summary = summary; }
    }
    private final Map<String, Parsed> parsed = new HashMap<>();
    /** Display status of one row; never implies payment for waiting, processing or unreadable results. */
    static String rowStatus(BulkQueue.Row row) {
        if (row.state() == BulkQueue.RowState.WAITING) return "MENUNGGU";
        if (row.state() == BulkQueue.RowState.PROCESSING) return "DIPROSES";
        if (row.state() == BulkQueue.RowState.ERROR) return "ERROR";
        try { return ResultFormatter.status(new JSONObject(row.result()).getString("status")); } catch (Exception e) { return "ERROR"; }
    }
    static Summary summarize(BulkQueue queue) {
        int paid = 0, unpaid = 0;
        for (BulkQueue.Row row : queue.rows()) {
            String status = rowStatus(row);
            if ("SUDAH BAYAR".equals(status)) paid++; else if ("BELUM BAYAR".equals(status)) unpaid++;
        }
        return new Summary(paid, unpaid, UiPresentation.tableKey(queue));
    }
    private List<BulkQueue> history(QueueStore database, Map<BulkQueue, Summary> summaries) {
        List<BulkQueue> list = new ArrayList<>(); Map<String, Parsed> next = new HashMap<>();
        for (String[] row : database.sessionSnapshots()) {
            Parsed hit = parsed.get(row[0]);
            if (hit == null || !hit.raw.equals(row[1])) { BulkQueue queue = BulkQueue.restore(row[1]); hit = new Parsed(row[1], queue, summarize(queue)); }
            next.put(row[0], hit); list.add(hit.queue); summaries.put(hit.queue, hit.summary);
        }
        parsed.clear(); parsed.putAll(next);
        return list;
    }
    private final Context context;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private QueueStore store;
    private volatile boolean closed;
    AppRepository(Context context) { this.context = context.getApplicationContext(); }
    <T> void submit(Job<T> job, Success<T> success, Failure failure) {
        if (closed) return;
        worker.execute(() -> {
            try {
                if (store == null) store = QueueStore.acquire(context);
                T value = job.run(store);
                ui.post(() -> { if (!closed) success.accept(value); });
            } catch (Exception error) { ui.post(() -> { if (!closed) failure.accept(error); }); }
        });
    }
    void load(String knownRevision, Success<Dashboard> success, Failure failure) {
        submit(database -> {
            String revision = database.read("revision");
            if (revision.equals(knownRevision)) return null;
            String raw = database.read("batch"); BulkQueue current = raw.isEmpty() ? null : BulkQueue.restore(raw);
            TokopediaService service = TokopediaService.instance;
            if ((service == null || !service.isRunning()) && current != null && current.cancelledPreparation()
                && database.cancelPreparation(current.sessionId(), true)) {
                current=null;revision=database.read("revision");
            }
            if ((service == null || !service.isRunning()) && current != null && current.recoverInterrupted()) {
                database.save(current, null, null, null, false); revision = database.read("revision");
            }
            Map<BulkQueue, Summary> summaries = new IdentityHashMap<>();
            return new Dashboard(revision, current, history(database, summaries), summaries);
        }, success, failure);
    }
    void close() {
        closed = true;
        worker.execute(() -> { if (store != null) QueueStore.release(store); });
        worker.shutdown();
    }
}
