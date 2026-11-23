import id.local.mufchecker.*;
import java.time.YearMonth;
import java.util.Arrays;

/** Meaningful scheduling and data-invalidation regressions; never makes a live inquiry. */
public final class TimingPresentationTest {
    private static int checks;
    private static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        ok(UiPresentation.eta(60000,1,10).isEmpty() && UiPresentation.eta(60000,10,10).isEmpty() && UiPresentation.eta(0,5,10).isEmpty(),"ETA waits for two finished contracts and ends at completion");
        ok("sisa ± 1m 0d".equals(UiPresentation.eta(60000,5,10)) && "sisa ± 20d".equals(UiPresentation.eta(20000,2,4)),"ETA extrapolates the average finished duration");
        ok(FlowTiming.nextItem(true,true)==300 && FlowTiming.nextItem(true,false)==0,"Ready form reuse shortens only the next-item startup wait");
        ok(FlowTiming.nextItem(false,true)==700 && FlowTiming.nextItem(false,false)==1200,"Legacy launch and return waits are preserved");
        ok(!FlowTiming.needsFocus(true,true),"Skip redundant focus only when ready-page contract input already has focus");
        ok(FlowTiming.needsFocus(true,false) && FlowTiming.needsFocus(false,true) && FlowTiming.needsFocus(false,false),"Unfocused and legacy forms retain focus settling");
        ok(FlowTiming.keyboard(false)==900 && FlowTiming.inquiry(false)==1400 && FlowTiming.details(false)==1200,"Legacy route keeps its action waits");
        ok(FlowTiming.keyboard(true)==250 && FlowTiming.details(true)==250 && FlowTiming.inquiry(true)==250,"Ready route may inspect early without allowing extra actions");
        ok(FlowTiming.nextInspection(true,1000,1000,1400,-1)==400,"Focus deadline read without an extra 700ms poll");
        ok(FlowTiming.nextInspection(true,1000,1000,1500,-1)==500,"Input deadline read at settle interval");
        ok(FlowTiming.nextInspection(true,1200,1000,1100,1400)==200,"Stable result is revisited at actual stability deadline");
        ok(FlowTiming.nextInspection(true,1300,1300,1300,1400)==250,"Event bursts cannot bypass minimum inspection spacing");
        ok(FlowTiming.nextInspection(false,1200,1000,1300,1400)==700,"Direct-link polling stays unchanged");
        ok(FlowTiming.nextInspection(true,2000,1900,0,-1)==700,"Loading pages retain bounded fallback polling");
        for(long now=1000;now<2000;now+=31){long delay=FlowTiming.nextInspection(true,now,1000,1400,1600);
            ok(delay>=0 && now+delay>=1250,"No negative or excessive-frequency deadline at "+now);}
        ok(!FlowTiming.maySearchDetails(true,true,0) && !FlowTiming.maySearchDetails(true,true,1199),"Do not scroll while an expanded detail sheet is still loading");
        ok(FlowTiming.maySearchDetails(true,true,1200) && FlowTiming.maySearchDetails(true,false,0),"Search becomes available after settle or before expanding");
        ok(FlowTiming.maySearchDetails(false,true,0),"Legacy detail-search policy preserved");
        ok(!FlowTiming.mayVerifyDetails(true,true,250,false),"Do not finalize a partial early detail frame");
        ok(FlowTiming.mayVerifyDetails(true,true,250,true),"Complete details can enter normal stability verification early");
        ok(FlowTiming.mayVerifyDetails(true,true,1200,false),"Original settle-window compatibility retained if extra labels are absent");
        java.util.Map<String,String> partial=new java.util.HashMap<>();partial.put("nama","DEMO** USER***");
        ok(!BillReader.earlyDetailsComplete(partial),"Name without installment amount is not complete for early capture");
        partial.put("tagihan","Rp1.250.000");ok(BillReader.earlyDetailsComplete(partial),"Visible masked name and amount are eligible");
        partial.put("tagihan","[AMBIGU]");ok(!BillReader.earlyDetailsComplete(partial),"Ambiguous extra value does not accelerate capture");
        ResultStability stable=new ResultStability();
        ok(stable.deadline()==-1&&!stable.observe("verified fields A",1000),"First verified snapshot does not finish result");
        ok(stable.deadline()==1400&&!stable.observe("verified fields A",1000),"Repeated same timestamp is not a fresh snapshot");
        ok(!stable.observe("verified fields A",1250),"Two fresh reads less than 400ms apart are insufficient");
        ok(stable.observe("verified fields A",1400),"Matching verified reads separated by 400ms pass");
        ok(!stable.observe("verified fields B",1450)&&stable.deadline()==1850,"A changing amount or field restarts stability");
        stable.reset();ok(!stable.observe("verified fields B",2000),"Background/invalid-identity reset cannot reuse previous observation");
        ok(stable.observe("verified fields B",2400),"Two new stable reads are required after reset");
        ok(!stable.observe("verified fields B",2200)&&stable.deadline()==2600,"Backward clock value resets observation window");
        ok(!stable.observe("",3000)&&stable.deadline()==-1,"Empty result resets readiness");
        String a="090000000001",b="090000000002";
        BulkQueue q=new BulkQueue(Arrays.asList(a,b),YearMonth.of(2026,10),BulkQueue.StartMode.READY_PAGE);
        String key=UiPresentation.tableKey(q);q.addCurrentDuration(30000);
        ok(key.equals(UiPresentation.tableKey(q)),"Preparation timer does not redraw rows");
        q.startPrepared();q.startNext();String processing=UiPresentation.tableKey(q);
        ok(!key.equals(processing),"Starting a row updates its displayed state");
        q.addCurrentDuration("FILL_CONTRACT",5000);q.addCurrentDuration("WAIT_RESULT",5000);
        ok(processing.equals(UiPresentation.tableKey(q)),"Duration-only checkpoints do not rebuild the table");
        q=BulkQueue.restore(q.serialize(false));
        ok(processing.equals(UiPresentation.tableKey(q)),"Freshly deserialized snapshot with same content does not rebuild rows");
        q.succeed(a,"verified-json-a","safe");
        ok(!processing.equals(UiPresentation.tableKey(q)),"New result invalidates rows immediately");
        q.startNext();q.fail("technical-error","safe");String failed=UiPresentation.tableKey(q);q.retryErrors();
        ok(!failed.equals(UiPresentation.tableKey(q)),"Retry error visibly changes rows back to waiting");
        BulkQueue another=new BulkQueue(Arrays.asList(a,b),q.period,BulkQueue.StartMode.READY_PAGE);
        ok(!UiPresentation.tableKey(q).equals(UiPresentation.tableKey(another)),"Switching sessions invalidates the table even for same contracts");
        ok(UiPresentation.panel(true,true,0,17,null,false).equals("MUF · 17 kontrak"),"Ready panel contains only queue size and actions");
        ok(UiPresentation.panel(true,false,0,17,null,false).equals("Menunggu form"),"Pending panel is concise");
        ok(UiPresentation.panel(false,false,6,17,InquirySession.Stage.READ_DETAILS,false).equals("7/17 · Membaca"),"Read-details state is short");
        ok(UiPresentation.panel(false,false,6,17,InquirySession.Stage.WAIT_RESULT,false).equals("7/17 · Memeriksa"),"Waiting state does not expose gesture debugging");
        ok(UiPresentation.panel(false,false,6,17,InquirySession.Stage.WAIT_RESULT,true).equals("7/17 · Menyelesaikan"),"Requested pause retains clear status");
        ok(UiPresentation.duration(229898).equals("3m 49d") && UiPresentation.duration(-1).equals("0d"),"Duration display follows recorded milliseconds");
        for(InquirySession.Stage stage:InquirySession.Stage.values()){
            String text=UiPresentation.panel(false,false,99,100,stage,false);
            ok(text.startsWith("100/100 · ") && text.length()<30,"Compact state fits one line for "+stage);}
        InquirySession f=new InquirySession(0,false);f.move(InquirySession.Stage.VERIFY_CONTRACT,0);
        ok(!f.markSubmitted(true,true,false,b,a,250),"Earlier inspection cannot submit a retained previous number");
        ok(f.markSubmitted(true,true,false,a,a,500),"Verified number can submit once after early inspection");
        ok(f.markTapDispatched() && !f.markSubmitted(true,true,false,a,a,750),"Faster result polling never allows another inquiry tap");
        ok(f.markDetailsOpened(1000) && !f.markDetailsOpened(1250),"Earlier detail reads never toggle the same details twice");
        ok(f.stageLimit()==25000 && InquirySession.SESSION_LIMIT==120000,"Timeout protection is unchanged");
        ok(UiPresentation.matchesResult("Semua", "  demo**  ", a, "DEMO** USER***", "SUDAH BAYAR"), "Masked names stay searchable with case/space normalization");
        ok(UiPresentation.matchesResult("Sudah", "090000000001", a, "DEMO**", "SUDAH BAYAR"), "Exact contract remains searchable including leading zero");
        ok(!UiPresentation.matchesResult("Error", "", a, "DEMO**", "SUDAH BAYAR"), "Filter and search combine without leaking success into errors");
        ok(UiPresentation.matchesResult("Error", "", b, null, "ERROR"), "Blocked/error rows remain visible without result fields");
        ok(!UiPresentation.matchesResult("Belum", "", b, "", "MENUNGGU"), "Waiting is distinct from unpaid");
        ok(!UiPresentation.matchesResult("Semua", "unknown", b, "DEMO**", "BELUM BAYAR"), "No-match search returns empty");
        java.util.Set<String> labels=new java.util.HashSet<>();
        for(String status:new String[]{"SUDAH BAYAR","BELUM BAYAR","ERROR","DIPROSES","MENUNGGU"}) {
            String label=UiPresentation.statusLabel(status);
            ok(!label.isEmpty() && labels.add(label), "Each status has a unique text label independent of color: "+status);
        }
        ok(UiPresentation.statusLabel(null).equals("! Error"), "Unknown status never appears paid");
        String[][] tableLabels={{"SUDAH BAYAR","Bayar"},{"BELUM BAYAR","Belum"},{"ERROR","Error"},{"DIPROSES","Proses"},{"MENUNGGU","Tunggu"}};
        for(String[] pair:tableLabels)ok(pair[1].equals(UiPresentation.tableStatusLabel(pair[0])),"Table keeps distinct meaning for "+pair[0]);
        ok("Error".equals(UiPresentation.tableStatusLabel(null)) && "Error".equals(UiPresentation.tableStatusLabel("UNKNOWN")),"Unknown table status never becomes paid or unpaid");
        System.out.println("PASS: "+checks+" timing/presentation checks");
    }
}
