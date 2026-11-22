package id.local.mufchecker.smoke;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import id.local.mufchecker.BulkQueue;
import java.lang.reflect.Method;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Separate, signed test APK. Synthetic data only; never included in the product APK. */
public final class UiSmokeInstrumentation extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments=args; start(); }
    private Object invoke(Object receiver, String name, Class<?>[] types, Object... args) throws Exception {
        Method method=receiver.getClass().getDeclaredMethod(name,types); method.setAccessible(true);
        return method.invoke(receiver,args);
    }
    private void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private Object field(Object object, String name) throws Exception {
        java.lang.reflect.Field field=object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private void setField(Object object, String name, Object value) throws Exception {
        java.lang.reflect.Field field=object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object,value);
    }
    private void waitCount(Activity activity, int expected) throws Exception {
        long deadline=android.os.SystemClock.uptimeMillis()+8000;
        AtomicBoolean ready=new AtomicBoolean();
        while(android.os.SystemClock.uptimeMillis()<deadline) {
            runOnMainSync(() -> { try { ready.set(((android.widget.BaseAdapter)field(activity,"resultAdapter")).getCount()==expected); } catch(Exception e) { throw new RuntimeException(e); } });
            if(ready.get()) return;
            android.os.SystemClock.sleep(80);
        }
        throw new AssertionError("Expected filtered rows: "+expected);
    }
    private void checkBounds(android.view.ViewGroup group) {
        for(int i=0;i<group.getChildCount();i++) {
            android.view.View child=group.getChildAt(i);
            check(child.getLeft()>=0 && child.getRight()<=group.getWidth() && child.getTop()>=0 && child.getBottom()<=group.getHeight(),"Child stays inside measured container");
            if(child instanceof android.widget.TextView) {
                android.text.Layout layout=((android.widget.TextView)child).getLayout();
                check(layout!=null,"Text has a measured layout");
                for(int line=0;line<layout.getLineCount();line++)check(layout.getEllipsisCount(line)==0,"Text remains complete at chosen font and width");
            }
            for(int j=0;j<i;j++) {
                android.view.View previous=group.getChildAt(j);
                check(!android.graphics.Rect.intersects(new android.graphics.Rect(child.getLeft(),child.getTop(),child.getRight(),child.getBottom()),
                    new android.graphics.Rect(previous.getLeft(),previous.getTop(),previous.getRight(),previous.getBottom())),"Row/action children do not overlap");
            }
        }
    }
    private void checkResultLayout(Activity activity) throws Exception {
        android.widget.BaseAdapter adapter=(android.widget.BaseAdapter)field(activity,"resultAdapter");
        android.widget.ListView list=(android.widget.ListView)field(activity,"resultList");
        android.view.ViewGroup heading=(android.view.ViewGroup)field(activity,"tableHeading");
        float density=activity.getResources().getDisplayMetrics().density;
        android.view.View recycled=null;
        for(int widthDp:new int[]{280,320,375,407,800})for(int index:new int[]{0,1,2,99}) {
            android.view.View row=adapter.getView(index,recycled,list);
            if(recycled!=null)check(row==recycled,"Table reuses the same cells across paid/unpaid/error and widths");
            recycled=row;int width=Math.round(widthDp*density);
            int widthSpec=android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY);
            int heightSpec=android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED);
            row.measure(widthSpec,heightSpec);row.layout(0,0,width,row.getMeasuredHeight());
            heading.measure(widthSpec,heightSpec);heading.layout(0,0,width,heading.getMeasuredHeight());
            android.view.ViewGroup cells=(android.view.ViewGroup)field(row,"cells"),heads=(android.view.ViewGroup)field(heading,"cells");
            checkBounds(cells);checkBounds(heads);
            check(cells.getChildCount()==4,"Exactly four spreadsheet columns");
            for(int i=0;i<4;i++) {
                android.view.View cell=cells.getChildAt(i),head=heads.getChildAt(i);
                check(cell.getLeft()==head.getLeft() && cell.getRight()==head.getRight(),"Header and row columns stay aligned");
                android.widget.TextView text=(android.widget.TextView)cell;
                check(text.getLineCount()==1,"Every result cell remains exactly one line");
                check(text.getPaint().measureText(text.getText().toString())<=text.getWidth()-text.getPaddingLeft()-text.getPaddingRight(),"Full identity fits its natural-width column");
                check(text.getLayout().getLineEnd(text.getLayout().getLineCount()-1)==text.length(),"Every contract/name/status character is laid out");
            }
            android.widget.TextView ordinal=(android.widget.TextView)field(row,"ordinal"),number=(android.widget.TextView)field(row,"number");
            android.widget.TextView name=(android.widget.TextView)field(row,"name"),status=(android.widget.TextView)field(row,"badge");
            check(number.getText().length()==20 && number.getText().charAt(0)=='0',"Twenty-digit contract and leading zero retained");
            check(ordinal.getRight()==number.getLeft() && number.getRight()==name.getLeft() && name.getRight()==status.getLeft(),"No/Contract/Name/Status are distinct adjacent columns");
            check((index==1?"Belum":index==2?"Error":"Bayar").contentEquals(status.getText()),"Unpaid and restrictions keep their own meaning in the table");
            check(row.getMeasuredHeight()>=Math.round(56*density),"Whole result row remains a usable touch target");
            int end=Math.max(0,cells.getWidth()-row.getWidth());row.scrollTo(end,0);
            check(row.getScrollX()==end && heading.getScrollX()==end,"Horizontal offset synchronizes with heading");
            check(status.getRight()-row.getScrollX()<=row.getWidth(),"Status is reachable at horizontal end");
            row.scrollTo(0,0);
        }
        android.widget.TextView session=(android.widget.TextView)field(activity,"sessionSelect");
        check(!session.getText().toString().contains("⌄"),"Session selector has no chevron");
        check(!((android.widget.TextView)field(activity,"modeButton")).getText().toString().contains("⌄"),"Mode has no chevron");
        check("Pilih sesi".contentEquals(((android.widget.TextView)field(activity,"diagnosisChoice")).getText()),"Diagnosis selector stays compact");
        check(!((android.widget.TextView)field(activity,"summary")).getText().toString().contains(" m "),"Duration uses compact units");
    }
    private void waitProductWindow(Context context) {
        long deadline=android.os.SystemClock.uptimeMillis()+5000;
        while(android.os.SystemClock.uptimeMillis()<deadline){
            android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            if(root!=null){boolean own=context.getPackageName().contentEquals(root.getPackageName());root.recycle();if(own)return;}
            android.os.SystemClock.sleep(100);
        }
        throw new AssertionError("Screenshot must show the product, without system overlays");
    }
    private void waitText(String text) {
        long deadline=android.os.SystemClock.uptimeMillis()+8000;
        while(android.os.SystemClock.uptimeMillis()<deadline){
            android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            if(root!=null && !root.findAccessibilityNodeInfosByText(text).isEmpty())return;
            android.os.SystemClock.sleep(80);
        }
        throw new AssertionError("Expected visible text: "+text);
    }
    private void swipe(float x1,float y1,float x2,float y2) {
        long down=android.os.SystemClock.uptimeMillis();
        for(int i=0;i<=16;i++){
            long now=android.os.SystemClock.uptimeMillis();float fraction=i/16f;
            android.view.MotionEvent event=android.view.MotionEvent.obtain(down,now,i==0?android.view.MotionEvent.ACTION_DOWN:i==16?android.view.MotionEvent.ACTION_UP:android.view.MotionEvent.ACTION_MOVE,x1+(x2-x1)*fraction,y1+(y2-y1)*fraction,0);
            sendPointerSync(event);event.recycle();android.os.SystemClock.sleep(20);
        }
        android.os.SystemClock.sleep(500);waitForIdleSync();
    }
    private android.view.ViewGroup floatingPreview(Activity activity,boolean preparing,String label) throws Exception {
        Class<?> type=Class.forName("id.local.mufchecker.FloatingPanel",true,getTargetContext().getClassLoader());
        java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructor(Context.class,boolean.class,String.class);constructor.setAccessible(true);
        return (android.view.ViewGroup)constructor.newInstance(activity,preparing,label);
    }
    private void checkFloating(Activity activity) throws Exception {
        float density=activity.getResources().getDisplayMetrics().density;
        for(boolean preparing:new boolean[]{true,false})for(int widthDp:new int[]{280,320,375,407}) {
            android.view.ViewGroup panel=floatingPreview(activity,preparing,preparing?"Menunggu form":"100/100 · Menyelesaikan");
            android.widget.Button primary=(android.widget.Button)field(panel,"primary"),stop=(android.widget.Button)field(panel,"stop");
            check(primary.isEnabled()!=preparing,"Waiting primary remains disabled until service readiness");
            java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
            primary.setOnClickListener(v -> calls.incrementAndGet());stop.setOnClickListener(v -> calls.incrementAndGet());
            check(calls.get()==0,"Creating/styling a floating panel never executes an action");
            int width=Math.round(widthDp*density);panel.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED));
            panel.layout(0,0,width,panel.getMeasuredHeight());checkBounds(panel);
            check(primary.getMeasuredHeight()>=Math.round(48*density) && stop.getMeasuredHeight()>=Math.round(48*density),"Floating actions retain 48dp touch targets");
            primary.setEnabled(true);primary.performClick();stop.performClick();check(calls.get()==2,"Explicit test clicks dispatch exactly one callback each");
        }
    }
    private void waitEditable(Activity activity,boolean expected) throws Exception {
        long deadline=android.os.SystemClock.uptimeMillis()+8000;AtomicBoolean ready=new AtomicBoolean();
        while(android.os.SystemClock.uptimeMillis()<deadline){
            runOnMainSync(() -> {try{
                ready.set(((android.view.View)field(activity,"contract")).isEnabled()==expected
                    && ((android.view.View)field(activity,"periodButton")).isEnabled()==expected
                    && ((android.view.View)field(activity,"start")).isEnabled()==expected);
            }catch(Exception e){throw new RuntimeException(e);}});
            if(ready.get())return;android.os.SystemClock.sleep(80);
        }
        throw new AssertionError("Expected editable contract/period/start: "+expected);
    }
    private void waitListPosition(Activity activity,int position) throws Exception {
        long deadline=android.os.SystemClock.uptimeMillis()+8000;AtomicBoolean ready=new AtomicBoolean();
        while(android.os.SystemClock.uptimeMillis()<deadline){
            runOnMainSync(() -> {try{
                android.widget.ListView list=(android.widget.ListView)field(activity,"resultList");
                ready.set(list.getFirstVisiblePosition()==position && list.getChildCount()>0);
            }catch(Exception error){throw new RuntimeException(error);}});
            if(ready.get())return;android.os.SystemClock.sleep(40);
        }
        throw new AssertionError("List did not lay out requested position: "+position);
    }
    private void refreshDashboard(Activity activity) {
        runOnMainSync(() -> {try{setField(activity,"revision","!");invoke(activity,"update",new Class<?>[]{});}catch(Exception e){throw new RuntimeException(e);}});
    }
    private Context isolatedContext(Context context,String prefix) {
        final java.io.File directory=new java.io.File(context.getCacheDir(),prefix);check(directory.mkdir(),"Empty isolated database directory");
        return new android.content.ContextWrapper(context){
            @Override public Context getApplicationContext(){return this;}
            @Override public java.io.File getDatabasePath(String name){return new java.io.File(directory,name);}
            @Override public android.content.SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences(prefix+name,mode);}
            @Override public android.database.sqlite.SQLiteDatabase openOrCreateDatabase(String name,int mode,android.database.sqlite.SQLiteDatabase.CursorFactory factory){return android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory);}
            @Override public android.database.sqlite.SQLiteDatabase openOrCreateDatabase(String name,int mode,android.database.sqlite.SQLiteDatabase.CursorFactory factory,android.database.DatabaseErrorHandler handler){return android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getPath(),factory,handler);}
        };
    }
    /** Fresh, isolated Android SQLite database; never seeds or edits the normal app database. */
    private void checkCancellation(Context context) throws Exception {
        Context isolated=isolatedContext(context,"cancel-smoke-"+java.util.UUID.randomUUID());
        Class<?> storeType=Class.forName("id.local.mufchecker.QueueStore",true,context.getClassLoader());
        java.lang.reflect.Constructor<?> constructor=storeType.getDeclaredConstructor(Context.class);constructor.setAccessible(true);
        Object store=constructor.newInstance(isolated);
        Class<?> serviceType=Class.forName("id.local.mufchecker.TokopediaService",true,context.getClassLoader());
        Object previous=serviceType.getField("instance").get(null);check(previous==null,"No live accessibility service on test emulator");
        Activity activity=startActivitySync(new Intent().setClassName(context,"id.local.mufchecker.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        android.content.SharedPreferences preferences=context.getSharedPreferences("checker",Context.MODE_PRIVATE);
        String inputBefore=preferences.getString("last_contracts",""),periodBefore=preferences.getString("selected_period","");
        final java.util.concurrent.atomic.AtomicReference<Object> service=new java.util.concurrent.atomic.AtomicReference<>(),repository=new java.util.concurrent.atomic.AtomicReference<>();
        try {
            BulkQueue old=new BulkQueue(java.util.Arrays.asList("09000000000000000001"),YearMonth.of(2026,10));old.startNext();old.succeed(old.current().contract,"verified-history","");
            invoke(store,"save",new Class<?>[]{BulkQueue.class,String.class,String.class,String.class,boolean.class},old,null,null,null,false);
            BulkQueue draft=new BulkQueue(java.util.Arrays.asList("09000000000000000002"),YearMonth.of(2026,11),BulkQueue.StartMode.READY_PAGE);
            invoke(store,"save",new Class<?>[]{BulkQueue.class,String.class,String.class,String.class,boolean.class},draft,null,null,null,false);
            runOnMainSync(() -> {try{
                Object fake=serviceType.getConstructor().newInstance();
                Method attach=android.content.ContextWrapper.class.getDeclaredMethod("attachBaseContext",Context.class);attach.setAccessible(true);attach.invoke(fake,isolated);
                setField(fake,"store",store);setField(fake,"batch",draft);setField(fake,"preparing",true);
                service.set(fake);serviceType.getField("instance").set(null,fake);
                invoke(field(activity,"repository"),"close",new Class<?>[]{});
                Class<?> repoType=Class.forName("id.local.mufchecker.AppRepository",true,context.getClassLoader());
                java.lang.reflect.Constructor<?> make=repoType.getDeclaredConstructor(Context.class);make.setAccessible(true);Object repo=make.newInstance(isolated);setField(repo,"store",store);
                repository.set(repo);setField(activity,"repository",repo);
            }catch(Exception e){throw new RuntimeException(e);}});
            refreshDashboard(activity);waitEditable(activity,false);
            runOnMainSync(() -> {try{invoke(service.get(),"stopByUser",new Class<?>[]{});}catch(Exception e){throw new RuntimeException(e);}});
            refreshDashboard(activity);waitEditable(activity,true);
            runOnMainSync(() -> {try{
                android.widget.EditText input=(android.widget.EditText)field(activity,"contract");input.setText("09000000000000000002");input.getText().append("\n09000000000000000003");input.getText().delete(20,input.length());
                check(input.getText().toString().equals("09000000000000000002"),"Cancelled input accepts additions and deletions");
            }catch(Exception e){throw new RuntimeException(e);}});
            check("".equals(invoke(store,"read",new Class<?>[]{String.class},"batch")),"Cancelled draft releases current pointer");
            check(((BulkQueue)invoke(store,"session",new Class<?>[]{String.class},draft.sessionId())).cancelledPreparation(),"Cancelled history remains available");
            check(((BulkQueue)invoke(store,"session",new Class<?>[]{String.class},old.sessionId())).rows().get(0).result().equals("verified-history"),"Previous results remain intact");
            waitProductWindow(context);
            android.graphics.Bitmap cancelled=getUiAutomation().takeScreenshot();
            String capture=arguments.getString("capture","cancelled-editable");check(capture.matches("[a-z0-9-]+"),"Safe cancellation capture name");
            try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null),capture+".png"))){cancelled.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}cancelled.recycle();
            BulkQueue fresh=new BulkQueue(java.util.Arrays.asList("09000000000000000003"),YearMonth.of(2026,11),BulkQueue.StartMode.READY_PAGE);
            invoke(store,"save",new Class<?>[]{BulkQueue.class,String.class,String.class,String.class,boolean.class},fresh,null,null,null,false);
            check(!(Boolean)invoke(store,"cancelPreparation",new Class<?>[]{String.class,boolean.class},draft.sessionId(),false),"Stale cancellation cannot release another session");
            android.database.sqlite.SQLiteDatabase db=((android.database.sqlite.SQLiteOpenHelper)store).getWritableDatabase();
            db.execSQL("CREATE TEMP TRIGGER cancel_abort BEFORE DELETE ON meta WHEN OLD.key='batch' BEGIN SELECT RAISE(ABORT,'synthetic failure'); END");
            boolean failed=false;try{invoke(store,"cancelPreparation",new Class<?>[]{String.class,boolean.class},fresh.sessionId(),false);}catch(java.lang.reflect.InvocationTargetException expected){failed=true;}
            check(failed,"Injected storage failure surfaces");db.execSQL("DROP TRIGGER cancel_abort");
            check(((BulkQueue)invoke(store,"session",new Class<?>[]{String.class},fresh.sessionId())).state()==BulkQueue.RunState.PREPARED,"Failed cancellation rolls back history and current pointer together");
            check(!((String)invoke(store,"read",new Class<?>[]{String.class},"batch")).isEmpty(),"Failed cancellation remains protected");
            BulkQueue started=new BulkQueue(java.util.Arrays.asList("09000000000000000004","09000000000000000005"),YearMonth.of(2026,11),BulkQueue.StartMode.READY_PAGE);
            started.startPrepared();started.startNext();started.succeed(started.current().contract,"preserved-success","");started.pause("test","");started.prepareReadyPage();
            invoke(store,"save",new Class<?>[]{BulkQueue.class,String.class,String.class,String.class,boolean.class},started,null,null,null,false);
            check(!(Boolean)invoke(store,"cancelPreparation",new Class<?>[]{String.class,boolean.class},started.sessionId(),false),"Started work cannot be released by draft cancellation");
            runOnMainSync(() -> {try{
                setField(service.get(),"batch",started);setField(service.get(),"preparing",true);
                setField(service.get(),"trace",new id.local.mufchecker.DiagnosticTrace(true));invoke(service.get(),"stopByUser",new Class<?>[]{});
            }catch(Exception e){throw new RuntimeException(e);}});
            refreshDashboard(activity);waitEditable(activity,false);
            check(BulkQueue.restore((String)invoke(store,"read",new Class<?>[]{String.class},"batch")).rows().get(0).result().equals("preserved-success"),"Cancelling re-preparation retains completed results");
            BulkQueue legacy=new BulkQueue(java.util.Arrays.asList("09000000000000000006"),YearMonth.of(2026,11),BulkQueue.StartMode.READY_PAGE);
            legacy.pause("Persiapan dibatalkan. Antrean tersimpan; siapkan ulang untuk menampilkan tombol Mulai.","");
            invoke(store,"save",new Class<?>[]{BulkQueue.class,String.class,String.class,String.class,boolean.class},legacy,null,null,null,false);
            refreshDashboard(activity);waitEditable(activity,true);
            check("".equals(invoke(store,"read",new Class<?>[]{String.class},"batch")),"Repository recovers a 0.8.3 cancelled draft automatically");
        } finally {
            runOnMainSync(() -> {try{serviceType.getField("instance").set(null,previous);activity.finish();}catch(Exception e){throw new RuntimeException(e);}});
            waitForIdleSync();preferences.edit().putString("last_contracts",inputBefore).putString("selected_period",periodBefore).commit();
            ((android.database.sqlite.SQLiteOpenHelper)store).close();
        }
    }
    private id.local.mufchecker.InquirySession responseSession(String number){
        long now=android.os.SystemClock.elapsedRealtime();
        id.local.mufchecker.InquirySession session=new id.local.mufchecker.InquirySession(now,false);
        session.move(id.local.mufchecker.InquirySession.Stage.VERIFY_CONTRACT,now);
        check(session.markSubmitted(true,true,false,number,number,now) && session.markTapDispatched() && session.markTapCompleted(),"Accepted synthetic inquiry fixture");return session;
    }
    private void checkResponseRecovery(Context context) throws Exception {
        Context isolated=isolatedContext(context,"response-smoke-"+java.util.UUID.randomUUID());
        Class<?> storeType=Class.forName("id.local.mufchecker.QueueStore",true,context.getClassLoader());
        java.lang.reflect.Constructor<?> makeStore=storeType.getDeclaredConstructor(Context.class);makeStore.setAccessible(true);
        Object store=makeStore.newInstance(isolated);
        Class<?> serviceType=Class.forName("id.local.mufchecker.TokopediaService",true,context.getClassLoader());
        Object previous=serviceType.getField("instance").get(null);check(previous==null,"Emulator has no live accessibility service");
        final java.util.concurrent.atomic.AtomicReference<Object> service=new java.util.concurrent.atomic.AtomicReference<>();
        try {
            runOnMainSync(() -> {try{
                Object fake=serviceType.getConstructor().newInstance();
                Method attach=android.content.ContextWrapper.class.getDeclaredMethod("attachBaseContext",Context.class);attach.setAccessible(true);attach.invoke(fake,isolated);
                setField(fake,"store",store);service.set(fake);serviceType.getField("instance").set(null,fake);
            }catch(Exception error){throw new RuntimeException(error);}});
            List<String> numbers=new ArrayList<>();for(int i=1;i<=6;i++)numbers.add(String.format(java.util.Locale.ROOT,"0900000000%02d",i));
            BulkQueue batch=new BulkQueue(numbers,YearMonth.of(2026,11),BulkQueue.StartMode.READY_PAGE);batch.startPrepared();batch.startNext();batch.succeed(numbers.get(0),"previous-verified-success","");batch.startNext();
            invoke(store,"save",new Class<?>[]{BulkQueue.class,String.class,String.class,String.class,boolean.class},batch,null,null,null,false);
            runOnMainSync(() -> {try{
                Object fake=service.get();setField(fake,"batch",batch);setField(fake,"active",true);setField(fake,"contract",batch.current().contract);setField(fake,"period",batch.period);
                setField(fake,"session",responseSession(batch.current().contract));setField(fake,"trace",new id.local.mufchecker.DiagnosticTrace(true));
                setField(fake,"submittedAt",android.os.SystemClock.uptimeMillis()-1);
                android.view.accessibility.AccessibilityEvent message=android.view.accessibility.AccessibilityEvent.obtain(android.view.accessibility.AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);
                message.setPackageName("com.tokopedia.tkpd");message.setEventTime(android.os.SystemClock.uptimeMillis());message.getText().add("Nomor kontrak tidak ditemukan");
                invoke(fake,"onAccessibilityEvent",new Class<?>[]{android.view.accessibility.AccessibilityEvent.class},message);message.recycle();
                ((android.os.Handler)field(fake,"handler")).removeCallbacksAndMessages(null);
                id.local.mufchecker.InquiryResponse response=(id.local.mufchecker.InquiryResponse)field(fake,"response");
                id.local.mufchecker.InquiryResponse.Signal signal=response.pendingEvent(android.os.SystemClock.uptimeMillis());
                check(signal==id.local.mufchecker.InquiryResponse.Signal.CONTRACT_NOT_FOUND,"Actual event handler captures short contract error");
                check((Boolean)invoke(fake,"handleInquiryResponse",new Class<?>[]{id.local.mufchecker.InquiryResponse.Signal.class,boolean.class},signal,false),"Explicit error processed immediately");
                check(batch.rows().get(1).state()==BulkQueue.RowState.ERROR && batch.currentIndex()==2,"Error row committed and exactly next row selected");
                setField(fake,"session",responseSession(batch.current().contract));
                invoke(fake,"handleInquiryResponse",new Class<?>[]{id.local.mufchecker.InquiryResponse.Signal.class,boolean.class},id.local.mufchecker.InquiryResponse.Signal.RESTRICTED,false);
                check(batch.state()==BulkQueue.RunState.RUNNING && batch.currentIndex()==3 && batch.rows().get(2).reason().contains("cabang"),"Two explicit contract errors do not pause queue or lose restriction reason");
                setField(fake,"session",responseSession(batch.current().contract));
                response=(id.local.mufchecker.InquiryResponse)field(fake,"response");boolean expired=false;
                for(int time=0;time<=10000;time+=1000)expired=response.observeQuiet(time,(id.local.mufchecker.InquirySession)field(fake,"session"),true,false,false);
                check(expired,"Quiet synthetic form reaches ten-second deadline without wall-clock sleep");
                invoke(fake,"handleInquiryResponse",new Class<?>[]{id.local.mufchecker.InquiryResponse.Signal.class,boolean.class},id.local.mufchecker.InquiryResponse.Signal.NONE,expired);
                check(batch.currentIndex()==4 && batch.rows().get(3).reason().startsWith("Tidak ada respons"),"No-response becomes technical ERROR and advances once");
                setField(fake,"session",responseSession(batch.current().contract));
                invoke(fake,"handleInquiryResponse",new Class<?>[]{id.local.mufchecker.InquiryResponse.Signal.class,boolean.class},id.local.mufchecker.InquiryResponse.Signal.SERVICE_ERROR,false);
                check(batch.state()==BulkQueue.RunState.PAUSED && batch.rows().get(5).state()==BulkQueue.RowState.WAITING && !(Boolean)field(fake,"active"),"Second technical failure pauses before untouched next row");
                check(batch.rows().get(0).result().equals("previous-verified-success"),"No success is repeated or overwritten by fast skip");
            }catch(Exception error){throw new RuntimeException(error);}});
            BulkQueue saved=BulkQueue.restore((String)invoke(store,"read",new Class<?>[]{String.class},"batch"));
            check(saved.completedCount()==5 && saved.rows().get(1).reason().contains("tidak ditemukan") && saved.rows().get(3).reason().contains("10 detik"),"Each ERROR reason/state checkpoint is durable");
            BulkQueue failed=new BulkQueue(numbers,YearMonth.of(2026,11),BulkQueue.StartMode.READY_PAGE);failed.startPrepared();failed.startNext();
            invoke(store,"save",new Class<?>[]{BulkQueue.class,String.class,String.class,String.class,boolean.class},failed,null,null,null,false);
            android.database.sqlite.SQLiteDatabase db=((android.database.sqlite.SQLiteOpenHelper)store).getWritableDatabase();
            db.execSQL("CREATE TEMP TRIGGER response_abort BEFORE INSERT ON reports BEGIN SELECT RAISE(ABORT,'synthetic checkpoint failure'); END");
            runOnMainSync(() -> {try{
                Object fake=service.get();setField(fake,"batch",failed);setField(fake,"active",true);setField(fake,"contract",failed.current().contract);
                setField(fake,"session",responseSession(failed.current().contract));setField(fake,"trace",new id.local.mufchecker.DiagnosticTrace(true));
                invoke(fake,"handleInquiryResponse",new Class<?>[]{id.local.mufchecker.InquiryResponse.Signal.class,boolean.class},id.local.mufchecker.InquiryResponse.Signal.RESTRICTED,false);
                check(!(Boolean)field(fake,"active") && failed.rows().get(1).state()==BulkQueue.RowState.WAITING,"Failed checkpoint stops before next contract");
            }catch(Exception error){throw new RuntimeException(error);}});
            db.execSQL("DROP TRIGGER response_abort");
            saved=BulkQueue.restore((String)invoke(store,"read",new Class<?>[]{String.class},"batch"));
            check(saved.completedCount()==0,"Failed ERROR/report transaction cannot falsely advance saved queue");
            Activity probe=startActivitySync(new Intent().setClassName(context,"id.local.mufchecker.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            try{
                Class<?> snapshot=Class.forName("id.local.mufchecker.TokopediaService$Snapshot",true,context.getClassLoader());
                java.lang.reflect.Constructor<?> constructor=snapshot.getDeclaredConstructor(android.view.accessibility.AccessibilityNodeInfo.class,int.class,int.class);constructor.setAccessible(true);
                int index=0;
                for(String value:new String[]{"android.widget.ProgressBar","Mohon tunggu","Pembayaran tidak dapat dilakukan. Hubungi kantor cabang."}){
                    final String marker="Native response probe "+index++;
                    runOnMainSync(() -> {
                        android.widget.LinearLayout content=new android.widget.LinearLayout(probe);content.setOrientation(android.widget.LinearLayout.VERTICAL);content.setGravity(android.view.Gravity.CENTER);
                        android.widget.TextView heading=new android.widget.TextView(probe);heading.setText(marker);content.addView(heading);
                        if(value.startsWith("android."))content.addView(new android.widget.ProgressBar(probe));
                        else{android.widget.TextView label=new android.widget.TextView(probe);label.setText(value);content.addView(label);}
                        probe.setContentView(content);
                    });
                    waitText(marker);waitForIdleSync();
                    android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
                    Object frame=constructor.newInstance(root,1220,2712);
                    check((Boolean)invoke(frame,"loading",new Class<?>[]{})==!value.contains("cabang"),"Real native accessibility snapshot detects spinner/text loading");
                    if(value.contains("cabang"))check(invoke(frame,"responseSignal",new Class<?>[]{})==id.local.mufchecker.InquiryResponse.Signal.RESTRICTED,"Native snapshot classifies explicit branch message");
                    invoke(frame,"close",new Class<?>[]{});root.recycle();
                }
            }finally{runOnMainSync(probe::finish);waitForIdleSync();}
        }finally{
            runOnMainSync(() -> {try{((android.os.Handler)field(service.get(),"handler")).removeCallbacksAndMessages(null);serviceType.getField("instance").set(null,previous);}catch(Exception error){throw new RuntimeException(error);}});
            ((android.database.sqlite.SQLiteOpenHelper)store).close();
        }
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            check(android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.HARDWARE.contains("goldfish"), "Emulator only");
            Context context=getTargetContext();
            Class<?> storeClass=Class.forName("id.local.mufchecker.QueueStore",true,context.getClassLoader());
            Method acquire=storeClass.getDeclaredMethod("acquire",Context.class); acquire.setAccessible(true);
            Object store=acquire.invoke(null,context);
            String command=arguments.getString("command","verify");
            try {
                if (command.equals("seed")) {
                    check(((List<?>)invoke(store,"sessions",new Class<?>[]{})).isEmpty(), "Seed requires an empty emulator app database");
                    for (int session=0;session<2;session++) {
                        List<String> numbers=new ArrayList<>();
                        for(int i=0;i<100;i++) numbers.add(String.format(java.util.Locale.ROOT,"0900000000000000%04d",i));
                        BulkQueue q=new BulkQueue(numbers,YearMonth.of(2026,10),BulkQueue.StartMode.READY_PAGE); q.startPrepared();
                        int count=session==0?4:100;
                        for(int i=0;i<count;i++) {
                            BulkQueue.Row row=q.startNext(); q.addCurrentDuration("WAIT_RESULT",7800);
                            if(i%3==2) q.fail("Pembayaran dibatasi — hubungi kantor cabang.","Synthetic diagnosis");
                            else {
                                JSONObject fields=new JSONObject().put("nama","DEMO** PENGGUNA*** PANJANG***").put("tagihan","Rp1.234.567").put("cicilan ke","12");
                                JSONObject json=new JSONObject().put("contract",row.contract).put("period","2026-10")
                                    .put("due",i%3==0?"2026-11-15":"2026-10-15").put("status",i%3==0?"SUDAH BAYAR":"BELUM BAYAR")
                                    .put("details",fields).put("reason","Data contoh untuk pengujian UI.");
                                q.succeed(row.contract,json.toString(),"Synthetic diagnosis");
                            }
                        }
                        if(session==0) q.pause("Sesi contoh dijeda. Siapkan ulang panel untuk melanjutkan.","");
                        check((Boolean)invoke(store,"save",new Class<?>[]{BulkQueue.class,String.class,String.class,String.class,boolean.class},q,null,null,null,true),"Save fixture");
                    }
                    context.getSharedPreferences("checker",Context.MODE_PRIVATE).edit().putString("last_contracts","09000000000000000000\n09000000000000000001").commit();
                }
                List<?> sessions=(List<?>)invoke(store,"sessions",new Class<?>[]{});
                check(sessions.size()==2,"Two sessions survive upgrade");
                int completed=0,paused=0; java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
                for(Object item:sessions) {
                    BulkQueue q=(BulkQueue)item; check(q.rows().size()==100,"100 rows preserved");
                    java.util.Properties properties=new java.util.Properties();properties.load(new java.io.StringReader(q.serialize(false)));
                    for(String key:new java.util.TreeSet<String>(properties.stringPropertyNames())){
                        digest.update((key+"="+properties.getProperty(key)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                    if(q.state()==BulkQueue.RunState.COMPLETED) { completed++; check(q.errorCount()==33,"33 branch restrictions retained"); check(q.rows().get(0).result().contains("DEMO**"),"Masked result retained"); }
                    if(q.state()==BulkQueue.RunState.PAUSED) { paused++; check(q.completedCount()==4,"Unfinished session preserves four completed results"); }
                }
                check(completed==1 && paused==1,"Completed and paused state retained");
                StringBuilder fingerprint=new StringBuilder();for(byte value:digest.digest())fingerprint.append(String.format(java.util.Locale.ROOT,"%02x",value & 255));
                result.putString("snapshotSha256",fingerprint.toString());
            } finally {
                Method release=storeClass.getDeclaredMethod("release",storeClass); release.setAccessible(true); release.invoke(null,store);
            }
            if(command.equals("cancel")){
                checkCancellation(context);result.putString("cancellation","PASS: actual service cancellation, editable form, legacy recovery, history retention, stale-session guard, transaction rollback and started-work protection on isolated empty Android database");
            }
            if(command.equals("response")){
                checkResponseRecovery(context);result.putString("response","PASS: short event capture, immediate contract ERROR skip, two business errors continue, ten-second quiet decision, two technical errors pause, durable reasons, preserved success and rollback stops next contract on isolated database");
            }
            if(command.equals("icon")) {
                android.graphics.drawable.Drawable icon=context.getApplicationInfo().loadIcon(context.getPackageManager());
                check(icon instanceof android.graphics.drawable.AdaptiveIconDrawable,"Launcher resolves to native adaptive icon");
                android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(512,512,android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);icon.setBounds(0,0,512,512);icon.draw(canvas);
                java.io.File image=new java.io.File(context.getExternalFilesDir(null),"launcher.png");
                try(java.io.FileOutputStream output=new java.io.FileOutputStream(image)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}
                bitmap.recycle();result.putString("icon","PASS: native adaptive icon rendered from installed APK");
            }
            if(command.equals("panel")) {
                Activity activity=startActivitySync(new Intent().setClassName(context,"id.local.mufchecker.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                String state=arguments.getString("state","ready");
                runOnMainSync(() -> {try {
                    checkFloating(activity);
                    android.widget.FrameLayout stage=new android.widget.FrameLayout(activity);
                    stage.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{0xFFF7F9F8,0xFFCAD9D0}));
                    android.widget.TextView caption=new android.widget.TextView(activity);caption.setText("Pratinjau panel native\nData sintetis · bukan inquiry Tokopedia");caption.setTextSize(15);caption.setTextColor(0xFF1D2321);caption.setPadding(32,0,32,0);
                    android.widget.FrameLayout.LayoutParams captionLp=new android.widget.FrameLayout.LayoutParams(-1,-2);captionLp.topMargin=Math.round(150*activity.getResources().getDisplayMetrics().density);stage.addView(caption,captionLp);
                    boolean preparing=!state.equals("active");
                    android.view.ViewGroup panel=floatingPreview(activity,preparing,preparing?(state.equals("waiting")?"Menunggu form":"MUF · 100 kontrak"):"7/100 · Memeriksa");
                    ((android.widget.Button)field(panel,"primary")).setEnabled(!state.equals("waiting"));panel.getChildAt(1).setAlpha(state.equals("waiting")?.45f:1f);
                    android.widget.FrameLayout.LayoutParams lp=new android.widget.FrameLayout.LayoutParams(-1,-2);int margin=Math.round(12*activity.getResources().getDisplayMetrics().density);lp.setMargins(margin,Math.round(32*activity.getResources().getDisplayMetrics().density),margin,0);stage.addView(panel,lp);activity.setContentView(stage);
                }catch(Exception e){throw new RuntimeException(e);} });
                waitForIdleSync();waitProductWindow(context);
                android.graphics.Bitmap screenshot=getUiAutomation().takeScreenshot();java.io.File image=new java.io.File(context.getExternalFilesDir(null),"panel-"+state+".png");
                try(java.io.FileOutputStream output=new java.io.FileOutputStream(image)){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}screenshot.recycle();
                runOnMainSync(() -> {try {
                    android.view.ViewGroup content=(android.view.ViewGroup)activity.findViewById(android.R.id.content);
                    android.view.ViewGroup stage=(android.view.ViewGroup)content.getChildAt(0);android.view.View panel=stage.getChildAt(1);
                    android.graphics.Bitmap strip=android.graphics.Bitmap.createBitmap(panel.getWidth(),panel.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas canvas=new android.graphics.Canvas(strip);canvas.translate(-panel.getLeft(),-panel.getTop());stage.draw(canvas);
                    java.io.File target=new java.io.File(context.getExternalFilesDir(null),"panel-"+state+"-strip.png");
                    try(java.io.FileOutputStream output=new java.io.FileOutputStream(target)){strip.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}strip.recycle();
                }catch(Exception e){throw new RuntimeException(e);} });
                result.putString("panel","PASS: native glass preview, wrapping, 48dp actions, disabled readiness and explicit callbacks");
            }
            if(command.equals("page") || command.equals("ui") || command.equals("rotate") || command.equals("gestures")) {
                Activity activity=startActivitySync(new Intent().setClassName(context,"id.local.mufchecker.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                int page=Integer.parseInt(arguments.getString("page","0"));
                runOnMainSync(() -> { try { invoke(activity,"selectPage",new Class<?>[]{int.class},page); } catch(Exception e) { throw new RuntimeException(e); } });
                waitForIdleSync();
                if(command.equals("gestures")){
                    runOnMainSync(() -> {try{invoke(activity,"selectPage",new Class<?>[]{int.class},1);}catch(Exception e){throw new RuntimeException(e);}});
                    waitCount(activity,100);
                    runOnMainSync(() -> {try{((android.widget.ListView)field(activity,"resultList")).setSelection(1);}catch(Exception e){throw new RuntimeException(e);}});
                    waitListPosition(activity,1);waitForIdleSync();waitProductWindow(context);final int[] bounds=new int[4];
                    runOnMainSync(() -> {try{
                        android.widget.ListView list=(android.widget.ListView)field(activity,"resultList");android.view.View row=list.getChildAt(0);
                        row.getLocationOnScreen(bounds);bounds[2]=row.getWidth();bounds[3]=row.getHeight();
                    }catch(Exception e){throw new RuntimeException(e);}});
                    swipe(bounds[0]+bounds[2]*.85f,bounds[1]+bounds[3]/2f,bounds[0]+bounds[2]*.12f,bounds[1]+bounds[3]/2f);
                    runOnMainSync(() -> {try{
                        int offset=(Integer)field(activity,"tableScrollX");check(offset>0,"Native horizontal swipe moves shared table offset");
                        android.widget.ListView list=(android.widget.ListView)field(activity,"resultList");
                        for(int i=0;i<list.getChildCount();i++)check(list.getChildAt(i).getScrollX()==offset,"Visible rows share horizontal offset after native gesture");
                        check(((android.view.View)field(activity,"tableHeading")).getScrollX()==offset,"Header shares swipe offset");
                        list.getLocationOnScreen(bounds);bounds[2]=list.getWidth();bounds[3]=list.getHeight();
                    }catch(Exception e){throw new RuntimeException(e);}});
                    swipe(bounds[0]+bounds[2]/2f,bounds[1]+bounds[3]*.8f,bounds[0]+bounds[2]/2f,bounds[1]+bounds[3]*.2f);
                    runOnMainSync(() -> {try{
                        android.widget.ListView list=(android.widget.ListView)field(activity,"resultList");
                        check(list.getFirstVisiblePosition()>1,"Native vertical swipe crosses rows while horizontal cells are present");
                        list.setSelection(60);
                    }catch(Exception e){throw new RuntimeException(e);}});
                    waitListPosition(activity,60);waitForIdleSync();
                    runOnMainSync(() -> {try{
                        int offset=(Integer)field(activity,"tableScrollX");android.widget.ListView list=(android.widget.ListView)field(activity,"resultList");
                        check(list.getFirstVisiblePosition()==60,"ListView still scrolls vertically");
                        for(int i=0;i<list.getChildCount();i++)check(list.getChildAt(i).getScrollX()==offset,"Recycled rows inherit shared horizontal offset");
                        list.setSelection(1);
                    }catch(Exception e){throw new RuntimeException(e);}});
                    waitListPosition(activity,1);waitForIdleSync();
                    runOnMainSync(() -> {try{
                        android.widget.ListView list=(android.widget.ListView)field(activity,"resultList");android.view.View row=list.getChildAt(0);
                        row.getLocationOnScreen(bounds);bounds[2]=row.getWidth();bounds[3]=row.getHeight();
                    }catch(Exception e){throw new RuntimeException(e);}});
                    swipe(bounds[0]+bounds[2]/2f,bounds[1]+bounds[3]/2f,bounds[0]+bounds[2]/2f,bounds[1]+bounds[3]/2f);
                    waitText("Rincian kontrak");waitText("DEMO** PENGGUNA*** PANJANG***");
                    android.view.accessibility.AccessibilityNodeInfo node=getUiAutomation().getRootInActiveWindow();
                    java.util.List<android.view.accessibility.AccessibilityNodeInfo> close=node.findAccessibilityNodeInfosByText("Tutup");
                    check(!close.isEmpty() && close.get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK),"Detail dialog closes after native row tap");
                    result.putString("gestures","PASS: native horizontal swipe, shared header/visible/recycled offsets, vertical recycling, row tap opens details and closes");
                }
                if(command.equals("rotate")) {
                    runOnMainSync(() -> {try { invoke(activity,"selectPage",new Class<?>[]{int.class},1); }catch(Exception e){throw new RuntimeException(e);} });
                    waitCount(activity,100);
                    runOnMainSync(() -> {try {
                        setField(activity,"filter","Error"); ((android.widget.EditText)field(activity,"search")).setText("09000000000000000002");
                        invoke(activity,"renderTable",new Class<?>[]{});
                    }catch(Exception e){throw new RuntimeException(e);} });
                    waitCount(activity,1);
                    ActivityMonitor monitor=addMonitor("id.local.mufchecker.MainActivity",null,false);
                    runOnMainSync(() -> activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
                    Activity rotated=waitForMonitorWithTimeout(monitor,8000); removeMonitor(monitor);
                    check(rotated!=null && rotated!=activity,"Rotation recreates Activity"); waitCount(rotated,1);
                    runOnMainSync(() -> {try {
                        check(((Integer)field(rotated,"selectedPage"))==1,"Results tab survives rotation");
                        check("Error".equals(field(rotated,"filter")),"Status filter survives rotation");
                        check("09000000000000000002".equals(((android.widget.EditText)field(rotated,"search")).getText().toString()),"Query survives rotation");
                    }catch(Exception e){throw new RuntimeException(e);} });
                    waitForIdleSync();
                    waitProductWindow(context);
                    android.graphics.Bitmap screenshot=getUiAutomation().takeScreenshot();
                    java.io.File image=new java.io.File(context.getExternalFilesDir(null),"rotasi-pencarian.png");
                    try(java.io.FileOutputStream output=new java.io.FileOutputStream(image)){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}
                    screenshot.recycle();result.putString("rotation","PASS: Activity recreated with results tab, query and error filter preserved");
                }
                if(command.equals("page")) {
                    String query=arguments.getString("query","");
                    if(!query.isEmpty()) {
                        waitCount(activity,100);
                        runOnMainSync(() -> {try {
                            setField(activity,"filter",arguments.getString("filter","Semua"));
                            android.widget.EditText search=(android.widget.EditText)field(activity,"search"); search.setText(query); search.requestFocus();
                            if("true".equals(arguments.getString("keyboard","false")))((android.view.inputmethod.InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(search,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                        }catch(Exception e){throw new RuntimeException(e);} });
                        waitCount(activity,1);waitForIdleSync();
                        if("true".equals(arguments.getString("keyboard","false"))) {
                            int[] touch=new int[2];
                            runOnMainSync(() -> {try {
                                android.widget.EditText search=(android.widget.EditText)field(activity,"search");
                                search.getLocationOnScreen(touch);touch[0]+=search.getWidth()/2;touch[1]+=search.getHeight()/2;
                            }catch(Exception e){throw new RuntimeException(e);} });
                            long now=android.os.SystemClock.uptimeMillis();
                            android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,touch[0],touch[1],0);
                            android.view.MotionEvent up=android.view.MotionEvent.obtain(now,now+50,android.view.MotionEvent.ACTION_UP,touch[0],touch[1],0);
                            sendPointerSync(down);sendPointerSync(up);down.recycle();up.recycle();
                            android.os.SystemClock.sleep(600);waitForIdleSync();
                        }
                    }
                    String dialog=arguments.getString("dialog","");
                    if(dialog.equals("detail") || dialog.equals("error"))waitCount(activity,100);
                    runOnMainSync(() -> { try {
                        if(dialog.equals("access"))invoke(activity,"explainAccessibility",new Class<?>[]{});
                        if(dialog.equals("sessions"))invoke(activity,"chooseDiagnosisSession",new Class<?>[]{});
                        if(dialog.equals("detail") || dialog.equals("error")) {
                            android.widget.BaseAdapter adapter=(android.widget.BaseAdapter)field(activity,"resultAdapter");
                            Object item=adapter.getItem(dialog.equals("error")?2:0);
                            invoke(activity,"showRow",new Class<?>[]{BulkQueue.Row.class,String.class},field(item,"source"),field(item,"sessionId"));
                        }
                    }catch(Exception e){throw new RuntimeException(e);} });
                    waitForIdleSync();
                    if(dialog.equals("detail") || dialog.equals("error"))waitText("Rincian kontrak");
                    if("true".equals(arguments.getString("tableEnd","false"))){
                        waitCount(activity,100);
                        runOnMainSync(() -> {try{
                            android.widget.HorizontalScrollView heading=(android.widget.HorizontalScrollView)field(activity,"tableHeading");
                            heading.scrollTo(heading.getChildAt(0).getWidth()-heading.getWidth(),0);
                        }catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
                    }
                    String capture=arguments.getString("capture", "page-"+page);
                    check(capture.matches("[a-z0-9-]+"),"Safe screenshot filename");
                    waitProductWindow(context);
                    android.graphics.Bitmap screenshot=getUiAutomation().takeScreenshot();
                    check(screenshot!=null,"Screenshot captured from live APK");
                    java.io.File image=new java.io.File(context.getExternalFilesDir(null),capture+".png");
                    try(java.io.FileOutputStream output=new java.io.FileOutputStream(image)){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}
                    screenshot.recycle();result.putString("screenshot",image.getAbsolutePath());
                }
                if(command.equals("ui")) {
                    runOnMainSync(() -> { try { invoke(activity,"selectPage",new Class<?>[]{int.class},1); } catch(Exception e) { throw new RuntimeException(e); } });
                    waitCount(activity,100);
                    runOnMainSync(() -> { try {
                        android.widget.ListView list=(android.widget.ListView)field(activity,"resultList");
                        check(list.getChildCount()<20,"100 rows are virtualized");
                        checkResultLayout(activity);checkFloating(activity);
                        ((android.widget.EditText)field(activity,"search")).setText("09000000000000000099");
                    } catch(Exception e) { throw new RuntimeException(e); } });
                    waitCount(activity,1);
                    runOnMainSync(() -> { try { setField(activity,"filter","Error"); invoke(activity,"renderTable",new Class<?>[]{}); } catch(Exception e) { throw new RuntimeException(e); } });
                    waitCount(activity,0);
                    runOnMainSync(() -> { try { ((android.widget.EditText)field(activity,"search")).setText("09000000000000000002"); } catch(Exception e) { throw new RuntimeException(e); } });
                    waitCount(activity,1);
                    runOnMainSync(() -> { try {
                        setField(activity,"filter","Semua"); ((android.widget.EditText)field(activity,"search")).setText("");
                        invoke(activity,"renderTable",new Class<?>[]{});
                    } catch(Exception e) { throw new RuntimeException(e); } });
                    waitCount(activity,100);
                    // TextWatcher also schedules a debounced render; settle it before testing an unrelated refresh.
                    android.os.SystemClock.sleep(300);waitForIdleSync();
                    runOnMainSync(() -> { try { ((android.widget.ListView)field(activity,"resultList")).setSelection(60); } catch(Exception e) { throw new RuntimeException(e); } });
                    waitListPosition(activity,60);waitForIdleSync();
                    runOnMainSync(() -> { try { invoke(activity,"renderTable",new Class<?>[]{}); } catch(Exception e) { throw new RuntimeException(e); } });
                    android.os.SystemClock.sleep(500); waitForIdleSync();
                    runOnMainSync(() -> { try {
                        android.widget.ListView list=(android.widget.ListView)field(activity,"resultList");
                        check(list.getFirstVisiblePosition()==60,"Refresh preserves scroll anchor; actual position="+list.getFirstVisiblePosition()); list.setSelection(0);
                    } catch(Exception e) { throw new RuntimeException(e); } });
                    final java.util.concurrent.atomic.AtomicReference<android.app.AlertDialog> testedDialog=new java.util.concurrent.atomic.AtomicReference<>();
                    runOnMainSync(() -> { try {
                        android.app.AlertDialog.Builder builder=new android.app.AlertDialog.Builder(activity).setTitle("Aksesibilitas")
                            .setMessage("Uji tata letak tombol dengan ukuran font pengguna.").setNeutralButton("Diagnosis",null)
                            .setNegativeButton("Tutup",null).setPositiveButton("Buka pengaturan",null);
                        testedDialog.set((android.app.AlertDialog)invoke(activity,"showDialog",new Class<?>[]{android.app.AlertDialog.Builder.class},builder));
                    }catch(Exception e){throw new RuntimeException(e);} });
                    waitForIdleSync();
                    runOnMainSync(() -> {
                        android.app.AlertDialog dialog=testedDialog.get();
                        checkBounds((android.view.ViewGroup)dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).getParent());
                        dialog.dismiss();
                    });
                    result.putString("ui","PASS: four aligned table columns at 280/320/375/407/800dp, complete identities, distinct statuses, dialog actions, search/filter, scroll anchor");
                }
            }
            result.putString("stream","PASS: synthetic Android SQLite/session upgrade checks\n"); finish(Activity.RESULT_OK,result);
        } catch(Throwable error) { result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(error)); finish(Activity.RESULT_CANCELED,result); }
    }
}
