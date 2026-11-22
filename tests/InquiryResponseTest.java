import id.local.mufchecker.InquiryResponse;
import id.local.mufchecker.InquirySession;

/** Synthetic response/clock regressions; no Android or customer inquiry. */
public final class InquiryResponseTest {
    private static int checks;
    private static void ok(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static InquirySession submitted(){
        InquirySession session=new InquirySession(0,false);session.move(InquirySession.Stage.VERIFY_CONTRACT,0);
        session.markSubmitted(true,true,false,"090000000001","090000000001",0);session.markTapDispatched();session.markTapCompleted();return session;
    }
    public static void main(String[] args){
        usingMessages();usingClock();usingEvents();
        System.out.println("PASS: "+checks+" response/error checks (10-second quiet form, scoped messages, loading, stale events and explicit errors)");
    }
    private static void usingMessages(){
        ok(InquiryResponse.classify(null)==InquiryResponse.Signal.NONE,"Unreadable message never implies blocked/payment status");
        ok(InquiryResponse.classify("Pembayaran tidak dapat dilakukan. Silakan hubungi kantor cabang.")==InquiryResponse.Signal.RESTRICTED,"Explicit branch restriction");
        ok(InquiryResponse.classify("Transaksi tidak dapat dilakukan, hubungi cabang!")==InquiryResponse.Signal.RESTRICTED,"Restriction punctuation/wording variant");
        ok(InquiryResponse.classify("hubungi cabang")==InquiryResponse.Signal.NONE,"Branch mention alone is not a restriction");
        for(String value:new String[]{"nomor kontrak tidak ditemukan","nomor pelanggan tidak ditemukan","NOMOR KONTRAK TIDAK VALID","nomor pelanggan tidak valid"})
            ok(InquiryResponse.classify(value)==InquiryResponse.Signal.CONTRACT_NOT_FOUND,"Explicit contract-local error: "+value);
        for(String value:new String[]{"tagihan tidak ditemukan","tidak ada tagihan"})ok(InquiryResponse.classify(value)==InquiryResponse.Signal.NO_BILL,"No bill stays an unverified ERROR");
        ok(InquiryResponse.classify("Tagihan sudah dibayar")==InquiryResponse.Signal.ALREADY_PAID,"Message without verified details is never promoted to successful paid result");
        for(String value:new String[]{"terjadi kesalahan","coba lagi nanti","layanan sedang tidak tersedia","gangguan jaringan"})
            ok(!InquiryResponse.classify(value).contractError && InquiryResponse.classify(value)==InquiryResponse.Signal.SERVICE_ERROR,"Service problems retain technical failure guard");
        ok(InquiryResponse.prefer(InquiryResponse.Signal.SERVICE_ERROR,InquiryResponse.Signal.RESTRICTED)==InquiryResponse.Signal.RESTRICTED,"Explicit restriction overrides generic error title");
        ok(InquiryResponse.prefer(InquiryResponse.Signal.RESTRICTED,InquiryResponse.Signal.SERVICE_ERROR)==InquiryResponse.Signal.RESTRICTED,"Generic text never replaces recorded branch restriction");
        for(InquiryResponse.Signal signal:InquiryResponse.Signal.values())ok(!signal.reason.matches(".*[0-9]{8,20}.*"),"Only fixed, identifier-free reasons retained");
    }
    private static void usingClock(){
        InquirySession session=submitted();InquiryResponse response=new InquiryResponse();
        for(int time=0;time<10000;time+=1000)ok(!response.observeQuiet(time,session,true,false,false),"Do not skip before full quiet deadline: "+time);
        ok(!response.observeQuiet(9999,session,true,false,false),"9999 ms remains waiting");
        ok(response.observeQuiet(10000,session,true,false,false),"Exactly 10000 ms of observed quiet form becomes no response");
        ok(response.deadline()==10000,"Expose deadline to scheduler");
        ok(!response.observeQuiet(10001,session,true,true,false) && response.deadline()==-1,"Loading resets quiet timer");
        for(int time=11000;time<21000;time+=1000)ok(!response.observeQuiet(time,session,true,false,false),"Loading does not become early timeout once it clears");
        ok(response.observeQuiet(21000,session,true,false,false),"Ten new quiet seconds after loading");
        ok(!response.observeQuiet(21001,session,false,false,false),"Wrong provider/contract or unreadable form resets timer");
        ok(!response.observeQuiet(25000,session,true,false,false) && response.deadline()==35000,"Returning from background starts fresh observation interval");
        ok(!response.observeQuiet(28001,session,true,false,false) && response.deadline()==38001,"A gap over 2 seconds cannot prove a continuously idle form");
        ok(!response.observeQuiet(100,session,true,false,false) && response.deadline()==10100,"Regressing test clock resets interval");
        ok(!response.observeQuiet(101,session,true,false,true),"Checkout/result view prevents silent skip");
        session.markResultObserved();ok(!response.observeQuiet(20000,session,true,false,false),"Observed results keep normal detail/stability verification");
        InquirySession unstarted=new InquirySession(0,false);ok(!response.observeQuiet(20000,unstarted,true,false,false),"Unstarted session never skips");
        InquirySession reserved=new InquirySession(0,false);reserved.move(InquirySession.Stage.VERIFY_CONTRACT,0);reserved.markSubmitted(true,true,false,"090000000001","090000000001",0);
        ok(!response.observeQuiet(0,reserved,true,false,false),"Reserved tap is not accepted inquiry evidence");
        reserved.markTapRejected();ok(!response.observeQuiet(10000,reserved,true,false,false),"Rejected gesture cannot use silent-response skip");
        response.reset();ok(response.deadline()==-1 && response.quietAge(30000)==0,"New contract resets all quiet observations");
        ok(session.stageLimit()==25000,"Normal result/detail timeout remains 25 seconds");
    }
    private static void usingEvents(){
        String text="nomor kontrak tidak ditemukan";InquiryResponse response=new InquiryResponse();InquirySession session=submitted();
        response.observeEvent(false,true,session,1000,1100,1200,text);ok(response.pendingEvent(1200)==InquiryResponse.Signal.NONE,"Other application events ignored");
        response.observeEvent(true,false,session,1000,1100,1200,text);ok(response.pendingEvent(1200)==InquiryResponse.Signal.NONE,"Inactive queue events ignored");
        response.observeEvent(true,true,session,1000,999,1200,text);ok(response.pendingEvent(1200)==InquiryResponse.Signal.NONE,"Event from previous inquiry ignored");
        response.observeEvent(true,true,session,1000,1300,1200,text);ok(response.pendingEvent(1200)==InquiryResponse.Signal.NONE,"Future event rejected");
        response.observeEvent(true,true,session,1000,1100,6200,text);ok(response.pendingEvent(6200)==InquiryResponse.Signal.NONE,"Stale transport event rejected");
        response.observeEvent(true,true,session,1000,1100,1200,text);ok(response.pendingEvent(1200)==InquiryResponse.Signal.CONTRACT_NOT_FOUND,"Fresh post-inquiry message captured");
        ok(response.pendingEvent(6200)==InquiryResponse.Signal.CONTRACT_NOT_FOUND,"Five-second latch boundary");
        ok(response.pendingEvent(6201)==InquiryResponse.Signal.NONE,"Expired latch cannot affect a later screen");
        response.observeEvent(true,true,session,1000,9000,9000,text);ok(response.pendingEvent(9000)==InquiryResponse.Signal.CONTRACT_NOT_FOUND,"Fresh response after slow network is still recognized");
        response.reset();ok(response.pendingEvent(9001)==InquiryResponse.Signal.NONE,"No event leaks to next contract");
        session.markResultObserved();response.observeEvent(true,true,session,1000,10000,10000,text);ok(response.pendingEvent(10000)==InquiryResponse.Signal.NONE,"Messages after result observation cannot replace successful details");
    }
}
