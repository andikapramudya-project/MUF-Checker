import id.local.mufchecker.*;
import java.time.YearMonth;
import java.util.*;

/** Focused-input probes cannot submit. Fresh full form/result rules remain authoritative. */
public final class InputVerificationTest {
    private static int checks;
    private static final String A="090000000001", B="090000000002", PKG="com.tokopedia.tkpd";
    private static final InquirySession.Stage VERIFY=InquirySession.Stage.VERIFY_CONTRACT;
    private static void ok(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static InputVerification.Field field(String value,String pkg,String id,String cls,int window,
                                                  int left,boolean visible,boolean enabled,boolean focused,boolean writable){
        return new InputVerification.Field(value,pkg,id,cls,window,left,100,300,160,visible,enabled,focused,writable);
    }
    private static InputVerification.Field field(String value){return field(value,PKG,"","android.widget.EditText",7,20,true,true,true,true);}
    private static void fallback(InputVerification probe,InputVerification.Field actual,String message){
        ok(probe.decide(1250,7,true,actual)==InputVerification.Action.WAIT,message+" preserves the original settling deadline");
        ok(probe.decide(1500,7,true,actual)==InputVerification.Action.FULL_READ,message+" falls back to full verification");
    }
    public static void main(String[] args){
        InputVerification probe=new InputVerification();
        ok(!probe.eligible(true,VERIFY,false,1,false,A),"No shortcut without an accepted write from a verified form");
        ok(probe.decide(1250,7,true,field(A))==InputVerification.Action.FULL_READ,"An unarmed probe never closes a keyboard");
        ok(probe.arm(field(A),1000) && probe.fullReadAt()==1500,"Accepted write retains the original full-read deadline");
        ok(probe.eligible(true,VERIFY,false,1,false,A),"One accepted write permits a focused probe");
        ok(probe.eligible(true,VERIFY,false,2,false,A),"A bounded second write also requires a fresh probe");
        ok(!probe.eligible(false,VERIFY,false,1,false,A),"Direct-link mode keeps its original verification route");
        ok(!probe.eligible(true,VERIFY,true,1,false,A),"No keyboard shortcut after submitting an inquiry");
        ok(!probe.eligible(true,VERIFY,false,1,true,A),"Do not send another Back after hiding the keyboard");
        ok(!probe.eligible(true,VERIFY,false,0,false,A) && !probe.eligible(true,VERIFY,false,3,false,A),"Input attempt bounds still apply");
        ok(!probe.eligible(true,VERIFY,false,1,false,B),"A receipt from a previous contract cannot authorize this row");
        for(InquirySession.Stage stage:InquirySession.Stage.values()) if(stage!=VERIFY)
            ok(!probe.eligible(true,stage,false,1,false,A),"No probe in stage "+stage);
        ok(probe.decide(1249,7,true,field(A))==InputVerification.Action.WAIT,"Do not act before 250ms even if text matches");
        ok(probe.decide(1250,7,true,field(A))==InputVerification.Action.CLOSE_KEYBOARD,"A fresh matching focused input may hide IME at 250ms");
        ok(probe.decide(1250,7,false,field(A))==InputVerification.Action.WAIT,"Without an IME retain 500ms full-read settling");
        ok(probe.decide(1500,7,false,field(A))==InputVerification.Action.FULL_READ,"No Back is sent when IME is absent");
        ok(probe.decide(999,7,true,field(A))==InputVerification.Action.FULL_READ,"Backward time invalidates the shortcut");
        fallback(probe,null,"Missing focus");fallback(probe,field(""),"Empty field");fallback(probe,field(B),"Previous contract");
        fallback(probe,field("90000000001"),"Missing leading zero");fallback(probe,field(A+" "),"Trailing whitespace is not an exact match");
        fallback(probe,field(A,"other.app","","android.widget.EditText",7,20,true,true,true,true),"Wrong package");
        fallback(probe,field(A,PKG,"search","android.widget.EditText",7,20,true,true,true,true),"Different view ID");
        fallback(probe,field(A,PKG,"","android.view.View",7,20,true,true,true,true),"Different class");
        fallback(probe,field(A,PKG,"","android.widget.EditText",8,20,true,true,true,true),"Different input window");
        fallback(probe,field(A,PKG,"","android.widget.EditText",7,30,true,true,true,true),"Moved input bounds");
        fallback(probe,field(A,PKG,"","android.widget.EditText",7,20,false,true,true,true),"Invisible input");
        fallback(probe,field(A,PKG,"","android.widget.EditText",7,20,true,false,true,true),"Disabled input");
        fallback(probe,field(A,PKG,"","android.widget.EditText",7,20,true,true,false,true),"Input lost focus");
        fallback(probe,field(A,PKG,"","android.widget.EditText",7,20,true,true,true,false),"Non-writable input");
        ok(probe.decide(1250,8,true,field(A))==InputVerification.Action.WAIT,"A changed root window cannot reuse the target");
        ok(probe.decide(1500,8,true,field(A))==InputVerification.Action.FULL_READ,"A changed root window gets a complete read");
        probe.clear();ok(!probe.eligible(true,VERIFY,false,1,false,A),"Stop/new-row reset removes target identity");
        ok(!probe.arm(null,1000) && !probe.arm(field("bad"),1000),"Unusable target cannot enable an early read");
        ok(!probe.arm(field(A,"other.app","","android.widget.EditText",7,20,true,true,true,true),1000),"A foreign target cannot be armed");
        ok(!probe.arm(field(A,PKG,"","android.widget.EditText",7,300,true,true,true,true),1000),"Zero-size target cannot be armed");
        verifyFullGuards(probe);
        System.out.println("PASS: "+checks+" focused-input verification checks");
    }
    private static void verifyFullGuards(InputVerification probe){
        InquirySession session=new InquirySession(0,false);session.move(InquirySession.Stage.FILL_CONTRACT,1000);
        ok(session.markInputAttempt(),"Normal focus/write accounting remains in use");session.move(VERIFY,1000);
        probe.arm(field(A),1000);
        ok(probe.decide(1250,7,true,field(A))==InputVerification.Action.CLOSE_KEYBOARD && !session.submitted(),"Matched probe alone never reserves an inquiry");
        ok(!session.markSubmitted(false,true,false,A,A,1500),"Provider changed after typing: no inquiry");
        ok(!session.markSubmitted(true,false,false,A,A,1500),"Missing/ambiguous contract input: no inquiry");
        ok(!session.markSubmitted(true,true,true,A,A,1500),"Old checkout cannot be used as the input form");
        ok(!session.markSubmitted(true,true,false,A,B,1500),"Text changed after hiding IME: no inquiry");
        ok(!session.markSubmitted(true,true,false,A,"",1500),"Tokopedia cleared input after IME: no inquiry");
        ok(!session.retryEmptyInput("",2999) && session.retryEmptyInput("",3000),"Original empty-input grace interval is retained");
        probe.clear();ok(session.markInputAttempt(),"An empty input still has one bounded refill");
        session.move(VERIFY,3000);probe.arm(field(A),3000);
        ok(probe.decide(3250,7,true,field(A))==InputVerification.Action.CLOSE_KEYBOARD,"Refill must verify fresh text before hiding IME");
        ok(session.markSubmitted(true,true,false,A,A,3500) && session.markTapDispatched(),"Only full provider/form/number verification allows one inquiry");
        ok(!session.markSubmitted(true,true,false,A,A,3501),"A repeated event cannot send another inquiry");
        ResultStability stable=new ResultStability();
        ok(!stable.observe("verified-fields",4000) && !stable.observe("verified-fields",4399),"Focused-input changes do not shorten result stability");
        ok(stable.observe("verified-fields",4400),"Two full result reads still require 400ms");
        BulkQueue queue=new BulkQueue(Arrays.asList(A,B),YearMonth.of(2026,10),BulkQueue.StartMode.READY_PAGE);
        queue.startPrepared();queue.startNext();queue.succeed(A,"EXACT MASKED RESULT","safe");
        queue=BulkQueue.restore(queue.serialize(false));queue.startNext();queue.pause("interrupted","");
        ok(queue.rows().get(0).result().equals("EXACT MASKED RESULT") && queue.completedCount()==1,"A probe interruption preserves the committed result");
        ok(queue.prepareReadyPage() && queue.startPrepared() && queue.startNext().contract.equals(B),"Resume starts only the unfinished row after explicit Start");
        probe.clear();ok(!probe.eligible(true,VERIFY,false,1,false,B),"The resumed row cannot reuse a previous focused target");
    }
}
