package id.local.mufchecker;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/** Session snapshots and per-row reports commit together before advancing. */
final class QueueStore extends SQLiteOpenHelper {
    private static QueueStore shared;
    private int clients;
    static synchronized QueueStore acquire(Context context) {
        if (shared == null) shared = new QueueStore(context);
        shared.clients++; return shared;
    }
    static synchronized void release(QueueStore store) {
        if (store != shared || store == null) return;
        if (--store.clients == 0) { store.close(); shared = null; }
    }
    private final SharedPreferences legacy;
    private QueueStore(Context context) {
        super(context.getApplicationContext(), "inquiries.db", null, 2);
        legacy = context.getSharedPreferences("checker", Context.MODE_PRIVATE);
        try { migrate(); } catch (RuntimeException error) { close(); throw error; }
    }
    private void createHistory(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sessions (id TEXT PRIMARY KEY, created INTEGER NOT NULL, snapshot TEXT NOT NULL)");
        db.execSQL("CREATE TABLE reports (session_id TEXT NOT NULL, contract TEXT NOT NULL, report TEXT NOT NULL, PRIMARY KEY(session_id,contract))");
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)"); createHistory(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion != 1 || newVersion != 2) throw new IllegalStateException("Versi penyimpanan tidak didukung");
        createHistory(db);
        String raw = read(db, "batch");
        if (!raw.isEmpty()) {
            BulkQueue q = BulkQueue.restore(raw); // Failure rolls back the entire migration.
            saveSnapshot(db, q);
            try (Cursor c = db.query("diagnostics", new String[]{"contract", "report"}, null, null, null, null, null)) {
                while (c.moveToNext()) diagnostic(db, q.sessionId(), c.getString(0), c.getString(1));
            }
        }
        db.execSQL("DROP TABLE diagnostics");
    }
    private void replace(SQLiteDatabase db, String table, ContentValues values) {
        if (db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE) < 0) throw new IllegalStateException("Data tidak tersimpan");
    }
    private void put(SQLiteDatabase db, String key, String value) {
        ContentValues v = new ContentValues(); v.put("key", key); v.put("value", value); replace(db, "meta", v);
    }
    private void diagnostic(SQLiteDatabase db, String id, String contract, String report) {
        ContentValues v = new ContentValues(); v.put("session_id", id); v.put("contract", contract); v.put("report", report); replace(db, "reports", v);
    }
    private String read(SQLiteDatabase db, String key) {
        try (Cursor c = db.query("meta", new String[]{"value"}, "key=?", new String[]{key}, null, null, null)) { return c.moveToFirst() ? c.getString(0) : ""; }
    }
    String read(String key) { return read(getReadableDatabase(), key); }
    BulkQueue session(String id) {
        try (Cursor c = getReadableDatabase().query("sessions", new String[]{"snapshot"}, "id=?", new String[]{id}, null, null, null)) {
            return c.moveToFirst() ? BulkQueue.restore(c.getString(0)) : null;
        }
    }
    List<BulkQueue> sessions() {
        List<BulkQueue> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("sessions", new String[]{"snapshot"}, null, null, null, null, "created DESC, rowid DESC")) {
            while (c.moveToNext()) list.add(BulkQueue.restore(c.getString(0)));
        }
        return list;
    }
    String rowDiagnostic(String id, String contract) {
        try (Cursor c = getReadableDatabase().query("reports", new String[]{"report"}, "session_id=? AND contract=?", new String[]{id, contract}, null, null, null)) {
            return c.moveToFirst() ? c.getString(0) : "";
        }
    }
    private void bump(SQLiteDatabase db) {
        String current = read(db, "revision"); put(db, "revision", Long.toString(current.isEmpty() ? 1 : Long.parseLong(current) + 1));
    }
    private void saveSnapshot(SQLiteDatabase db, BulkQueue q) {
        String raw = q.serialize(false);
        ContentValues v = new ContentValues(); v.put("id", q.sessionId()); v.put("created", q.createdAt()); v.put("snapshot", raw); replace(db, "sessions", v); put(db, "batch", raw);
    }
    boolean save(BulkQueue queue, String result, String contract, String report, boolean reset) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            if (reset) db.delete("meta", "key=?", new String[]{"result"});
            saveSnapshot(db, queue);
            if (result != null) put(db, "result", result);
            if (contract != null && report != null && !report.isEmpty()) diagnostic(db, queue.sessionId(), contract, report);
            bump(db); db.setTransactionSuccessful(); return true;
        } finally { db.endTransaction(); }
    }
    /** Retain cancelled history but atomically release the current draft. Never release attempted work. */
    boolean cancelPreparation(String id, boolean cancelledOnly) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            String raw=read(db,"batch");if(raw.isEmpty())return false;
            BulkQueue draft=BulkQueue.restore(raw);
            if(!draft.sessionId().equals(id) || !draft.unstartedPreparation())return false;
            TokopediaService live=TokopediaService.instance;
            if(cancelledOnly){
                if(!draft.cancelledPreparation() || live!=null && live.isRunning())return false;
            } else if(draft.state()!=BulkQueue.RunState.PREPARED)return false;
            draft.stop("Persiapan dibatalkan. Daftar kontrak dapat diubah.","");
            saveSnapshot(db,draft);
            db.delete("meta","key=?",new String[]{"batch"});bump(db);
            db.setTransactionSuccessful();return true;
        } finally { db.endTransaction(); }
    }
    void deleteSession(String id) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            String raw = read(db, "batch");
            if (!raw.isEmpty()) {
                BulkQueue q = BulkQueue.restore(raw);
                if (q.sessionId().equals(id)) {
                    TokopediaService service = TokopediaService.instance;
                    if (q.state() == BulkQueue.RunState.RUNNING || q.state() == BulkQueue.RunState.PREPARED
                        && service != null && service.isRunning()) throw new IllegalStateException("Sesi aktif/disiapkan tidak dapat dihapus");
                    db.delete("meta", "key IN ('batch','result')", null);
                }
            }
            db.delete("reports", "session_id=?", new String[]{id}); db.delete("sessions", "id=?", new String[]{id}); bump(db); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    void clear() {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            db.delete("reports", null, null); db.delete("sessions", null, null); db.delete("meta", "key!=?", new String[]{"revision"});
            put(db, "migrated", "1"); bump(db); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    String exportDiagnosis(BulkQueue q) {
        List<String> reports = new ArrayList<>();
        for (BulkQueue.Row row : q.rows()) reports.add(rowDiagnostic(q.sessionId(), row.contract));
        return SessionReport.render(q, reports);
    }
    private void migrate() {
        if (read("migrated").equals("1")) return;
        String raw = legacy.getString("batch", ""); BulkQueue q = raw.isEmpty() ? null : BulkQueue.restore(raw);
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            if (q != null) {
                saveSnapshot(db, q);
                for (BulkQueue.Row r : q.rows()) if (!r.diagnostic().isEmpty()) diagnostic(db, q.sessionId(), r.contract, r.diagnostic());
            }
            String result = legacy.getString("result", ""); if (!result.isEmpty()) put(db, "result", result);
            put(db, "migrated", "1"); bump(db); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        legacy.edit().remove("batch").remove("result").apply();
    }
}
