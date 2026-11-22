package id.local.mufchecker;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Activity I/O runs serially off the UI thread; callbacks never outlive the Activity. */
final class AppRepository {
    interface Job<T> { T run(QueueStore store) throws Exception; }
    interface Success<T> { void accept(T value); }
    interface Failure { void accept(Exception error); }
    static final class Dashboard {
        final String revision;
        final BulkQueue current;
        final List<BulkQueue> sessions;
        Dashboard(String revision, BulkQueue current, List<BulkQueue> sessions) { this.revision = revision; this.current = current; this.sessions = sessions; }
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
            return new Dashboard(revision, current, database.sessions());
        }, success, failure);
    }
    void close() {
        closed = true;
        worker.execute(() -> { if (store != null) QueueStore.release(store); });
        worker.shutdown();
    }
}
