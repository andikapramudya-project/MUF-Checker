import id.local.mufchecker.BulkQueue;
import id.local.mufchecker.InquirySession;
import id.local.mufchecker.ResultFormatter;
import id.local.mufchecker.BillReader;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Synthetic queue, persistence, stale-result and presentation regressions. No customer inquiries. */
public final class BulkQueueTest {
    private static int checks;
    private static final String A = "012345670001", B = "023456780002", C = "034567890003";
    private static final YearMonth PERIOD = YearMonth.of(2026, 10);
    private static void ok(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void reject(Runnable action, String message) {
        checks++; try { action.run(); } catch (RuntimeException expected) { return; } throw new AssertionError(message);
    }
    private static BulkQueue queue() { return new BulkQueue(Arrays.asList(A, B, C), PERIOD); }
    public static void main(String[] args) throws Exception {
        preparationCancellation();
        legacySnapshot(args);
        legacy100(args);
        BulkQueue.Input input = BulkQueue.parseLines("\uFEFF" + A + "\r\n\n " + B + " \r\n" + A + "\n" + C);
        ok(input.contracts.equals(Arrays.asList(A, B, C)), "One per line, CRLF, blank rows, order and leading zeros");
        ok(input.duplicates == 1, "Duplicates counted and removed");
        reject(() -> BulkQueue.parseLines(""), "Reject empty queue");
        reject(() -> BulkQueue.parseLines(A + "," + B), "Do not guess comma-separated data");
        reject(() -> BulkQueue.parseLines("0123 45670001"), "Do not remove inner spaces from contract");
        reject(() -> BulkQueue.parseLines("0123\uFEFF45670001"), "Do not strip inner BOM from a number");
        reject(() -> BulkQueue.parseLines("1e10"), "No numeric coercion");
        ok(BulkQueue.parseLines("01234567\n02345678\n03456789\n04567890\n05678901\n06789012").contracts.size() == 6, "Accept more than five contracts");
        ok(BulkQueue.parseLines("01234567\n02345678\n03456789\n04567890\n05678901").contracts.size() == 5, "Five-contract trial accepted");
        reject(() -> new BulkQueue(Arrays.asList(A, A), PERIOD), "Queue itself enforces unique contracts");
        BulkQueue q = queue();
        ok(q.startNext().contract.equals(A) && q.currentIndex() == 0, "Start only first item");
        ok(q.startNext() == null, "Cannot advance before active item is resolved");
        reject(() -> q.succeed(B, "wrong", ""), "Prevent a result from another contract being assigned to current row");
        reject(() -> q.succeed(A, "", ""), "No empty successful result");
        String payload = "{\"contract\":\"" + A + "\",\"name\":\"SYNTHETIC & \\u2022\",\"amount\":\"Rp1.000\"}\nnext line";
        q.succeed(A, payload, "SAFE META\nStage READ_DETAILS; identity=true");
        BulkQueue restored = BulkQueue.restore(q.serialize());
        ok(restored.rows().get(0).state() == BulkQueue.RowState.SUCCESS && restored.rows().get(0).result().equals(payload), "Persist exact payload before navigating away");
        ok(restored.rows().get(0).diagnostic().contains("READ_DETAILS"), "Retain per-contract diagnostic");
        ok(restored.period.equals(PERIOD), "Period persists for entire batch");
        ok(restored.startNext().contract.equals(B), "Restore skips already saved result");
        reject(() -> restored.succeed(A, payload, ""), "Reject prior contract result when next item is active");
        Map<String, String> stale = new HashMap<>(); stale.put("nomor pelanggan", "01234567****"); stale.put("jatuh tempo", "02-OCT-26");
        reject(() -> BillReader.verifiedDue(stale, B), "Previous checkout cannot satisfy next contract identity");
        InquirySession rowSession = new InquirySession(0); rowSession.move(InquirySession.Stage.RETURN_FORM, 0);
        ok(!rowSession.markSubmitted(true, true, false, B, B, 0), "Returning from checkout is not verified input stage");
        ok(rowSession.returnAction(false, true, false, 100) == InquirySession.ReturnAction.BACK, "Return to input with one Back action after saved result");
        ok(rowSession.returnAction(false, true, false, 1500) == InquirySession.ReturnAction.WAIT, "Do not repeatedly press Back while page is changing");
        ok(rowSession.returnAction(false, false, false, 6500) == InquirySession.ReturnAction.WAIT, "Do not reopen Tokopedia over a different foreground app");
        ok(rowSession.returnAction(false, false, true, 6500) == InquirySession.ReturnAction.OPEN_LINK, "Back closes Tokopedia to checker: recover via direct link once");
        ok(rowSession.returnAction(false, true, false, 8000) == InquirySession.ReturnAction.WAIT, "Never reopen recovery link repeatedly");
        InquirySession ready = new InquirySession(0); ready.move(InquirySession.Stage.RETURN_FORM, 0);
        ok(ready.returnAction(true, true, false, 500) == InquirySession.ReturnAction.USE_FORM, "Reused form needs no Back or link reopen");
        InquirySession homepage = new InquirySession(0); homepage.move(InquirySession.Stage.RETURN_FORM, 0);
        homepage.returnAction(false, true, false, 100);
        ok(homepage.returnAction(false, true, false, 6500) == InquirySession.ReturnAction.OPEN_LINK, "Recover if Back lands on Tokopedia home");
        ok(homepage.expired(20000), "Failed form recovery remains bounded and can pause queue");
        restored.fail("Tagihan tidak dapat dibaca", "SAFE ERROR");
        ok(restored.rows().get(1).state() == BulkQueue.RowState.ERROR && restored.rows().get(1).result().isEmpty(), "Failure remains ERROR rather than unpaid or previous result");
        ok(restored.startNext().contract.equals(C), "Recoverable error can advance to next pending item");
        restored.succeed(C, "result C", "SAFE META C");
        ok(restored.state() == BulkQueue.RunState.COMPLETED && restored.completedCount() == 3, "Complete batch containing success and error rows");
        ok(!restored.resume() && restored.startNext() == null, "Completed batch cannot be repeated by Resume");
        BulkQueue paused = queue(); paused.startNext(); paused.requestPause();
        ok(paused.pauseRequested() && paused.state() == BulkQueue.RunState.RUNNING, "Pause waits for active contract boundary");
        paused.succeed(A, "result A", "meta A");
        ok(paused.state() == BulkQueue.RunState.PAUSED && paused.startNext() == null, "No next query after pause request");
        paused = BulkQueue.restore(paused.serialize());
        ok(paused.resume() && paused.startNext().contract.equals(B), "Resume after pause starts next pending contract");
        paused.pause("Login required", "safe login trace");
        ok(paused.rows().get(1).state() == BulkQueue.RowState.WAITING && paused.rows().get(0).state() == BulkQueue.RowState.SUCCESS, "Login pause does not invent a result or discard completed result");
        ok(paused.resume() && paused.startNext().contract.equals(B), "Explicit resume can restart unfinished inquiry after login");
        paused.stop("Stopped by user", "stop trace");
        ok(paused.state() == BulkQueue.RunState.STOPPED && paused.rows().get(1).state() == BulkQueue.RowState.WAITING, "Stop preserves unfinished rows without marking them paid/unpaid");
        ok(paused.rows().get(0).result().equals("result A"), "Stop retains previously saved result");
        BulkQueue interrupted = queue(); interrupted.startNext(); interrupted.succeed(A, "result A", "meta A"); interrupted.startNext();
        interrupted = BulkQueue.restore(interrupted.serialize());
        ok(interrupted.current().contract.equals(B), "Processing marker survives a process death");
        ok(interrupted.recoverInterrupted() && interrupted.state() == BulkQueue.RunState.PAUSED, "Restart requires user resume, never automatically reruns a partial inquiry");
        ok(!interrupted.recoverInterrupted(), "Recovery is idempotent");
        ok(interrupted.resume() && interrupted.startNext().contract.equals(B), "Resume skips successful rows after interruption");
        ok(interrupted.rows().get(0).result().equals("result A"), "Recovery does not replace saved result");
        BulkQueue last = new BulkQueue(Arrays.asList(A), PERIOD); last.startNext(); last.requestPause(); last.succeed(A, "ok", "meta");
        ok(last.state() == BulkQueue.RunState.COMPLETED && !last.hasUnfinished(), "Pause on final row still ends completed");
        reject(() -> BulkQueue.restore("not a queue"), "Reject malformed storage");
        reject(() -> BulkQueue.restore(q.serialize().replace("SUCCESS", "WAITING").replace("RUNNING", "COMPLETED")), "Inconsistent completed state is rejected");
        ok(ResultFormatter.cleanName("DEM&#x4f;**&#x20;USER***").equals("DEMO** USER***"), "Decode hex HTML and preserve all masks");
        ok(ResultFormatter.cleanName("DEM&#79;**&#32;USER***").equals("DEMO** USER***"), "Decode decimal HTML entities");
        ok(ResultFormatter.cleanName("A&amp;B&nbsp;***").equals("A&B ***"), "Named entities and NBSP preserve masks");
        ok(ResultFormatter.cleanName("DEM&amp;#x4f;**").equals("DEMO**"), "Decode one nested entity level");
        ok(ResultFormatter.cleanName("D*** U***").equals("D*** U***"), "Never unmask missing letters");
        ok(ResultFormatter.cleanName("MAX***").equals("MAX***"), "Do not strip real letters or asterisks");
        ok(ResultFormatter.cleanName("***").equals("***"), "Fully masked name retains source masking");
        ok(ResultFormatter.cleanName("DEM** US** A M* TEST*").equals("DEM** US** A M* TEST*"), "Preserve spaced and trailing asterisks");
        ok(ResultFormatter.cleanName("[AMBIGU]").equals("—"), "Ambiguous name is unavailable");
        ok(ResultFormatter.status("TERINDIKASI SUDAH BAYAR").equals("SUDAH BAYAR"), "Compact paid status");
        ok(ResultFormatter.status("TERINDIKASI BELUM BAYAR").equals("BELUM BAYAR"), "Compact unpaid status");
        ok(ResultFormatter.status("JATUH TEMPO TERTINGGAL").equals("BELUM BAYAR"), "Arrears remain unpaid while stored reason remains available");
        ok(ResultFormatter.status("ERROR").equals("ERROR"), "Error is never turned into unpaid");
        ok(ResultFormatter.checked("23-10-2026 18:22:32 WITA").equals("23/10 18:22"), "Compact legacy timestamp");
        ok(ResultFormatter.checked("2026-10-23T18:22:32+08:00[Asia/Makassar]").equals("23/10 18:22"), "Compact zoned timestamp");
        String compact = ResultFormatter.compact("TERINDIKASI BELUM BAYAR", A, "DEM&#x4f;**&#x20;USER***", "28", "Rp4.091.500", "23-10-2026 18:22:32 WITA");
        ok(compact.equals("BELUM BAYAR\n\nKontrak: " + A + "\nNama: DEMO** USER***\nCicilan ke: 28\nAngsuran: Rp4.091.500\n\nDiperiksa 23/10 18:22"), "Compact card preserves source masks");
        ok(!compact.contains("Admin") && !compact.contains("Total") && !compact.contains("AMBIGU"), "Do not display ambiguous totals or admin in compact card");
        largeBatch(20); largeBatch(50); largeBatch(100);
        java.util.List<String> tooMany = synthetic(101);
        reject(() -> new BulkQueue(tooMany, PERIOD), "101 contracts rejected by model");
        reject(() -> BulkQueue.parseLines(String.join("\n", tooMany)), "101 unique lines rejected");
        BulkQueue retry = queue(); retry.startNext(); retry.succeed(A, "KEEP", "LONG DIAGNOSTIC"); retry.startNext(); retry.fail("error", "trace");
        ok(!retry.retryErrors(), "Cannot retry during a running queue"); retry.pause("check", "");
        ok(retry.retryErrors() && retry.state() == BulkQueue.RunState.PAUSED, "Explicit retry leaves queue paused until Resume");
        ok(retry.rows().get(0).result().equals("KEEP") && retry.rows().get(1).state() == BulkQueue.RowState.WAITING, "Retry preserves successes and resets only error");
        ok(!retry.retryErrors(), "No duplicate retry request");
        ok(retry.resume() && retry.startNext().contract.equals(B), "Explicit resume selects earliest retried error");
        reject(() -> retry.succeed(A, "stale", ""), "Retry still rejects stale prior identity");
        retry.succeed(B, "RETRY OK", "trace"); retry.startNext(); retry.fail("error C", "trace");
        ok(retry.state() == BulkQueue.RunState.COMPLETED && retry.retryErrors(), "Completed batch can requeue errors");
        ok(retry.resume() && retry.startNext().contract.equals(C), "Completed retry skips every successful row");
        System.out.println("PASS: " + checks + " bulk/format checks (20/50/100 contracts, durable recovery, explicit error retry, source masks)");
    }
    private static java.util.List<String> synthetic(int count) {
        java.util.List<String> values = new java.util.ArrayList<>();
        for (int i = 1; i <= count; i++) values.add(String.format(java.util.Locale.ROOT, "09000000%04d", i));
        return values;
    }
    private static void legacySnapshot(String[] args) throws Exception {
        String raw = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[0])), java.nio.charset.StandardCharsets.UTF_8);
        BulkQueue old = BulkQueue.restore(raw);
        ok(old.rows().size()==50 && old.sessionId().equals("synthetic-v070-ready-50"),"Read a snapshot emitted by the original 0.7.0 implementation");
        ok(old.createdAt()==1793030400000L && old.startedAt()==1793030401000L && old.finishedAt()==0,"Preserve old session timestamps");
        ok(old.completedCount()==26 && old.errorCount()==1 && old.activeMs()==27600L,"Preserve old progress, error and active timing");
        for(int i=0;i<25;i++) {
            String number=synthetic(50).get(i);
            String payload="{\"contract\":\""+number+"\",\"name\":\"DEMO** USER***\",\"due\":\"2026-11-01\"}";
            ok(old.rows().get(i).result().equals(payload),"Old payload, leading zero and masked name stay exact at "+i);
            ok(old.rows().get(i).stageMs().get("WAIT_RESULT")==1000L+i,"Old stage timing stays with its row "+i);
        }
        ok(old.prepareReadyPage() && old.startPrepared() && old.startNext().contract.equals("090000000027"),"Old paused queue resumes first unfinished contract after explicit Start");
        old.pause("test","");ok(old.retryErrors() && old.prepareReadyPage() && old.startPrepared(),"Old errors remain eligible for explicit retry");
        ok(old.startNext().contract.equals("090000000026") && old.rows().get(0).result().contains("DEMO** USER***"),"Old error retry skips successful results");
        reject(() -> BulkQueue.restore(raw + "\nrow.0.stage.invalid=1\n"),"Optimized timing index still rejects invalid stage names");
        reject(() -> BulkQueue.restore(raw + "\nrow.0.stage.WAIT_RESULT=bad\n"),"Optimized timing index still rejects invalid durations");
        ok(BulkQueue.restore(raw+"\nrow.999.stage.invalid=1\n").activeMs()==27600L,"Unrecognized future row keys retain old compatibility behavior");
    }
    private static void legacy100(String[] args) throws Exception {
        String raw=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[1])),java.nio.charset.StandardCharsets.UTF_8);
        BulkQueue old=BulkQueue.restore(raw);
        ok(old.rows().size()==100 && old.sessionId().equals("synthetic-v071-ready-100"),"Read original 0.7.1 100-row snapshot without migration");
        ok(old.completedCount()==41 && old.errorCount()==0 && old.activeMs()==42120L,"Preserve 41 committed old results and partial active time");
        ok(old.createdAt()==1793030400000L && old.startedAt()==1793030401000L && old.finishedAt()==0,"Preserve 0.7.1 timestamps");
        for(int i=0;i<41;i++){
            String number=synthetic(100).get(i);
            String payload="{\"contract\":\""+number+"\",\"name\":\"DEMO** USER***\",\"due\":\"2026-11-01\"}";
            ok(old.rows().get(i).result().equals(payload),"Exact old result and mask retained at "+i);
            ok(old.rows().get(i).stageMs().get("WAIT_RESULT")==1000L+i,"Old per-row timing retained at "+i);
        }
        ok(old.prepareReadyPage() && old.startPrepared() && old.startNext().contract.equals("090000000042"),"Old 100-row queue resumes at row 42 only after explicit Start");
        old.succeed("090000000042","new verified result","safe");old=BulkQueue.restore(old.serialize(false));
        ok(old.completedCount()==42 && old.rows().get(0).result().contains("DEMO** USER***"),"New result does not overwrite the old committed rows");
    }
    private static void preparationCancellation() {
        BulkQueue draft=new BulkQueue(Arrays.asList(A,B),PERIOD,BulkQueue.StartMode.READY_PAGE);
        ok(draft.unstartedPreparation(),"Fresh ready-page preparation has no inquiry history");
        ok(!draft.cancelledPreparation(),"Armed draft is not an old cancellation");
        draft.pause("Persiapan dibatalkan. Antrean tersimpan; siapkan ulang untuk menampilkan tombol Mulai.","");
        ok(draft.cancelledPreparation(),"Recognize cancellation saved by 0.8.3");
        draft=BulkQueue.restore(draft.serialize(false));
        ok(draft.cancelledPreparation(),"Legacy cancelled draft remains recognizable after restart");
        draft.stop("Persiapan dibatalkan. Daftar kontrak dapat diubah.","");
        ok(draft.cancelledPreparation() && draft.completedCount()==0,"New cancelled history preserves waiting rows");
        draft.prepareReadyPage();ok(!draft.cancelledPreparation(),"Re-armed preparation cannot be released as old cancellation");
        draft.startPrepared();ok(!draft.unstartedPreparation(),"Explicit Start protects a session even before its first row");
        draft.stop("Persiapan dibatalkan.","");ok(!draft.cancelledPreparation(),"Start then immediate STOP remains protected");
        BulkQueue attempted=new BulkQueue(Arrays.asList(A,B),PERIOD,BulkQueue.StartMode.READY_PAGE);
        attempted.startPrepared();attempted.startNext();attempted.addCurrentDuration("WAIT_RESULT",1);
        attempted.stop("Persiapan dibatalkan.","");
        ok(!attempted.cancelledPreparation(),"Attempted waiting row is never mistaken for a draft");
        attempted.prepareReadyPage();ok(!attempted.unstartedPreparation(),"Cancelling resumed preparation preserves attempted work");
        attempted.startPrepared();attempted.startNext();attempted.succeed(A,"verified-result","");attempted.pause("pause","");
        ok(!attempted.unstartedPreparation(),"Saved successful result prevents release");
        ok(BulkQueue.restore(attempted.serialize()).rows().get(0).result().equals("verified-result"),"Cancellation policy never rewrites results");
        BulkQueue technical=new BulkQueue(Arrays.asList(A),PERIOD,BulkQueue.StartMode.READY_PAGE);
        technical.pause("Form tidak tersedia","");ok(!technical.cancelledPreparation(),"Technical pause is not implicit user cancellation");
        BulkQueue direct=new BulkQueue(Arrays.asList(A),PERIOD);direct.stop("Persiapan dibatalkan.","");
        ok(!direct.unstartedPreparation(),"Direct-link sessions never use preparation release");
    }
    private static void largeBatch(int size) {
        java.util.List<String> values = synthetic(size);
        ok(BulkQueue.parseLines(String.join("\n", values) + "\n" + values.get(0)).contracts.equals(values), "Large input preserves order/leading zero and deduplicates");
        BulkQueue q = new BulkQueue(values, PERIOD);
        for (int i = 0; i < size; i++) {
            ok(q.startNext().contract.equals(values.get(i)), "Large batch sequence " + i);
            ok(q.currentIndex() == i && q.completedCount() == i, "No skipped or duplicate row " + i);
            if (i == size / 2) {
                q = BulkQueue.restore(q.serialize(false)); q.recoverInterrupted();
                ok(q.resume() && q.startNext().contract.equals(values.get(i)), "Interrupted large queue restarts only active row");
            }
            q.succeed(values.get(i), "payload " + i, "A LARGE PRIVATE DIAGNOSTIC BLOCK");
            String lean = q.serialize(false);
            ok(!lean.contains("PRIVATE DIAGNOSTIC"), "Queue checkpoint excludes long traces");
            q = BulkQueue.restore(lean);
            ok(q.rows().get(i).result().equals("payload " + i), "Result survives lean checkpoint");
        }
        ok(q.state() == BulkQueue.RunState.COMPLETED && q.completedCount() == size && !q.resume(), "Entire large batch completed once");
    }
}
