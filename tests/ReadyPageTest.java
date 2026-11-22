import id.local.mufchecker.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** Offline form fixtures and real queue/session/gate rules. No live customer inquiries. */
public final class ReadyPageTest {
    private static int checks;
    private static void ok(boolean v, String message) { checks++; if (!v) throw new AssertionError(message); }
    private static UiRules.Node label(String text, int top, int bottom) {
        return new UiRules.Node(text, "", "", "", "", 40, top, 820, bottom, false);
    }
    public static void main(String[] args) {
        String a = "090000000001", b = "090000000002";
        YearMonth period = YearMonth.of(2026,10);
        ReadyPageGate gate = new ReadyPageGate();
        ok(!gate.canStart(0) && !gate.consume(0), "No start before any observed form");
        gate.observe(true,1000);
        ok(!gate.canStart(1500), "One snapshot alone never enables Start");
        gate.observe(true,1100);
        ok(!gate.consume(1100), "Two immediate frames cannot bypass settle interval");
        gate.observe(true,1300);
        ok(gate.canStart(1300), "Two stable fresh observations enable Start");
        gate.observe(false,1301);
        ok(!gate.consume(1301), "Changed page/wrong provider invalidates an enabled Start");
        gate.observe(true,2000); gate.observe(true,2400);
        long staleAt=2400+ReadyPageGate.FRESH_MS+1;
        ok(!gate.consume(staleAt), "An old enabled Start cannot use a stale snapshot");
        gate.observe(true,staleAt+100);
        ok(!gate.consume(staleAt+100), "Stale interval requires two new observations");
        gate.observe(true,staleAt+400);
        ok(gate.consume(staleAt+400) && !gate.consume(staleAt+401), "Explicit Start consumed only once");
        gate.observe(true,staleAt+800);
        ok(!gate.canStart(staleAt+800), "Late events cannot re-arm a consumed button");
        gate.reset(); gate.observe(true,10000); gate.observe(true,12500);
        ok(gate.canStart(12500), "A slow accessibility scan can still prepare the form; click performs a fresh read");
        gate.reset(); gate.observe(true,6000); gate.observe(true,6300);
        ok(gate.canStart(6300) && !gate.canStart(6200), "Resume resets gate and rejects time reversal");
        for (int mask=0; mask<128; mask++) {
            boolean foreground=(mask&1)!=0, title=(mask&2)!=0, provider=(mask&4)!=0, input=(mask&8)!=0;
            boolean result=(mask&16)!=0, blocked=(mask&32)!=0, list=(mask&64)!=0;
            ok(ReadyPageGate.formReady(foreground,title,provider,input,result,blocked,list) == (mask==15),
                "Only the verified foreground MUF input form is eligible, mask="+mask);
        }
        List<UiRules.Node> form = new ArrayList<>(Arrays.asList(label("Angsuran Kredit",120,180),
            label("Penyedia Layanan",260,300), label("Mandiri Utama Finance",350,400),
            label("Nomor Kontrak / Virtual Account",450,490),
            new UiRules.Node(a,"","","","",40,510,820,560,true),
            label("Promo",800,840), label("Transaksi & Post Ceritamu",900,950)));
        ok(UiRules.selectedMuf(form) && UiRules.contractField(form)==4, "Screenshot-style native form identifies selected MUF and labelled contract");
        form.set(2,label("Kredivo",350,400));
        form.add(label("Mandiri Utama Finance",1000,1050));
        ok(!UiRules.selectedMuf(form), "MUF promo elsewhere cannot override selected other provider");
        List<TopupMatcher.Node> buttons = Arrays.asList(
            new TopupMatcher.Node("","",-1,0,0,920,2048,true,true,false),
            new TopupMatcher.Node("+ Bayar Lainn...","",0,100,600,440,700,true,true,true),
            new TopupMatcher.Node("Bayar","",0,480,600,820,700,true,true,true),
            new TopupMatcher.Node("Salin","",0,700,1000,820,1080,true,true,true));
        UiRules.ButtonMatch match=UiRules.inquiryAction(buttons,920,2048);
        ok(match.gestureTarget==2 && match.count==1, "Bayar is unique; Bayar Lainnya and Salin never become inquiry targets");
        List<TopupMatcher.Node> disabled=new ArrayList<>(buttons);
        disabled.set(2,new TopupMatcher.Node("Bayar","",0,480,600,820,700,true,false,true));
        ok(UiRules.inquiryAction(disabled,920,2048).gestureTarget<0, "Disabled Bayar cannot be tapped");
        List<TopupMatcher.Node> ambiguous=new ArrayList<>(buttons);
        ambiguous.add(new TopupMatcher.Node("Bayar","",0,480,750,820,850,true,true,true));
        ok(UiRules.inquiryAction(ambiguous,920,2048).gestureTarget<0, "Multiple Bayar buttons block a tap");

        BulkQueue q=new BulkQueue(Arrays.asList(a,b),period,BulkQueue.StartMode.READY_PAGE);
        ok(q.state()==BulkQueue.RunState.PREPARED && q.current()==null && q.startedAt()==0, "Preparation starts no contract and no active timer");
        q.addCurrentDuration("WAIT_RESULT",25000);
        ok(q.startNext()==null && q.activeMs()==0, "Watcher cannot accidentally start queue rows");
        q=BulkQueue.restore(q.serialize(false));
        ok(q.startMode()==BulkQueue.StartMode.READY_PAGE && q.state()==BulkQueue.RunState.PREPARED, "Mode and prepared state survive durable checkpoint");
        ok(!q.recoverInterrupted() && !q.resume(), "Restart never starts a prepared queue through legacy resume");
        ok(q.prepareReadyPage() && q.startedAt()==0 && q.activeMs()==0, "Explicit re-arm retains zero active time");
        ok(q.startPrepared() && !q.startPrepared(), "Queue begins only once from explicit floating Start");
        long started=q.startedAt();
        ok(started>=q.createdAt(), "Actual Start timestamp recorded after preparation");
        ok(q.startNext().contract.equals(a) && q.startNext()==null, "Exactly one active contract");
        InquirySession flow=new InquirySession(10000,false);
        flow.move(InquirySession.Stage.FILL_CONTRACT,10000);
        ok(flow.markInputAttempt(), "First replacement allowed on verified form");
        flow.move(InquirySession.Stage.VERIFY_CONTRACT,10100);
        ok(!flow.markSubmitted(true,true,false,a,b,10500), "Old retained contract cannot be submitted for next row");
        ok(!flow.retryEmptyInput("",11999), "Wait for asynchronous input before empty retry");
        ok(flow.retryEmptyInput("",12100) && flow.markInputAttempt(), "One bounded second fill after confirmed empty input");
        flow.move(InquirySession.Stage.VERIFY_CONTRACT,12200);
        ok(!flow.retryEmptyInput("",15000) && !flow.markSubmitted(false,true,false,a,a,15000), "No third fill; wrong provider blocks inquiry");
        ok(flow.markSubmitted(true,true,false,a,a,15000), "Exact number plus MUF permits inquiry");
        ok(flow.markTapDispatched() && flow.markTapCompleted() && flow.markResultObserved(), "Accepted single tap then fresh results");
        ok(!flow.markSubmitted(true,true,false,a,a,16000), "No repeat tap after successful dispatch");
        Map<String,String> fields=new LinkedHashMap<>(); fields.put("nomor pelanggan",a); fields.put("jatuh tempo","02-NOV-26");
        ok(BillReader.verifiedDue(fields,a).equals(LocalDate.of(2026,11,2)), "Existing identity/date verification preserved for ready mode");
        fields.put("nomor pelanggan",b);
        boolean rejected=false;
        try { BillReader.verifiedDue(fields,a); } catch(IllegalArgumentException e) { rejected=true; }
        ok(rejected,"A different contract result is never recorded as current success");
        q.addCurrentDuration("WAIT_RESULT",5000); q.succeed(a,"verified-result","safe");
        q=BulkQueue.restore(q.serialize(false));
        ok(q.rows().get(0).state()==BulkQueue.RowState.SUCCESS && q.rows().get(0).result().equals("verified-result"), "Success survives checkpoint before next contract");
        ok(q.startNext().contract.equals(b), "Next row begins sequentially after persisted success");
        q.pause("form missing","safe");
        q=BulkQueue.restore(q.serialize(false));
        ok(q.prepareReadyPage() && q.startNext()==null, "Paused ready queue must re-arm without inquiry");
        ok(q.startPrepared() && q.startedAt()==started && q.startNext().contract.equals(b), "Resume preserves first Start time and skips previous success");
        q.fail("Pembayaran dibatasi — hubungi kantor cabang.","safe");
        ok(q.state()==BulkQueue.RunState.COMPLETED && q.rows().get(0).result().equals("verified-result"), "Business error can complete queue without losing success");
        ok(q.retryErrors() && !q.resume() && q.prepareReadyPage() && q.startPrepared(), "Retry ERROR requires new preparation and floating Start");
        ok(q.startNext().contract.equals(b), "Explicit retry repeats only error row");
        q.succeed(b,"second-result","safe");
        String report=SessionReport.render(q,Arrays.asList("safe","safe"));
        ok(report.contains("HALAMAN_TOKOPEDIA_SIAP") && report.contains("Waktu persiapan awal:"), "Export distinguishes route and preparation time");
        ok(!report.contains(a) && !report.contains(b), "Ready-page report contains no contract identifiers");
        ok(new DiagnosticTrace(true).render().contains("HALAMAN_TOKOPEDIA_SIAP"), "Per-row trace reports actual route");
        BulkQueue stopped=new BulkQueue(Arrays.asList(a),period,BulkQueue.StartMode.READY_PAGE);
        stopped.stop("cancel","");
        ok(!stopped.startPrepared() && stopped.activeMs()==0 && stopped.current()==null, "Cancel before Start has no active contract or time");

        InquirySession back=new InquirySession(0,false); back.move(InquirySession.Stage.RETURN_FORM,0);
        ok(back.returnAction(false,true,false,700)==InquirySession.ReturnAction.BACK,"One Back to previous form");
        ok(back.returnAction(false,true,false,701)==InquirySession.ReturnAction.WAIT,"No repeated Back while transition is loading");
        ok(back.returnAction(true,true,false,800)==InquirySession.ReturnAction.USE_FORM,"Use ready form immediately instead of fixed recovery delay");
        ok(back.returnAction(false,true,false,6700)==InquirySession.ReturnAction.PAUSE,"Missing form pauses ready mode instead of opening old link");
        ok(back.returnAction(false,true,false,20000)==InquirySession.ReturnAction.WAIT,"No repeated recovery action");
        InquirySession background=new InquirySession(0,false); background.move(InquirySession.Stage.RETURN_FORM,0);
        ok(background.returnAction(false,false,false,10000)==InquirySession.ReturnAction.WAIT,"No Back when another app is foreground");

        for(int size:new int[]{5,20,30,50,100}) {
            List<String> numbers=new ArrayList<>(); for(int i=0;i<size;i++) numbers.add(String.format(Locale.ROOT,"09000000%04d",i));
            BulkQueue bulk=new BulkQueue(numbers,period,BulkQueue.StartMode.READY_PAGE);
            String id=bulk.sessionId(); long created=bulk.createdAt();
            ok(bulk.startPrepared(),"Explicit start for "+size+" contracts");
            for(int i=0;i<size;i++) {
                ok(bulk.startNext().contract.equals(numbers.get(i)),"Sequential row "+i+" of "+size);
                if (size==100 && i==73) {
                    bulk=BulkQueue.restore(bulk.serialize(false));
                    ok(bulk.recoverInterrupted() && bulk.completedCount()==73,"Interrupted 100-row queue retains all prior results");
                    ok(!bulk.resume() && bulk.prepareReadyPage() && bulk.startPrepared(),"Recovery still requires explicit floating Start");
                    ok(bulk.startNext().contract.equals(numbers.get(i)),"Only interrupted row resumes; no skipped or repeated successes");
                }
                bulk.addCurrentDuration("WAIT_RESULT",1000); bulk.succeed(numbers.get(i),"verified-"+i,"safe");
                bulk=BulkQueue.restore(bulk.serialize(false));
                ok(bulk.completedCount()==i+1 && bulk.startMode()==BulkQueue.StartMode.READY_PAGE,"Checkpoint preserves count/mode for "+i);
                ok(bulk.sessionId().equals(id) && bulk.createdAt()==created && bulk.period.equals(period),"Session identity and period survive every checkpoint");
                ok(bulk.rows().get(i).stageMs().get("WAIT_RESULT")==1000L,"Stage timing remains attached to the correct row");
            }
            ok(bulk.state()==BulkQueue.RunState.COMPLETED && bulk.activeMs()==size*1000L,"Completed queue timing for "+size);
        }
        BulkQueue legacy=new BulkQueue(Arrays.asList(a),period);
        String old=legacy.serialize(false).replaceAll("(?m)^(startMode|startedAt)=.*\\R?", "");
        legacy=BulkQueue.restore(old);
        ok(legacy.startMode()==BulkQueue.StartMode.DIRECT_LINK && legacy.startedAt()==legacy.createdAt(),"Old sessions retain direct-link semantics and timing");
        System.out.println("PASS: "+checks+" ready-page checks");
    }
}
