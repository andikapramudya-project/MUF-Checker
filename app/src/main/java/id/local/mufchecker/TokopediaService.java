package id.local.mufchecker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TokopediaService extends AccessibilityService {
    public static final String TOKOPEDIA = "com.tokopedia.tkpd";
    public static final String MUF_URL = "https://www.tokopedia.com/angsuran/mandiri-utama-finance";
    public static volatile TokopediaService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private InquirySession session;
    private DiagnosticTrace trace;
    private BulkQueue batch;
    private QueueStore store;
    private boolean checkerForeground;
    private int consecutiveErrors;
    private String contract;
    private YearMonth period;
    private volatile boolean active, preparing;
    private final ReadyPageGate readyGate = new ReadyPageGate();
    private String preparationSnapshot = "";
    private boolean keyboardDismissed;
    private long nextAction;
    private long lastInspectAt, submittedAt = -1;
    private final ResultStability resultStability = new ResultStability();
    private final InputVerification inputVerification = new InputVerification();
    private final InquiryResponse response = new InquiryResponse();
    private boolean inputFocused, eventQueued, screenHeld;
    private ScreenAwake screenAwake;
    private String returnShape = "";
    private String tokopediaVersion = "tidak diketahui", lastProgress = "", lastCompact = "";
    private String lastSavedStage = "", lastSavedReport = "";
    private long lastTraceSave, lastQueueCheckpoint;
    private DiagnosticTrace savedTrace;
    private String waitingFor = "Membuka Tokopedia", lastAction = "Belum ada", rootStatus = "Belum dibaca";
    private String lastTapLocation = "belum ada";
    private LinearLayout panel;
    private TextView panelText;
    private Button pauseButton;
    private boolean receiverRegistered;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!isRunning()) return;
            try { if (preparing) inspectPrepared(false); else inspect(); }
            catch (Exception e) {
                if (preparing) pauseForReason("Pembacaan form persiapan terhenti. Siapkan ulang panel dari MUF Checker.");
                else {
                    trace.event(session.stage().name(), "Galat pembaca: " + e.getClass().getSimpleName());
                    finish("Pembacaan layar terhenti. Kembali ke MUF Checker untuk melihat diagnosis.");
                }
            }
            if (isRunning()) {
                long now = SystemClock.elapsedRealtime();
                long stableDeadline = preparing ? -1 : resultStability.deadline();
                long quietDeadline=preparing?-1:response.deadline();
                if(quietDeadline>=0)stableDeadline=stableDeadline<0?quietDeadline:Math.min(stableDeadline,quietDeadline);
                handler.removeCallbacks(this);
                handler.postDelayed(this, FlowTiming.nextInspection(readyPageMode() && !preparing, now, lastInspectAt, nextAction, stableDeadline));
            }
        }
    };
    private final Runnable eventTick = () -> {
        eventQueued = false;
        if (!isRunning()) return;
        handler.removeCallbacks(tick);
        tick.run();
    };
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { pauseForReason("Layar terkunci/padam. Antrean dijeda; lanjutkan dari MUF Checker."); }
    };

    @Override protected void onServiceConnected() {
        try { if (store == null) store = QueueStore.acquire(this); }
        catch (RuntimeException e) {
            instance = null;
            android.widget.Toast.makeText(this, "Penyimpanan belum dapat dibuka. Data lama dipertahankan; buka kembali MUF Checker.", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        instance = this;
        getSharedPreferences("checker", MODE_PRIVATE).edit().putBoolean("active", false).putBoolean("preparing", false).apply();
        try {
            String raw = store.read("batch");
            if (!raw.isEmpty()) {
                BulkQueue saved = BulkQueue.restore(raw);
                if (saved.recoverInterrupted()) store.save(saved, null, null, null, false);
            }
        } catch (RuntimeException ignored) { }
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isRunning() || event == null) return;
        if (!TOKOPEDIA.contentEquals(event.getPackageName() == null ? "" : event.getPackageName())) {
            if (preparing && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && !getPackageName().contentEquals(event.getPackageName() == null ? "" : event.getPackageName())) {
                readyGate.observe(false, SystemClock.elapsedRealtime());
                setPanelEnabled(false);
            }
            return;
        }
        if(session!=null && session.stage()==InquirySession.Stage.WAIT_RESULT && InquiryResponse.accepted(session.tapState())){
            StringBuilder message = new StringBuilder();
            for (CharSequence value : event.getText()) message.append(value).append(' ');
            if(event.getContentDescription()!=null)message.append(event.getContentDescription());
            response.observeEvent(true,active,session,submittedAt,event.getEventTime(),SystemClock.uptimeMillis(),message.toString());
        }
        if (!eventQueued) { eventQueued = true; handler.postDelayed(eventTick, 250); }
    }
    @Override public void onInterrupt() { pauseForReason("Aksesibilitas terinterupsi. Antrean dijeda."); }
    @Override public void onDestroy() {
        pauseForReason("Layanan berhenti. Antrean disimpan; aktifkan aksesibilitas untuk melanjutkan.");
        instance = null;
        if (store != null) { QueueStore.release(store); store = null; }
        super.onDestroy();
    }
    /** Includes the armed panel so editing/deleting cannot race a floating Start. */
    public boolean isRunning() { return active || preparing; }
    public boolean isPreparing() { return preparing; }
    private boolean readyPageMode() { return batch != null && batch.startMode() == BulkQueue.StartMode.READY_PAGE; }
    public void begin(String number, YearMonth selectedPeriod) {
        beginBatch(java.util.Collections.singletonList(number), selectedPeriod);
    }
    public void beginBatch(List<String> numbers, YearMonth selectedPeriod) {
        beginBatch(numbers, selectedPeriod, BulkQueue.StartMode.DIRECT_LINK);
    }
    public void beginBatch(List<String> numbers, YearMonth selectedPeriod, BulkQueue.StartMode mode) {
        if (isRunning()) return;
        String previous = store.read("batch");
        if (!previous.isEmpty() && BulkQueue.restore(previous).hasUnfinished()) return;
        batch = new BulkQueue(numbers, selectedPeriod, mode);
        if (readyPageMode()) batch.prepareReadyPage();
        if (!store.save(batch, null, null, null, true)) throw new IllegalStateException("Antrean belum tersimpan");
        getSharedPreferences("checker", MODE_PRIVATE).edit().remove("diagnostic").apply();
        if (readyPageMode()) activatePreparation(); else activateBatch();
    }
    public boolean resumeBatch() {
        if (isRunning()) return false;
        try {
            batch = BulkQueue.restore(store.read("batch"));
            batch.recoverInterrupted();
            if (readyPageMode()) {
                if (!batch.prepareReadyPage() || !store.save(batch, null, null, null, false)) return false;
                activatePreparation(); return preparing;
            }
            if (!batch.resume()) return false;
            activateBatch(); return true;
        } catch (RuntimeException e) { return false; }
    }
    private void registerScreenReceiver() {
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenOff, filter);
        receiverRegistered = true;
    }
    private void activatePreparation() {
        active = false; preparing = true; session = null; trace = new DiagnosticTrace(true); contract = null;
        preparationSnapshot = "";
        trace.event("PREPARE_FORM", "Antrean tersimpan; menunggu form dan tombol Mulai pengguna. Belum ada aksi pada Tokopedia.");
        period = batch.period; readyGate.reset(); lastInspectAt = 0; lastProgress = "";
        try {
            tokopediaVersion = getPackageManager().getPackageInfo(TOKOPEDIA, 0).versionName;
            showPanel(); registerScreenReceiver();
            getSharedPreferences("checker", MODE_PRIVATE).edit().putBoolean("active", false).putBoolean("preparing", true).apply();
            progress("Antrean disiapkan. Kembali melalui aplikasi terbaru ke Tokopedia > Top-up & Tagihan > Angsuran Kredit, pilih MUF, lalu tekan Mulai pada panel.");
            handler.removeCallbacks(tick); handler.post(tick);
        } catch (Exception e) { pauseForReason("Panel Mulai belum dapat disiapkan. Periksa Tokopedia dan aksesibilitas, lalu siapkan ulang."); }
    }
    private static boolean checkout(Snapshot s, Map<String, String> fields) {
        return s.has("checkout") || s.has("pilih pembayaran") || s.has("detail tagihan")
            || fields.containsKey("jatuh tempo") || fields.containsKey("nomor pelanggan");
    }
    private static boolean manualVerification(Snapshot s) {
        return s.hasAny("masuk ke tokopedia", "login", "log in", "verifikasi otp", "kode otp", "captcha", "masukkan pin", "kata sandi");
    }
    /** Preparation is read-only. Even a previously enabled Start rechecks the actual foreground form. */
    private void inspectPrepared(boolean userStart) {
        if (!preparing) return;
        long now = SystemClock.elapsedRealtime();
        if (!userStart && now - lastInspectAt < 250) return;
        lastInspectAt = now;
        AccessibilityNodeInfo root = tokopediaRoot();
        if (panel != null) panel.setVisibility(root == null ? View.GONE : View.VISIBLE);
        if (root == null) {
            readyGate.observe(false, now);
            setPanelEnabled(false);
            recordPreparation("Sumber layar: " + rootStatus + "\nForm siap: false");
            progress("Antrean siap. Buka form Angsuran Kredit di Tokopedia dan pilih Mandiri Utama Finance. Mulai ditekan pada panel.");
            return;
        }
        boolean start = false;
        try {
            Snapshot s = new Snapshot(root, getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
            try {
                Map<String, String> fields = s.fields();
                AccessibilityNodeInfo field = s.contractField();
                boolean ready = ReadyPageGate.formReady(true, s.has("angsuran kredit"), s.selectedMuf(),
                    field != null && field.isEnabled() && Snapshot.supports(field, AccessibilityNodeInfo.ACTION_SET_TEXT),
                    checkout(s, fields), manualVerification(s), s.providerListVisible()) && gestureSupported();
                now = SystemClock.elapsedRealtime();
                readyGate.observe(ready, now);
                boolean enabled = readyGate.canStart(now);
                recordPreparation("Sumber layar: " + rootStatus + "\nJudul Angsuran Kredit: " + s.has("angsuran kredit")
                    + "\nPenyedia MUF: " + s.selectedMuf() + "\nKolom kontrak: " + s.contractCapabilities()
                    + "\nRincian terbuka: " + checkout(s, fields) + "\nVerifikasi manual: " + manualVerification(s)
                    + "\nDaftar penyedia terbuka: " + s.providerListVisible() + "\nGestur tersedia: " + gestureSupported()
                    + "\nNode terbaca: " + s.nodes.size() + "\nDurasi pembacaan layar (ms): " + s.captureMs
                    + "\nForm siap: " + ready + "\nTombol Mulai siap: " + enabled);
                setPanelEnabled(enabled);
                progress(enabled ? "Form MUF siap · " + batch.rows().size() + " kontrak · Tekan Mulai"
                    : "Menunggu form Angsuran Kredit MUF yang dapat diisi. Pilih MUF, tutup keyboard/daftar penyedia, dan selesaikan login jika diperlukan.");
                start = userStart && readyGate.consume(now);
            } finally { s.close(); }
        } finally { root.recycle(); }
        if (start) {
            if (!batch.startPrepared()) return;
            preparing = false; active = true; consecutiveErrors = 0;
            screenAwake = new ScreenAwake(this);
            try {
                if (!persistBatch(null, null)) { pauseForReason("Antrean belum dapat disimpan. Inquiry belum dimulai."); return; }
                startNextItem(false);
                if (!active) return;
                showPanel(); handler.removeCallbacks(tick); handler.post(tick);
            } catch (RuntimeException e) { pauseForReason("Pemeriksaan belum dapat dimulai. Siapkan ulang form dan panel."); }
        }
    }
    private void recordPreparation(String report) {
        preparationSnapshot = "MUF Checker 0.8.6\nTahap: PREPARE_FORM\n" + report;
        trace.record("PREPARE_FORM", preparationSnapshot); persistTrace();
    }
    private void requestPreparedStart() {
        try { inspectPrepared(true); }
        catch (RuntimeException e) { pauseForReason("Form belum dapat diverifikasi ulang. Siapkan ulang panel dari MUF Checker."); }
    }
    private void activateBatch() {
        active = true; preparing = false; consecutiveErrors = 0;
        try { tokopediaVersion = getPackageManager().getPackageInfo(TOKOPEDIA, 0).versionName; } catch (Exception ignored) { }
        screenAwake = new ScreenAwake(this);
        try {
            startNextItem(false);
            if (!active) return;
            showPanel();
            registerScreenReceiver();
            handler.postDelayed(tick, 1200);
        } catch (Exception e) {
            pauseForReason("Tokopedia atau panel pemeriksaan belum dapat dibuka. Antrean dijeda; periksa izin dan lanjutkan.");
        }
    }
    private void startNextItem(boolean returnFromResult) {
        BulkQueue.Row row = batch.startNext();
        if (row == null) { stopFlow(batchSummary()); return; }
        contract = row.contract; period = batch.period;
        session = new InquirySession(SystemClock.elapsedRealtime(), !readyPageMode());
        session.move(returnFromResult ? InquirySession.Stage.RETURN_FORM
            : readyPageMode() ? InquirySession.Stage.FIND_FORM : InquirySession.Stage.OPEN_LINK, SystemClock.elapsedRealtime());
        trace = new DiagnosticTrace(readyPageMode());
        if (readyPageMode() && !preparationSnapshot.isEmpty()) {
            trace.record("PREPARE_FORM", preparationSnapshot); preparationSnapshot = "";
        }
        nextAction = SystemClock.elapsedRealtime() + FlowTiming.nextItem(readyPageMode(), returnFromResult);
        submittedAt = -1;
        inputFocused = false; response.reset(); returnShape = "";
        inputVerification.clear();
        keyboardDismissed = false;
        lastTapLocation = "belum ada";
        waitingFor = returnFromResult ? "Kembali ke form MUF untuk kontrak berikutnya"
            : readyPageMode() ? "Memeriksa form MUF yang sudah dibuka; tautan tidak dibuka" : "Membuka tautan MUF langsung di Tokopedia";
        lastAction = waitingFor;
        trace.event(session.stage().name(), lastAction);
        resultStability.reset();
        if (!persistBatch(null, null)) { pauseForReason("Antrean belum dapat disimpan. Tidak memulai inquiry."); return; }
        trace.record("LAUNCH", diagnosticHeader() + "\nAksi terakhir: " + lastAction);
        persistTrace(); progress(waitingFor);
        if (!returnFromResult && !readyPageMode()) openTokopedia(this);
    }
    public static void openTokopedia(Context context) {
        Intent link = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(MUF_URL));
        link.setPackage(TOKOPEDIA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Explicit package keeps the inquiry in Tokopedia. The contract is never placed in the URL.
        context.startActivity(link);
    }
    public void stopByUser() {
        if (preparing) {
            if(batch.unstartedPreparation()){
                String note="Persiapan dibatalkan. Daftar kontrak dapat diubah.";
                try {
                    if(!store.cancelPreparation(batch.sessionId(),false)){
                        pauseForReason("Pembatalan belum tersimpan. Antrean tetap dilindungi; coba batalkan kembali.");return;
                    }
                    batch.stop(note,"");stopFlow(note);batch=null;
                }catch(RuntimeException error){pauseForReason("Pembatalan belum tersimpan. Antrean tetap dilindungi; siapkan ulang lalu batalkan kembali.");}
                return;
            }
            pauseForReason("Persiapan dibatalkan. Antrean tersimpan; siapkan ulang untuk menampilkan tombol Mulai.");return;
        }
        if (!active) return;
        String note = "Antrean dihentikan. Hasil yang selesai tetap tersimpan; kontrak yang belum selesai tetap menunggu.";
        closeTrace(note); batch.stop(note, trace.render()); persistBatch(null, trace.render()); stopFlow(note);
    }
    public void requestPause() {
        if (preparing) { pauseForReason("Persiapan dijeda. Siapkan ulang panel untuk melanjutkan."); return; }
        if (!active) return;
        if (!session.submitted() && session.inputAttempts() == 0) { pauseForReason("Antrean dijeda sebelum inquiry berikutnya."); return; }
        batch.requestPause(); persistBatch(null, null);
        progress("Jeda diminta; menyelesaikan kontrak aktif terlebih dahulu.");
        setPanelEnabled(false);
    }
    private void inspect() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastInspectAt < 250) return;
        lastInspectAt = now;
        if (session.expired(now)) {
            if (readyPageMode() && !session.submitted()) {
                pauseForReason("Form MUF belum siap dalam batas waktu. Antrean dijeda; buka kembali halaman MUF dan siapkan ulang panel."); return;
            }
            finish("Batas waktu tahap " + session.stage() + ": " + waitingFor + ". Belum ada kesimpulan; salin diagnosis teknis di MUF Checker.");
            return;
        }
        AccessibilityNodeInfo root = tokopediaRoot();
        if (readyPageMode() && !preparing && panel != null) panel.setVisibility(root == null ? View.GONE : View.VISIBLE);
        if (now - lastQueueCheckpoint >= 5000 && !persistBatch(null, null)) {
            if (root != null) root.recycle();
            pauseForReason("Checkpoint antrean gagal. Pemeriksaan dijeda sebelum tindakan berikutnya."); return;
        }
        boolean keepScreen = root != null && getSharedPreferences("checker", MODE_PRIVATE).getBoolean("keep_screen", true);
        screenHeld = screenAwake != null && screenAwake.maintain(keepScreen);
        if (panel != null) {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) panel.getLayoutParams();
            int desired = keepScreen ? params.flags | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON : params.flags & ~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            if (params.flags != desired) { params.flags = desired; ((WindowManager) getSystemService(WINDOW_SERVICE)).updateViewLayout(panel, params); }
        }
        if (root == null) {
            response.resetQuiet();
            resultStability.reset();
            InquirySession.ReturnAction action = session.returnAction(false, false, checkerForeground, now);
            if (action == InquirySession.ReturnAction.PAUSE) {
                pauseForReason("Form MUF belum kembali. Buka form dari menu Tokopedia dan siapkan ulang panel; tautan lama tidak dibuka."); return;
            }
            if (action == InquirySession.ReturnAction.OPEN_LINK) {
                reopenForm(now); return;
            }
            recordDiagnostic(diagnosticHeader() + "\nSumber layar: " + rootStatus + "\nAksi terakhir: " + lastAction);
            waitFor("Layar Tokopedia belum terbaca. Tampilkan Tokopedia untuk melanjutkan"); return;
        }
        try {
            if (SystemClock.elapsedRealtime() < nextAction) return;
            if (verifyFocusedInput(root)) return;
            Snapshot s = new Snapshot(root, getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
            try {
                Map<String, String> fields = s.fields();
                boolean checkout = checkout(s, fields);
                boolean provider = s.selectedMuf();
                AccessibilityNodeInfo field = s.contractField();
                boolean inputPage = field != null;
                diagnose(s, fields, provider, inputPage, checkout);
                if (manualVerification(s)) {
                    pauseForReason("Selesaikan login/verifikasi di Tokopedia, lalu tekan Lanjutkan antrean di MUF Checker.");
                    return;
                }
                if(session.stage()!=InquirySession.Stage.WAIT_RESULT)response.resetQuiet();
                if (readyPageMode() && !session.submitted() && !checkout && inputPage
                    && s.providerIndex() >= 0 && !provider) {
                    pauseForReason("Penyedia pada form berubah. Pilih Mandiri Utama Finance, lalu siapkan ulang panel Mulai."); return;
                }
                if (session.stage() == InquirySession.Stage.RETURN_FORM && !(provider && inputPage && !checkout)) {
                    String shape = s.hasContractLabel() + ":" + s.hasProviderLabel() + ":" + provider + ":" + inputPage;
                    long observedAt = SystemClock.elapsedRealtime();
                    if (!checkout && !shape.equals(returnShape)) { returnShape = shape; session.returnProgress(observedAt); }
                    InquirySession.ReturnAction action = session.returnAction(false, true, false, observedAt);
                    if (action == InquirySession.ReturnAction.BACK) {
                        if (performGlobalAction(GLOBAL_ACTION_BACK)) {
                            acted("Kembali satu kali ke form MUF setelah hasil sebelumnya disimpan", 450); return;
                        }
                        reopenForm(now); return;
                    }
                    if (action == InquirySession.ReturnAction.OPEN_LINK) { reopenForm(now); return; }
                    if (action == InquirySession.ReturnAction.PAUSE) {
                        pauseForReason("Form MUF belum kembali setelah satu Kembali. Antrean dijeda; buka form dari menu Tokopedia dan siapkan ulang panel."); return;
                    }
                    waitFor("Menunggu form sebelumnya. Hasil lama tidak digunakan untuk kontrak berikutnya"); return;
                }
                if (session.submitted()) {
                    String actual=field==null || field.getText()==null?"":field.getText().toString().trim();
                    boolean exactForm=provider && inputPage && contract.equals(actual) && !checkout;
                    if(!checkout && !session.resultObserved()){
                        InquiryResponse.Signal signal=s.responseSignal();
                        if(exactForm)signal=InquiryResponse.prefer(signal,response.pendingEvent(SystemClock.uptimeMillis()));
                        boolean quietExpired=response.observeQuiet(SystemClock.elapsedRealtime(),session,exactForm,s.loading(),checkout);
                        if(handleInquiryResponse(signal,quietExpired))return;
                    }else response.resetQuiet();
                    if (!checkout) {
                        resultStability.reset();
                        waitFor(provider && inputPage ? "Ketukan Bayar sudah dicoba, tetapi form kontrak masih terbuka. Menunggu perubahan halaman; tidak mengulang ketukan"
                            : "Menunggu halaman rincian Tokopedia setelah satu ketukan Bayar"); return;
                    }
                    if (!session.markResultObserved()) {
                        waitFor("Halaman berubah, tetapi ketukan inquiry belum tercatat diterima Android"); return;
                    }
                    if (!fields.containsKey("jatuh tempo")) {
                        resultStability.reset();
                        AccessibilityNodeInfo details = s.detailsButton();
                        if (details != null && !session.detailsOpened()) {
                            if (!session.markDetailsOpened(SystemClock.elapsedRealtime()) || !click(details)) {
                                finish("Lihat Detail ditemukan tetapi tidak dapat dibuka melalui aksesibilitas. Salin diagnosis teknis."); return;
                            }
                            acted("Membuka Lihat Detail", FlowTiming.details(readyPageMode()));
                            return;
                        }
                        if (s.has("tutup detail")) session.move(InquirySession.Stage.READ_DETAILS, now);
                        if (!FlowTiming.maySearchDetails(readyPageMode(), session.detailsOpened(), session.stageAge(SystemClock.elapsedRealtime()))) {
                            waitFor("Menunggu rincian selesai dimuat sebelum mencari Jatuh Tempo"); return;
                        }
                        if (scroll(s, "Mencari Jatuh Tempo / Lihat Detail pada rincian", session.scrolls() < 3)) return;
                        waitFor(session.detailsOpened() ? "Rincian dibuka, tetapi label Jatuh Tempo belum terbaca"
                            : "Tombol Lihat Detail atau label Jatuh Tempo belum terbaca");
                        return;
                    }
                    String verifiedResult;
                    try {
                        if(!FlowTiming.mayVerifyDetails(readyPageMode(),session.detailsOpened(),session.stageAge(SystemClock.elapsedRealtime()),BillReader.earlyDetailsComplete(fields))){
                            resultStability.reset();waitFor("Menunggu nama dan tagihan selesai dimuat pada rincian");return;
                        }
                        LocalDate due = BillReader.verifiedDue(fields, contract);
                        long readAt = SystemClock.elapsedRealtime();
                        if (!resultStability.observe(fields.toString(),readAt)) { progress("Mencocokkan kontrak dan menunggu rincian stabil…"); return; }
                        verifiedResult = buildResult(fields, due);
                    } catch (Exception e) {
                        resultStability.reset();
                        waitFor("Rincian belum terverifikasi: " + e.getMessage());
                        return;
                    }
                    completeCurrent(verifiedResult);
                    return;
                }
                if (checkout) {
                    if (session.stage() == InquirySession.Stage.OPEN_LINK) {
                        waitFor("Menunggu tautan MUF membuka form baru; rincian yang sudah ada belum digunakan"); return;
                    }
                    finish("Rincian/checkout sudah terbuka sebelum inquiry baru. Kembali ke form nomor kontrak MUF, lalu mulai ulang agar hasil lama tidak terbaca.");
                    return;
                }
                if (session.stage() != InquirySession.Stage.VERIFY_CONTRACT && (!readyPageMode() || !inputFocused)
                    && s.hasContractLabel() && keyboardVisible()) {
                    if (!keyboardDismissed) {
                        keyboardDismissed = true;
                        performGlobalAction(GLOBAL_ACTION_BACK);
                        acted("Menutup keyboard untuk membaca penyedia dan form MUF", FlowTiming.keyboard(readyPageMode()));
                    } else waitFor("Menunggu keyboard tertutup agar form dari tautan dapat diverifikasi");
                    return;
                }
                if (session.stage() == InquirySession.Stage.VERIFY_CONTRACT) {
                    // Input focus can move the provider row outside the viewport. Close the IME before re-reading the form.
                    UiRules.Verification verification = UiRules.verification(keyboardVisible(), provider, inputPage);
                    if (verification == UiRules.Verification.CLOSE_KEYBOARD) {
                        if (!keyboardDismissed) {
                            keyboardDismissed = true;
                            performGlobalAction(GLOBAL_ACTION_BACK);
                            acted("Menutup keyboard sebelum inquiry", FlowTiming.keyboard(readyPageMode()));
                        } else waitFor("Keyboard masih terbuka; tombol inquiry belum dapat diverifikasi");
                        return;
                    }
                    if (verification == UiRules.Verification.WAIT_FORM) {
                        waitFor("Keyboard ditutup. Menunggu penyedia MUF dan kolom kontrak kembali terbaca"); return;
                    }
                    String actual = field.getText() == null ? "" : field.getText().toString();
                    if (!InquiryRules.maySubmit(provider, inputPage, checkout, contract, actual, session.submitted())) {
                        if (actual.isEmpty() && session.stageAge(now) < 2000) {
                            waitFor("Menunggu hasil pengisian nomor tampil pada kolom kontrak"); return;
                        }
                        if (session.retryEmptyInput(actual, now)) {
                            acted("Kolom masih kosong; mengulang pengisian kontrak satu kali", 700); return;
                        }
                        finish("Nomor pada form belum sama persis dengan kontrak yang diminta. Inquiry dibatalkan."); return;
                    }
                    if (s.inquiryMatch.gestureTarget < 0) {
                        waitFor("Nomor cocok, tetapi target Bayar belum unik/tersedia. " + s.inquiryMatch.summary()); return;
                    }
                    if (!gestureSupported()) {
                        finish("Label Bayar dikenali. Nonaktifkan lalu aktifkan kembali Aksesibilitas MUF Checker untuk mengaktifkan ketukan otomatis, lalu mulai ulang."); return;
                    }
                    if (!session.markSubmitted(provider, inputPage, checkout, contract, actual, now)) {
                        finish("Pemeriksaan sebelum inquiry gagal. Tidak ada klik Bayar tambahan."); return;
                    }
                    submittedAt = SystemClock.uptimeMillis(); response.reset();
                    // WebView can acknowledge ACTION_CLICK without activating its HTML button.
                    // Use one physical tap from the start, even when an accessibility click is advertised.
                    tapInquiryLabel(s.menuTree.get(s.inquiryMatch.gestureTarget));
                    return;
                }
                if (provider && inputPage) {
                    session.move(InquirySession.Stage.FILL_CONTRACT, now);
                    diagnose(s, fields, provider, inputPage, checkout);
                    if (!inputFocused) {
                        inputFocused = true;
                        if (FlowTiming.needsFocus(readyPageMode(), field.isFocused())) {
                            field.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                            acted("Mengaktifkan kolom; menunggu fokus stabil sebelum pengisian", 400); return;
                        }
                        trace.event(session.stage().name(), "Kolom kontrak sudah fokus pada form MUF terverifikasi; langsung mengisi");
                    }
                    if (!session.markInputAttempt()) {
                        finish("Pengisian kontrak sudah dicoba dua kali dan belum berhasil diverifikasi. Salin diagnosis teknis."); return;
                    }
                    keyboardDismissed = false;
                    inputVerification.clear();
                    if (!setText(field, contract)) {
                        if (session.inputAttempts() < 2 && click(field)) {
                            acted("Mengaktifkan kolom kontrak sebelum mencoba pengisian ulang", 900); return;
                        }
                        finish("Kolom kontrak ditemukan tetapi pengisian teks ditolak Tokopedia. Salin diagnosis teknis.");
                        return;
                    }
                    long acceptedAt = SystemClock.elapsedRealtime();
                    boolean probe = false;
                    if (readyPageMode()) {
                        try { probe = inputVerification.arm(readInputField(field, contract), acceptedAt); }
                        catch (RuntimeException ignored) { inputVerification.clear(); }
                    }
                    session.move(InquirySession.Stage.VERIFY_CONTRACT, now);
                    acted("Aksi pengisian diterima Android. Memeriksa nomor yang benar-benar tampil dan penyedia MUF",
                        probe ? InputVerification.PROBE_MS : InputVerification.FULL_READ_MS);
                    return;
                }
                if (s.hasContractLabel() || s.hasProviderLabel()) {
                    if (!provider && s.providerIndex() < 0 && s.hasContractLabel() && session.scrolls() < 2
                        && scroll(s, "Menampilkan baris penyedia MUF di atas form kontrak", false)) return;
                    waitFor(provider ? "Penyedia MUF terbaca, tetapi kolom kontrak belum dapat dikenali secara unik"
                        : "Form terbuka, tetapi penyedia Mandiri Utama Finance belum terverifikasi"); return;
                }
                waitFor(readyPageMode() ? "Menunggu form Mandiri Utama Finance yang dibuka dari menu Tokopedia"
                    : "Menunggu form Mandiri Utama Finance dari tautan langsung di Tokopedia");
            } finally { s.close(); }
        } finally { root.recycle(); }
    }
    /** A fresh focused-field read can close only the IME. All inquiry guards run on a new full snapshot. */
    private boolean verifyFocusedInput(AccessibilityNodeInfo root) {
        if (!inputVerification.eligible(readyPageMode(), session.stage(), session.submitted(),
                session.inputAttempts(), keyboardDismissed, contract)) return false;
        long started = SystemClock.elapsedRealtime();
        InputVerification.Action action;
        AccessibilityNodeInfo focused = null;
        try {
            boolean keyboard = keyboardVisible();
            InputVerification.Field fresh = null;
            if (keyboard) focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused != null && focused.refresh()) fresh = readInputField(focused, null);
            action = inputVerification.decide(SystemClock.elapsedRealtime(), root.getWindowId(), keyboard, fresh);
        } catch (RuntimeException e) {
            long fullReadAt = inputVerification.fullReadAt();
            inputVerification.clear();
            // Failure must still honor the original input settling window before a full read.
            nextAction = Math.max(SystemClock.elapsedRealtime(), fullReadAt);
            trace.event(session.stage().name(), "Pembacaan kolom ringan gagal; kembali ke verifikasi lengkap");
            return true;
        } finally { if (focused != null && focused != root) focused.recycle(); }
        trace.record("VERIFY_INPUT_LIGHT", diagnosticHeader()
            + "\nJenis pembacaan: kolom fokus baru, tanpa pemindaian seluruh layar"
            + "\nDurasi pembacaan kolom (ms): " + (SystemClock.elapsedRealtime() - started)
            + "\nKeputusan: " + action + "\nInquiry dari pembacaan ringan: false");
        if (action == InputVerification.Action.WAIT) {
            nextAction = inputVerification.fullReadAt();
            waitFor("Menunggu pengisian stabil sebelum pemeriksaan lengkap"); return true;
        }
        inputVerification.clear();
        if (action == InputVerification.Action.FULL_READ) return false;
        if (!performGlobalAction(GLOBAL_ACTION_BACK)) {
            pauseForReason("Keyboard belum dapat ditutup. Antrean dijeda; tampilkan form MUF lalu siapkan ulang panel."); return true;
        }
        keyboardDismissed = true;
        acted("Nomor pada kolom fokus cocok; menutup keyboard lalu memeriksa ulang seluruh form MUF",
            FlowTiming.keyboard(true));
        return true;
    }
    private static InputVerification.Field readInputField(AccessibilityNodeInfo field, String expected) {
        Rect bounds = new Rect(); field.getBoundsInScreen(bounds);
        String className = Snapshot.string(field.getClassName());
        CharSequence actual = expected == null ? field.getText() : null;
        return new InputVerification.Field(expected == null ? (actual == null ? "" : actual.toString()) : expected,
            Snapshot.string(field.getPackageName()), field.getViewIdResourceName(), className,
            field.getWindowId(), bounds.left, bounds.top, bounds.right, bounds.bottom,
            field.isVisibleToUser(), field.isEnabled(), field.isFocused(),
            UiRules.writable(field.isEnabled(), field.isEditable(), Snapshot.supports(field, AccessibilityNodeInfo.ACTION_SET_TEXT), className));
    }
    private void reopenForm(long now) {
        if (readyPageMode()) {
            pauseForReason("Form MUF tidak dapat dipulihkan dengan Kembali. Antrean dijeda; buka ulang form dari menu Tokopedia."); return;
        }
        batch.recoveredForm();
        session.move(InquirySession.Stage.OPEN_LINK, now);
        openTokopedia(this);
        acted("Form sebelumnya belum pulih; membuka ulang tautan MUF satu kali", 1300);
    }
    private static boolean setText(AccessibilityNodeInfo field, String value) {
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }
    private boolean gestureSupported() {
        AccessibilityServiceInfo info = getServiceInfo();
        return info != null && (info.getCapabilities() & AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES) != 0;
    }
    private void tapInquiryLabel(TopupMatcher.Node label) {
        final InquirySession dispatched = session;
        float x = (label.left + label.right) / 2f, y = (label.top + label.bottom) / 2f;
        lastTapLocation = "label=[" + label.left + "," + label.top + "," + label.right + "," + label.bottom
            + "]; tengah=(" + x + "," + y + ")";
        if (panel != null) {
            int[] location = new int[2]; panel.getLocationOnScreen(location);
            if (new Rect(location[0], location[1], location[0] + panel.getWidth(), location[1] + panel.getHeight()).contains((int) x, (int) y)) {
                pauseForReason("Label Bayar tertutup panel pemeriksaan. Tidak mengirim ketukan; salin diagnosis teknis."); return;
            }
        }
        Path path = new Path(); path.moveTo(x, y);
        GestureDescription tap = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 0, 80)).build();
        acted("Menjadwalkan satu ketukan pada label Bayar terverifikasi; hasil Tokopedia belum terbuka", FlowTiming.inquiry(readyPageMode()));
        boolean accepted = dispatchGesture(tap, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription description) {
                if (active && session == dispatched) {
                    session.markTapCompleted();
                    waitFor("Ketukan Bayar selesai. Menunggu halaman rincian Tokopedia");
                    persistTrace();
                }
            }
            @Override public void onCancelled(GestureDescription description) {
                if (active && session == dispatched) {
                    session.markTapCancelled();
                    pauseForReason("Ketukan inquiry dibatalkan Android. Antrean dijeda tanpa mencoba ulang otomatis.");
                }
            }
        }, handler);
        if (accepted) {
            session.markTapDispatched();
            trace.event(session.stage().name(), "Android menerima gestur Bayar; belum membuktikan inquiry diterima Tokopedia");
            persistTrace();
        } else {
            session.markTapRejected();
            pauseForReason("Ketukan inquiry ditolak Android. Antrean dijeda; periksa aksesibilitas sebelum melanjutkan.");
        }
    }
    private void acted(String message, long delay) {
        lastAction = message;
        trace.event(session.stage().name(), message);
        persistTrace();
        nextAction = SystemClock.elapsedRealtime() + delay;
        waitFor(message);
    }
    private void waitFor(String message) {
        if (!message.equals(waitingFor)) {
            trace.event(session.stage().name(), "Status: " + message);
            persistTrace();
        }
        waitingFor = message;
        progress(message);
    }
    private boolean scroll(Snapshot s, String message, boolean forward) {
        AccessibilityNodeInfo target = s.scrollTarget(forward);
        if (target == null) return false;
        if (!session.allowScroll()) return false;
        if (!target.performAction(forward ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return false;
        acted(message + " (gulir " + session.scrolls() + "/6)", 1000);
        return true;
    }
    private AccessibilityNodeInfo tokopediaRoot() {
        checkerForeground = false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && TOKOPEDIA.contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) {
            rootStatus = "Root aktif Tokopedia"; return root;
        }
        // Only fall back through an overlay/IME. Never act on Tokopedia behind a different foreground app.
        boolean overlay = root == null || getPackageName().contentEquals(root.getPackageName() == null ? "" : root.getPackageName());
        if (root != null) root.recycle();
        List<AccessibilityWindowInfo> windows = getWindows();
        AccessibilityNodeInfo selected = null;
        try {
            for (AccessibilityWindowInfo window : windows) {
                if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD && window.isActive()) overlay = true;
            }
            if (!overlay) { rootStatus = "Aplikasi lain di depan; tindakan dijeda"; return null; }
            // Windows arrive topmost first. The first application must itself be Tokopedia.
            for (AccessibilityWindowInfo window : windows) {
                if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo candidate = window.getRoot();
                checkerForeground = candidate != null && getPackageName().contentEquals(candidate.getPackageName() == null ? "" : candidate.getPackageName());
                if (candidate != null && TOKOPEDIA.contentEquals(candidate.getPackageName() == null ? "" : candidate.getPackageName())) selected = candidate;
                else if (candidate != null) candidate.recycle();
                break;
            }
            rootStatus = selected == null ? "Root Tokopedia tidak tersedia di jendela aplikasi terdepan" : "Jendela aplikasi Tokopedia di bawah panel/keyboard";
            return selected;
        } finally { for (AccessibilityWindowInfo window : windows) window.recycle(); }
    }
    private boolean keyboardVisible() {
        List<AccessibilityWindowInfo> windows = getWindows();
        boolean found = false;
        for (AccessibilityWindowInfo w : windows) {
            if (w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) found = true;
            w.recycle();
        }
        return found;
    }
    private static boolean click(AccessibilityNodeInfo node) {
        if (node.isEnabled() && node.isClickable()) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        AccessibilityNodeInfo parent = node.getParent();
        for (int i = 0; parent != null && i < 3; i++) {
            if (parent.isEnabled() && parent.isClickable()) {
                boolean ok = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                parent.recycle();
                return ok;
            }
            AccessibilityNodeInfo next = parent.getParent();
            parent.recycle();
            parent = next;
        }
        if (parent != null) parent.recycle();
        return false;
    }
    private String diagnosticHeader() {
        return "MUF Checker 0.8.6\nPerangkat: " + Build.MANUFACTURER + " " + Build.MODEL
            + "\nAntrean: " + (batch.currentIndex() + 1) + "/" + batch.rows().size()
            + "\nAndroid: " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT
            + "\nTokopedia: " + tokopediaVersion + "\nTahap: " + session.stage()
            + "\nUsia tahap (dtk): " + session.stageAge(SystemClock.elapsedRealtime()) / 1000
            + "\nDurasi siklus (ms): " + session.elapsed(SystemClock.elapsedRealtime())
            + "\nPenahan layar aktif: " + screenHeld;
    }
    private void diagnose(Snapshot s, Map<String, String> fields, boolean provider, boolean input, boolean checkout) {
        String report = diagnosticHeader()
            + "\nSumber layar: " + rootStatus
            + "\nMenu Top-up: " + s.topupMatch.summary()
            + "\nNode terbaca: " + s.nodes.size() + "\nKolom mendukung teks: " + s.writableCount()
            + "\nDurasi pembacaan layar (ms): " + s.captureMs
            + "\nLabel kontrak: " + s.hasContractLabel() + "\nLabel penyedia: " + s.hasProviderLabel()
            + "\nNilai penyedia unik: " + (s.providerIndex() >= 0)
            + "\nPercobaan pengisian: " + session.inputAttempts()
            + "\nKolom kontrak: " + s.contractCapabilities()
            + "\nJumlah target inquiry: " + s.inquiryMatch.count
            + "\nPencarian Bayar: " + s.inquiryMatch.summary()
            + "\nLabel Bayar sebelum filter input: " + s.rawInquiryLabels
            + "\nGestur tersedia: " + gestureSupported()
            + "\nStatus ketukan: " + session.tapState() + "\nLokasi ketukan: " + lastTapLocation
            + "\nViewport: " + getResources().getDisplayMetrics().widthPixels + "x" + getResources().getDisplayMetrics().heightPixels
            + "\nHalaman rincian pernah teramati: " + session.resultObserved()
            + "\nLihat Detail terbaca: " + (s.detailsButton() != null)
            + "\nDaftar penyedia terbaca: " + s.providerListVisible()
            + "\nAksi terakhir: " + lastAction + "\nJumlah gulir tahap: " + session.scrolls()
            + "\nPenyedia MUF: " + provider + "\nForm kontrak: " + input + "\nRincian: " + checkout
            + "\nLabel berpasangan: " + fields.keySet()
            + "\nPercobaan inquiry dicadangkan: " + session.submitted() + "\nDetail dibuka: " + session.detailsOpened()
            + "\nIdentitas cocok: " + InquiryRules.identityMatches(contract, fields.containsKey("nomor pelanggan") ? fields.get("nomor pelanggan") : fields.get("nomor kontrak"));
        recordDiagnostic(report);
    }
    private void recordDiagnostic(String report) {
        trace.record(session.stage().name(), report);
        persistTrace();
    }
    private void persistTrace() {
        long now = SystemClock.elapsedRealtime(); String stage = session == null ? "PREPARE_FORM" : session.stage().name();
        if (savedTrace == trace && stage.equals(lastSavedStage) && now - lastTraceSave < 900) return;
        String report = trace.render();
        if (!report.equals(lastSavedReport)) getSharedPreferences("checker", MODE_PRIVATE).edit().putString("diagnostic", report).apply();
        lastSavedReport = report; lastSavedStage = stage; lastTraceSave = now; savedTrace = trace;
    }
    private String buildResult(Map<String, String> fields, LocalDate due) throws Exception {
        JSONObject json = new JSONObject();
        json.put("contract", contract);
        json.put("period", period.toString());
        json.put("due", due.toString());
        json.put("status", InquiryRules.status(due, period));
        json.put("reason", InquiryRules.reason(due, period));
        json.put("checked", ZonedDateTime.now(ZoneId.of("Asia/Makassar")).format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss 'WITA'")));
        json.put("source", "Tokopedia • inquiry aksesibilitas");
        JSONObject details = new JSONObject();
        for (Map.Entry<String, String> entry : fields.entrySet()) details.put(entry.getKey(), entry.getValue());
        json.put("details", details);
        return json.toString();
    }
    private void accountDuration() {
        if (batch == null || batch.current() == null || session == null) return;
        for (Map.Entry<String, Long> timing : session.drainDurations(SystemClock.elapsedRealtime()).entrySet())
            batch.addCurrentDuration(timing.getKey(), timing.getValue());
    }
    private boolean persistBatch(String result, String diagnostic) {
        try {
            accountDuration();
            boolean saved = store.save(batch, result, contract, diagnostic, false);
            if (saved) lastQueueCheckpoint = SystemClock.elapsedRealtime();
            android.content.SharedPreferences.Editor edit = getSharedPreferences("checker", MODE_PRIVATE).edit()
                .putBoolean("active", active).putBoolean("preparing", preparing);
            if (diagnostic != null) edit.putString("diagnostic", diagnostic);
            edit.apply(); return saved;
        } catch (RuntimeException e) { return false; }
    }
    private void closeTrace(String message) {
        accountDuration();
        if (trace == null) trace = new DiagnosticTrace(readyPageMode());
        if (session != null) trace.event(session.stage().name(), "Status ketukan akhir: " + session.tapState()
            + "; halaman rincian teramati=" + session.resultObserved());
        trace.finish(message);
    }
    private void completeCurrent(String result) {
        closeTrace("Selesai. Hasil kontrak terverifikasi dan disimpan sebelum pindah nomor.");
        batch.succeed(contract, result, trace.render()); consecutiveErrors = 0;
        if (!persistBatch(result, trace.render())) {
            batch.pause("Penyimpanan hasil belum terkonfirmasi. Antrean dijeda sebelum pindah nomor.", "");
            persistBatch(result, trace.render()); stopFlow(batch.reason()); return;
        }
        if (batch.state() == BulkQueue.RunState.RUNNING) startNextItem(true);
        else stopFlow(batch.state() == BulkQueue.RunState.COMPLETED ? batchSummary() : batch.reason());
    }
    private String batchSummary() {
        int successes = 0, errors = 0;
        for (BulkQueue.Row row : batch.rows()) {
            if (row.state() == BulkQueue.RowState.SUCCESS) successes++;
            if (row.state() == BulkQueue.RowState.ERROR) errors++;
        }
        return "Selesai · " + successes + " berhasil · " + errors + " error";
    }
    private void progress(String message) {
        if (active && batch != null && batch.current() != null) message = (batch.currentIndex() + 1) + "/" + batch.rows().size() + " · " + message
            + (batch.pauseRequested() ? " · Jeda setelah kontrak ini" : "");
        String compact = isRunning() && batch != null ? UiPresentation.panel(preparing,
            preparing && readyGate.canStart(SystemClock.elapsedRealtime()), batch.currentIndex(), batch.rows().size(),
            session == null ? InquirySession.Stage.FIND_FORM : session.stage(), batch.pauseRequested()) : message;
        if (!message.equals(lastProgress) || !compact.equals(lastCompact)) {
            lastProgress = message; lastCompact=compact;
            getSharedPreferences("checker", MODE_PRIVATE).edit().putString("progress", message).putString("progress_short", compact).apply();
        }
        if (panelText != null && !compact.contentEquals(panelText.getText())) panelText.setText(compact);
    }
    private boolean handleInquiryResponse(InquiryResponse.Signal signal,boolean quietExpired){
        if(!active || session==null || session.stage()!=InquirySession.Stage.WAIT_RESULT || !session.submitted()
            || session.resultObserved() || !InquiryResponse.accepted(session.tapState()))return false;
        if(signal!=InquiryResponse.Signal.NONE){
            trace.record("RESPONSE_DECISION","Pesan terbaca: "+signal.name()+"; ERROR disimpan sebelum nomor berikutnya; tanpa inquiry ulang.");
            finish(signal.reason,signal.contractError);return true;
        }
        if(quietExpired){
            trace.record("RESPONSE_DECISION","Form MUF dan kontrak tetap cocok tanpa indikator memuat; batas tanpa respons: 10000 ms.");
            finish("Tidak ada respons selama 10 detik pada form MUF terverifikasi. Tidak ada pesan blokir yang terbaca; status belum dapat disimpulkan.");return true;
        }
        return false;
    }
    private void finish(String message) { finish(message, false); }
    private void finish(String message, boolean businessError) {
        if (!active) return;
        closeTrace(message);
        if (batch.current() == null) {
            batch.pause(message, ""); persistBatch(null, trace.render()); stopFlow(message); return;
        }
        boolean recoverable = session != null && session.submitted();
        batch.fail(message, trace.render()); consecutiveErrors = businessError ? 0 : consecutiveErrors + 1;
        if (batch.hasUnfinished() && (!recoverable || consecutiveErrors >= 2))
            batch.pause(consecutiveErrors >= 2 ? "Dua kontrak berturut-turut gagal. Antrean dijeda; periksa diagnosis sebelum melanjutkan." : message, "");
        if (!persistBatch(null, trace.render())) {
            batch.pause("Penyimpanan antrean gagal. Tidak melanjutkan nomor berikutnya.", "");
            stopFlow(batch.reason()); return;
        }
        if (batch.state() == BulkQueue.RunState.RUNNING) {
            try { startNextItem(readyPageMode()); }
            catch (RuntimeException e) { pauseForReason("Form MUF belum dapat dipulihkan. Antrean dijeda."); }
        } else stopFlow(batch.state() == BulkQueue.RunState.COMPLETED ? batchSummary() : batch.reason());
    }
    private void pauseForReason(String message) {
        if (preparing) {
            String finalMessage = message;
            try {
                batch.pause(message, ""); trace.finish(message);
                String pending = null;
                for (BulkQueue.Row row : batch.rows()) if (row.state() == BulkQueue.RowState.WAITING) { pending = row.contract; break; }
                if (!store.save(batch, null, pending, trace.render(), false)) finalMessage += " Penyimpanan status belum terkonfirmasi.";
            } catch (RuntimeException e) { finalMessage += " Status belum dapat disimpan; antrean tidak dijalankan."; }
            stopFlow(finalMessage); return;
        }
        if (!active) return;
        closeTrace(message); batch.pause(message, trace.render());
        persistBatch(null, trace.render()); stopFlow(message);
    }
    private void stopFlow(String message) {
        active = false; preparing = false; readyGate.reset();
        inputVerification.clear();
        response.reset();
        if (screenAwake != null) screenAwake.release(); screenHeld = false;
        handler.removeCallbacks(eventTick); eventQueued = false;
        handler.removeCallbacks(tick);
        progress(message);
        getSharedPreferences("checker", MODE_PRIVATE).edit().putBoolean("active", false).putBoolean("preparing", false).apply();
        if (receiverRegistered) { unregisterReceiver(screenOff); receiverRegistered = false; }
        if (panel != null) {
            try { ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(panel); } catch (Exception ignored) {}
            panel = null;
            panelText = null;
            pauseButton = null;
        }
        String toast=batch!=null && batch.cancelledPreparation()?"Persiapan dibatalkan"
            : batch!=null && batch.state()==BulkQueue.RunState.COMPLETED?batchSummary()
            : batch!=null && batch.state()==BulkQueue.RunState.STOPPED?"Antrean dihentikan":"Antrean dijeda";
        android.widget.Toast.makeText(this, toast, android.widget.Toast.LENGTH_SHORT).show();
    }
    private void setPanelEnabled(boolean enabled) {
        if(panel instanceof FloatingPanel)((FloatingPanel)panel).setReady(enabled);
        else if(pauseButton!=null){pauseButton.setEnabled(enabled);pauseButton.setAlpha(enabled?1f:.45f);}
    }
    private void showPanel() {
        if (panel != null) {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(panel);
            panel = null; panelText = null; pauseButton = null;
        }
        FloatingPanel surface=new FloatingPanel(this,preparing,
            UiPresentation.panel(preparing,false,batch.currentIndex(),batch.rows().size(),
                session==null?InquirySession.Stage.FIND_FORM:session.stage(),batch.pauseRequested()));
        panel=surface;panelText=surface.message;pauseButton=surface.primary;
        pauseButton.setOnClickListener(v -> { if (preparing) requestPreparedStart(); else requestPause(); });
        surface.stop.setOnClickListener(v -> stopByUser());
        float density=getResources().getDisplayMetrics().density;
        int panelWidth = getResources().getDisplayMetrics().widthPixels - (int) (density * 24);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(panelWidth, -2,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | (!preparing && getSharedPreferences("checker", MODE_PRIVATE).getBoolean("keep_screen", true) ? WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON : 0),
            android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP;
        lp.y = (int) (getResources().getDisplayMetrics().density * 32);
        if (preparing) panel.setVisibility(View.GONE);
        ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(panel, lp);
    }
    private static final class Snapshot {
        final List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        final List<AccessibilityNodeInfo> visible = new ArrayList<>();
        final List<UiRules.Node> ui = new ArrayList<>();
        final List<AccessibilityNodeInfo> menuHandles = new ArrayList<>();
        final List<TopupMatcher.Node> menuTree = new ArrayList<>();
        final TopupMatcher.Result topupMatch;
        final UiRules.ButtonMatch inquiryMatch;
        final long captureMs;
        int rawInquiryLabels;
        final List<BillReader.Cell> cells = new ArrayList<>();
        final Map<String, AccessibilityNodeInfo> exact = new LinkedHashMap<>();
        private final Map<String, String> labelValues = new LinkedHashMap<>();
        private int cachedProviderIndex = Integer.MIN_VALUE;
        private boolean contractResolved, detailsResolved;
        private AccessibilityNodeInfo cachedContract, cachedDetails;
        private Map<String, String> cachedFields;
        final List<String> texts = new ArrayList<>();
        Snapshot(AccessibilityNodeInfo root, int width, int height) {
            long started = SystemClock.elapsedRealtime();
            walk(root, 0, -1);
            topupMatch = TopupMatcher.find(menuTree);
            inquiryMatch = UiRules.inquiryAction(menuTree, width, height);
            captureMs = SystemClock.elapsedRealtime() - started;
        }
        void walk(AccessibilityNodeInfo node, int depth, int parent) {
            if (depth > 60 || nodes.size() >= 2500 || node.isPassword()) return;
            String className = string(node.getClassName());
            // Capture properties once per node, and discard everything when this snapshot closes.
            CharSequence rawText = node.getText(), rawDescription = node.getContentDescription();
            String text = string(rawText), description = string(rawDescription);
            boolean visibleNow = node.isVisibleToUser(), enabled = node.isEnabled();
            boolean canSet = false, canClick = node.isClickable();
            for (AccessibilityNodeInfo.AccessibilityAction action : node.getActionList()) {
                if (action.getId() == AccessibilityNodeInfo.ACTION_SET_TEXT) canSet = true;
                if (action.getId() == AccessibilityNodeInfo.ACTION_CLICK) canClick = true;
            }
            boolean buttonRole = UiRules.buttonRole(className);
            boolean writable = UiRules.writable(enabled, node.isEditable(), canSet, className);
            Rect r = new Rect(); node.getBoundsInScreen(r);
            int menuIndex = menuTree.size();
            if (visibleNow && (InquiryRules.isInquiryButton(TopupMatcher.normalized(text))
                || InquiryRules.isInquiryButton(TopupMatcher.normalized(description)))) rawInquiryLabels++;
            menuTree.add(new TopupMatcher.Node(writable ? "" : text, writable ? "" : description,
                parent, r.left, r.top, r.right, r.bottom, visibleNow && !r.isEmpty(), enabled, canClick, buttonRole));
            menuHandles.add(node);
            if (visibleNow) {
                String hint = string(node.getHintText());
                String labelledBy = "";
                AccessibilityNodeInfo label = node.getLabeledBy();
                if (label != null) {
                    try {
                        labelledBy = string(label.getText());
                        if (labelledBy.isEmpty()) labelledBy = string(label.getContentDescription());
                    } finally { label.recycle(); }
                }
                visible.add(node);
                ui.add(new UiRules.Node(text, hint, description,
                    labelledBy, node.getViewIdResourceName(), r.left, r.top, r.right, r.bottom, writable));
                if (writable) {
                    // Keep input values out of the general text/field reader and diagnosis.
                    texts.add(InquiryRules.normalized(hint));
                    texts.add(InquiryRules.normalized(description));
                    texts.add(InquiryRules.normalized(labelledBy));
                } else {
                    CharSequence t = rawText;
                    if (t == null || t.length() == 0) t = rawDescription;
                    if (t != null && t.length() > 0) {
                        String value = t.toString().trim();
                        String norm = InquiryRules.normalized(value);
                        texts.add(norm);
                        // Duplicate text nodes at exactly the same bounds are common in WebViews.
                        String unique = norm + "@" + r.toShortString();
                        if (!exact.containsKey(unique)) {
                            exact.put(unique, node);
                            labelValues.put(unique, text.isEmpty() ? description : text);
                            cells.add(new BillReader.Cell(value, r.left, r.top, r.right, r.bottom));
                        }
                    }
                }
            }
            int childCount = node.getChildCount();
            for (int i = 0; i < childCount && nodes.size() < 2500; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) { nodes.add(child); walk(child, depth + 1, menuIndex); }
            }
        }
        boolean has(String value) {
            String target = InquiryRules.normalized(value);
            for (String t : texts) if (t.equals(target)) return true;
            return false;
        }
        boolean hasAny(String... values) {
            for (String v : values) for (String t : texts) if (t.contains(v)) return true;
            return false;
        }
        InquiryResponse.Signal responseSignal(){
            InquiryResponse.Signal signal=InquiryResponse.Signal.NONE;StringBuilder joined=new StringBuilder();
            for(BillReader.Cell cell:cells){
                signal=InquiryResponse.prefer(signal,InquiryResponse.classify(cell.text));
                if(joined.length()<16000)joined.append(cell.text).append(' ');
            }
            return InquiryResponse.prefer(signal,InquiryResponse.classify(joined.toString()));
        }
        boolean loading(){
            for(AccessibilityNodeInfo node:visible){
                CharSequence name=node.getClassName();if(name!=null && name.toString().contains("ProgressBar"))return true;
            }
            return hasAny("memuat","mohon tunggu","sedang diproses","sedang memproses","mengambil tagihan");
        }
        private static String string(CharSequence s) { return s == null ? "" : s.toString().trim(); }
        private static boolean supports(AccessibilityNodeInfo node, int action) {
            for (AccessibilityNodeInfo.AccessibilityAction a : node.getActionList()) if (a.getId() == action) return true;
            return false;
        }
        int writableCount() { int count = 0; for (UiRules.Node n : ui) if (n.writable) count++; return count; }
        Map<String, String> fields() { if (cachedFields == null) cachedFields = BillReader.fields(cells); return cachedFields; }
        int providerIndex() { if (cachedProviderIndex == Integer.MIN_VALUE) cachedProviderIndex = UiRules.providerValue(ui); return cachedProviderIndex; }
        boolean selectedMuf() { int index = providerIndex(); return index >= 0 && InquiryRules.isProvider(UiRules.providerName(ui.get(index))); }
        AccessibilityNodeInfo contractField() {
            if (contractResolved) return cachedContract;
            contractResolved = true;
            int index = UiRules.contractField(ui);
            if (index < 0) return null;
            if (supports(visible.get(index), AccessibilityNodeInfo.ACTION_SET_TEXT)) return cachedContract = visible.get(index);
            UiRules.Node selected = ui.get(index);
            for (int i = 0; i < ui.size(); i++) {
                UiRules.Node n = ui.get(i);
                if (n.writable && selected.left == n.left && selected.top == n.top && selected.right == n.right
                    && selected.bottom == n.bottom && supports(visible.get(i), AccessibilityNodeInfo.ACTION_SET_TEXT)) return cachedContract = visible.get(i);
            }
            return cachedContract = visible.get(index);
        }
        String contractCapabilities() {
            AccessibilityNodeInfo field = contractField();
            if (field == null) return "belum unik/terbaca";
            return "editable=" + field.isEditable() + ", setText=" + supports(field, AccessibilityNodeInfo.ACTION_SET_TEXT)
                + ", hintKontrak=" + UiRules.contractLabel(string(field.getHintText()))
                + ", deskripsiKontrak=" + UiRules.contractLabel(string(field.getContentDescription()));
        }
        boolean hasProviderLabel() {
            for (UiRules.Node n : ui) if (UiRules.providerLabel(n.label()) || !UiRules.combinedProvider(n.label()).isEmpty()) return true;
            return false;
        }
        boolean providerListVisible() {
            if (hasAny("pilih penyedia layanan", "pilih penyedia pinjaman", "cari penyedia", "cari perusahaan", "pilih perusahaan")) return true;
            if (hasProviderLabel() && !hasContractLabel() && UiRules.providerSearch(ui) >= 0) return true;
            int providers = 0;
            for (String name : new String[]{"Mandiri Utama Finance", "Kredivo", "Adira Finance", "BFI Finance", "FIF", "FIF Group", "WOM Finance", "Bussan Auto Finance"})
                if (has(name)) providers++;
            // A list of multiple providers is distinct from the single selected value on the form.
            return providers >= 2;
        }
        boolean hasContractLabel() {
            for (UiRules.Node n : ui) if (UiRules.contractLabel(n.label()) || UiRules.contractLabel(n.hint)
                || UiRules.contractLabel(n.description) || UiRules.contractLabel(n.labelledBy)) return true;
            return false;
        }
        AccessibilityNodeInfo providerControl() {
            int index = providerIndex();
            if (index >= 0 && enabledAction(visible.get(index))) return visible.get(index);
            // Some spinners expose only the provider label as their accessible clickable child.
            AccessibilityNodeInfo found = null;
            for (int i = 0; i < ui.size(); i++) if (UiRules.providerLabel(ui.get(i).label()) && enabledAction(visible.get(i))) {
                if (found != null) return null;
                found = visible.get(i);
            }
            return found;
        }
        boolean enabledAction(AccessibilityNodeInfo node) {
            if (node == null) return false;
            if (!node.isEnabled()) return false;
            if (node.isClickable() || supports(node, AccessibilityNodeInfo.ACTION_CLICK)) return true;
            AccessibilityNodeInfo parent = node.getParent();
            for (int i = 0; parent != null && i < 3; i++) {
                if (!parent.isEnabled()) { parent.recycle(); return false; }
                if (parent.isClickable()) { parent.recycle(); return true; }
                AccessibilityNodeInfo next = parent.getParent(); parent.recycle(); parent = next;
            }
            if (parent != null) parent.recycle();
            return false;
        }
        AccessibilityNodeInfo topupButton() { return topupMatch.target < 0 ? null : menuHandles.get(topupMatch.target); }
        AccessibilityNodeInfo detailsButton() {
            if (detailsResolved) return cachedDetails;
            detailsResolved = true;
            AccessibilityNodeInfo found = null;
            for (Map.Entry<String, AccessibilityNodeInfo> entry : exact.entrySet()) {
                String label = labelValues.get(entry.getKey());
                if (!UiRules.detailLabel(label)) continue;
                if (found != null) return null;
                found = entry.getValue();
            }
            return cachedDetails = found;
        }
        AccessibilityNodeInfo exactUnique(String title) {
            AccessibilityNodeInfo found = null;
            for (AccessibilityNodeInfo node : exact.values()) {
                CharSequence text = node.getText();
                if (text == null || text.length() == 0) text = node.getContentDescription();
                if (text != null && InquiryRules.normalized(text.toString()).equals(InquiryRules.normalized(title))) {
                    if (found != null) return null;
                    found = node;
                }
            }
            return found;
        }
        AccessibilityNodeInfo inquiryButton() {
            return inquiryMatch.target < 0 ? null : menuHandles.get(inquiryMatch.target);
        }
        AccessibilityNodeInfo scrollTarget(boolean forward) {
            // Prefer the smallest scrollable surface (e.g. a provider list inside a dialog).
            AccessibilityNodeInfo target = null;
            long area = Long.MAX_VALUE;
            for (int i = 0; i < visible.size(); i++) {
                AccessibilityNodeInfo n = visible.get(i);
                int action = forward ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
                if (!n.isEnabled() || !n.isScrollable() || !supports(n, action)) continue;
                UiRules.Node bounds = ui.get(i);
                long size = (long) (bounds.right - bounds.left) * (bounds.bottom - bounds.top);
                if (size > 0 && size < area) { target = n; area = size; }
            }
            return target;
        }
        void close() { for (AccessibilityNodeInfo node : nodes) node.recycle(); }
    }
}
