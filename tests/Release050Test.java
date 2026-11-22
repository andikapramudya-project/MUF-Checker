import id.local.mufchecker.*;
import java.time.YearMonth;
import java.util.Arrays;

/** Regression scenarios for session identity, timing, transient errors and back navigation. */
public final class Release050Test {
    private static int checks;
    private static void ok(boolean v, String message) { checks++; if (!v) throw new AssertionError(message); }
    public static void main(String[] args) {
        String a = "090000000001", b = "090000000002";
        BulkQueue q = new BulkQueue(Arrays.asList(a,b), YearMonth.of(2026,10));
        BulkQueue other = new BulkQueue(Arrays.asList(a,b), q.period);
        ok(!q.sessionId().equals(other.sessionId()), "Same contracts in different sessions have different identities");
        String id = q.sessionId(); long created = q.createdAt();
        q.startNext(); q.addCurrentDuration(2000); q.recoveredForm(); q.addCurrentDuration(3000);
        q.pause("pause", "trace"); q = BulkQueue.restore(q.serialize(false));
        ok(q.sessionId().equals(id) && q.createdAt() == created, "Session metadata survives interruption");
        ok(q.rows().get(0).durationMs() == 5000 && q.activeMs() == 5000, "Record active time before suspension");
        q.addCurrentDuration(9999); ok(q.activeMs() == 5000, "Pause does not accrue active time");
        q.resume(); q.startNext(); q.addCurrentDuration(1500); q.succeed(a,"result","safe");
        ok(q.rows().get(0).durationMs() == 6500 && q.rows().get(0).recoveries() == 1, "Resume accumulates only active attempts");
        q.startNext(); q.addCurrentDuration(2000); q.fail("Pembayaran dibatasi — hubungi kantor cabang.","safe");
        ok(q.finishedAt() >= created && q.state() == BulkQueue.RunState.COMPLETED, "Final error still completes session with timestamp");
        ok(q.activeMs() == 8500, "Session active total sums each row once");
        BulkQueue saved = BulkQueue.restore(q.serialize());
        ok(saved.finishedAt() == q.finishedAt() && saved.activeMs() == 8500, "Finish/timings persist");
        saved.retryErrors(); ok(saved.finishedAt() == 0 && saved.rows().get(0).result().equals("result"), "Retry clears end time but retains successful row");
        String legacy = q.serialize(false).replaceAll("(?m)^(sessionId|createdAt|finishedAt|row\\.[0-9]+\\.(durationMs|recoveries|stage\\.[A-Z_]+))=.*\\R?", "");
        BulkQueue migrated = BulkQueue.restore(legacy);
        ok(migrated.sessionId().equals("legacy-v040") && migrated.createdAt() == 0, "Old snapshot uses explicit unknown timestamp, not invented date");
        ok(migrated.completedCount() == 2 && migrated.rows().get(0).result().equals("result"), "Migration preserves completed payload");
        ok(migrated.activeMs() == 0, "No fabricated old duration");
        for (String text : new String[]{"Pembayaran tidak dapat dilakukan, silahkan hubungi kantor cabang terdekat", "PEMBAYARAN TIDAK BISA DILAKUKAN. Silakan hubungi kantor cabang.", "Transaksi tidak dapat dilakukan\nHubungi cabang terdekat"})
            ok(FlowSupport.branchRestriction(text), "Recognize explicit branch restriction");
        for (String text : new String[]{null,"pembayaran berhasil","pembayaran tidak dapat dilakukan","hubungi cabang","Coba lagi nanti","Informasi pembayaran. Pastikan nomor sesuai"})
            ok(!FlowSupport.branchRestriction(text), "Generic messages cannot imply contract blocking");
        ok(FlowSupport.belongsToInquiry(true,true,false,1000,1100,1200), "Accept fresh event after current tap");
        ok(!FlowSupport.belongsToInquiry(true,true,false,1000,999,1200), "Reject previous contract event");
        ok(!FlowSupport.belongsToInquiry(false,true,false,1000,1100,1200), "Ignore events while idle");
        ok(!FlowSupport.belongsToInquiry(true,false,false,1000,1100,1200), "Ignore events before inquiry");
        ok(!FlowSupport.belongsToInquiry(true,true,true,1000,1100,1200), "Ignore error event after observed result");
        ok(!FlowSupport.belongsToInquiry(true,true,false,1000,1100,6200), "Reject stale delayed event");
        ok(!FlowSupport.belongsToInquiry(true,true,false,1000,1300,1200), "Reject future event");
        InquirySession flow = new InquirySession(0); flow.move(InquirySession.Stage.RETURN_FORM,0);
        ok(flow.returnAction(false,true,false,2200) == InquirySession.ReturnAction.BACK,"Issue one back");
        ok(flow.returnAction(false,true,false,6000) == InquirySession.ReturnAction.WAIT,"Recovery clock starts at actual Back, not stage start");
        flow.returnProgress(8000);
        ok(flow.returnAction(false,true,false,8300) == InquirySession.ReturnAction.WAIT,"Let a changing form finish loading");
        ok(flow.returnAction(true,true,false,9000) == InquirySession.ReturnAction.USE_FORM,"Ready form immediately wins over fallback");
        flow.returnProgress(12000);
        ok(flow.returnAction(false,true,false,12200) == InquirySession.ReturnAction.OPEN_LINK,"Bounded recovery even if changes continue");
        ok(flow.returnAction(false,true,false,13000) == InquirySession.ReturnAction.WAIT,"Only one fallback link");
        InquirySession background = new InquirySession(0); background.move(InquirySession.Stage.RETURN_FORM,0);
        background.returnAction(false,true,false,0);
        ok(background.returnAction(false,false,false,15000) == InquirySession.ReturnAction.WAIT,"Never steal foreground from another app");
        String report = FlowSupport.redact("Antrean 1/2: " + a + " unknown 123456789012", q.rows());
        ok(!report.contains(a) && !report.contains("123456789012") && report.contains("1/2"), "Export removes contract identifiers but retains ordinal");
        ok(FlowSupport.timestamp(0).contains("Tidak tersedia"), "Unknown timestamps remain unknown");
        InquirySession timed = new InquirySession(1000);
        timed.move(InquirySession.Stage.OPEN_LINK, 1000);
        java.util.Map<String,Long> delta = timed.drainDurations(1300);
        ok(delta.get("OPEN_LINK") == 300L, "Initial checkpoint records only elapsed current stage");
        ok(timed.drainDurations(1300).isEmpty(), "Repeated checkpoint never counts elapsed time twice");
        timed.move(InquirySession.Stage.FILL_CONTRACT, 1700);
        timed.move(InquirySession.Stage.VERIFY_CONTRACT, 1900);
        timed.move(InquirySession.Stage.FILL_CONTRACT, 2100);
        delta = timed.drainDurations(2400);
        ok(delta.get("OPEN_LINK") == 400L && delta.get("FILL_CONTRACT") == 500L && delta.get("VERIFY_CONTRACT") == 200L, "Revisited stages retain every interval across retries");
        ok(timed.drainDurations(2500).get("FILL_CONTRACT") == 100L, "Later checkpoint adds only new time");
        BulkQueue measured = new BulkQueue(Arrays.asList(a,b), q.period); measured.startNext();
        measured.addCurrentDuration("OPEN_LINK", 1000); measured.addCurrentDuration("WAIT_RESULT", 3000);
        measured.succeed(a,"PRIVATE NAME AND AMOUNT JSON", "safe report"); measured.startNext(); measured.addCurrentDuration("WAIT_RESULT", 2000); measured.fail("blocked", "safe report");
        measured = BulkQueue.restore(measured.serialize(false));
        ok(measured.rows().get(0).stageMs().get("WAIT_RESULT") == 3000L, "Per-stage duration survives durable restore");
        ok(measured.activeMs() == 6000, "Stage sums match active session total");
        String exported = SessionReport.render(measured, Arrays.asList("SAFE technical trace " + a, "SAFE technical trace " + b));
        ok(!exported.contains(a) && !exported.contains(b), "Session export redacts identifiers from all row reports");
        ok(!exported.contains("PRIVATE NAME") && !exported.contains("AMOUNT JSON"), "Export never includes stored result JSON");
        ok(exported.contains("Rata-rata aktif: 3000 ms") && exported.contains("Median aktif: 3000"), "Report mean and even median are computed from completed measured rows");
        ok(exported.contains("Tercepat: 2000 ms") && exported.contains("Terlama: 4000 ms"), "Report fastest/slowest reflect actual measurements");
        ok(exported.contains("Tahap WAIT_RESULT: 3000 ms") && exported.contains("--- Antrean 2 / 2 ---"), "Export includes every ordinal and per-stage timing");
        String oldReport = SessionReport.render(migrated, java.util.Collections.emptyList());
        ok(oldReport.contains("Statistik waktu: Tidak tersedia") && !oldReport.contains("Rata-rata aktif: 0"), "Legacy rows never invent zero-duration averages");
        BulkQueue partial = new BulkQueue(Arrays.asList(a,b), q.period); partial.startNext(); partial.addCurrentDuration(2000); partial.succeed(a,"ok",""); partial.startNext(); partial.addCurrentDuration(9999); partial.pause("pause","");
        String partialReport = SessionReport.render(partial, java.util.Collections.emptyList());
        ok(partialReport.contains("Rata-rata aktif: 2000 ms"), "Unfinished attempts are excluded from completed-row statistics");
        ok(partialReport.contains("Waktu siklus aktif tercatat: 11999 ms"), "Session total still retains unfinished attempt time");
        System.out.println("PASS: " + checks + " v0.5.0 regression checks");
    }
}
