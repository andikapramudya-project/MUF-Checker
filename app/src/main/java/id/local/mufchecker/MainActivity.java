package id.local.mufchecker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int NAVY = MufTheme.NAVY, GREEN = MufTheme.GREEN;
    private static final int MUTED = MufTheme.MUTED, BACK = MufTheme.BACK;
    private static final int RED = MufTheme.RED, AMBER = MufTheme.AMBER;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private AppRepository repository;
    private boolean loading, busy, resumed;
    private String pendingExport = "", tableKey = "";
    private boolean tableDirty;
    private BulkQueue queue, viewQueue;
    private String selectedSession = "", latestSession = "";
    private LinearLayout sessionList;
    private LinearLayout resultOverview;
    private TextView diagnosisChoice;
    private LinearLayout activityProgress;
    private Button resultsShortcut;
    private ResultRow tableHeading;
    private final int[] tableColumns = new int[4];
    private int tableScrollX;
    private boolean syncingTableScroll;
    private ResultRow flingOwner;
    private android.widget.FrameLayout navigation;
    private LinearLayout navigationTabs;
    private View navigationIndicator;
    private TextView sessionSelect, sessionMenu, activityNote, paidCount, unpaidCount, errorCount;
    private java.util.List<BulkQueue> history = new java.util.ArrayList<>();
    private EditText contract, search;
    private TextView periodView, state, connection, inputCount, summary, empty;
    private Button start, stop, pause, resume, retry, periodButton;
    private ProgressBar progress;
    private Button modeButton;
    private android.widget.Switch awake;
    private BulkQueue.StartMode startMode = BulkQueue.StartMode.READY_PAGE;
    private ListView resultList;
    private ResultAdapter resultAdapter;
    private int renderGeneration;
    private TextView progressLabel;
    private final Runnable searchRefresh = () -> renderTable();
    private final View[] pages = new View[3];
    private final TextView[] tabs = new TextView[3];
    private YearMonth period;
    private String revision = "!", filter = "Semua", searchText = "";
    private int selectedPage;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { update(); handler.postDelayed(this, running() || queue != null && queue.hasUnfinished() ? 900 : 3000); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("checker", MODE_PRIVATE);
        repository = new AppRepository(this);
        period = YearMonth.now(ZoneId.of("Asia/Makassar"));
        try { period = YearMonth.parse(state == null ? prefs.getString("selected_period", period.toString()) : state.getString("period")); } catch (Exception ignored) { }
        selectedPage = state == null ? 0 : state.getInt("page", 0);
        if (state != null) {
            selectedSession = state.getString("session", ""); pendingExport = state.getString("pendingExport", "");
            filter = state.getString("filter", "Semua"); searchText = state.getString("search", "");
            tableScrollX = state.getInt("tableScrollX", 0);
        }
        buildUi(); update(); selectPage(selectedPage);
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if (period != null) out.putString("period", period.toString());
        out.putInt("page", selectedPage); super.onSaveInstanceState(out);
        out.putString("session", selectedSession); out.putString("pendingExport", pendingExport);
        out.putString("filter", filter); out.putString("search", searchText);
        out.putInt("tableScrollX", tableScrollX);
    }
    @Override protected void onResume() { super.onResume(); resumed=true; if (contract != null) { handler.removeCallbacks(refresh); handler.post(refresh); } }
    @Override protected void onPause() {
        resumed=false;
        handler.removeCallbacks(refresh); handler.removeCallbacks(searchRefresh);
        for(View page:pages)if(page!=null){page.animate().cancel();page.setAlpha(1f);page.setTranslationY(0);}
        syncNavigation(false);
        if (contract != null) prefs.edit().putString("last_contracts", contract.getText().toString()).putString("selected_period", period.toString()).apply();
        super.onPause();
    }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); if (repository != null) repository.close(); super.onDestroy(); }
    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "0.8.6"; }
    }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setLineSpacing(dp(3), 1f); if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); return t;
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private void gap(LinearLayout l, int h) { l.addView(new View(this), new LinearLayout.LayoutParams(1, dp(h))); }
    private GradientDrawable bg(int color, int radius, boolean border) {
        return MufTheme.shape(this,color,radius,border);
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = column(); c.setPadding(dp(16), dp(16), dp(16), dp(16)); c.setBackground(bg(Color.WHITE, 16, true));
        parent.addView(c, new LinearLayout.LayoutParams(-1, -2)); return c;
    }
    private Button button(LinearLayout parent, String label, boolean primary, View.OnClickListener action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(14); b.setMinHeight(dp(48));
        MufTheme.flatten(b);
        b.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}}, new int[]{MUTED, primary ? Color.WHITE : GREEN})); b.setBackground(MufTheme.touch(this,primary ? GREEN : Color.WHITE,12,!primary));
        b.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(10); parent.addView(b, lp);
        b.setOnClickListener(action); return b;
    }
    private LinearLayout page(LinearLayout host, int index) {
        LinearLayout body = column();
        if (index == 1) {
            resultList = new ListView(this); pages[index] = resultList;
            resultList.setItemsCanFocus(true);
            resultList.setPadding(dp(20), 0, dp(20), dp(24));
            resultList.setClipToPadding(false); resultList.setCacheColorHint(Color.TRANSPARENT);
            resultList.setDivider(new android.graphics.drawable.ColorDrawable(MufTheme.BORDER));
            resultList.setDividerHeight(0); resultList.setHeaderDividersEnabled(false);
            resultList.addHeaderView(body, null, false);
            body.setPadding(0, dp(8), 0, 0);
        } else {
            ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
            scroll.addView(body); pages[index] = scroll;
            body.setPadding(dp(20), dp(8), dp(20), dp(24));
        }
        host.addView(pages[index], new LinearLayout.LayoutParams(-1, -1));
        return body;
    }
    private TextView metric(LinearLayout parent, String label, int color, String filterValue) {
        LinearLayout c = column(); c.setPadding(dp(12),dp(10),dp(12),dp(10)); c.setBackground(MufTheme.touch(this,BACK,12,false));
        c.setMinimumHeight(dp(48)); c.setContentDescription("Tampilkan hasil " + label); c.setOnClickListener(v -> applyFilter(filterValue));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,-2,1); lp.setMargins(dp(3),0,dp(3),0); parent.addView(c,lp);
        TextView value = text("0",24,color,true); c.addView(value); c.addView(text(label,12,MUTED,false)); return value;
    }
    private void updateModeLabel() {
        String label = startMode == BulkQueue.StartMode.READY_PAGE ? "Halaman Tokopedia siap" : "Tautan MUF";
        if (modeButton != null) { modeButton.setText(label); modeButton.setContentDescription("Mode pemeriksaan, " + label + ", ketuk untuk mengubah"); }
        if (start != null) start.setText(startMode == BulkQueue.StartMode.READY_PAGE ? "Siapkan pemeriksaan" : "Mulai melalui tautan");
    }
    private void chooseMode() {
        if (running() || queue != null && queue.hasUnfinished()) { toast("Selesaikan antrean sebelum mengganti mode."); return; }
        showDialog(new AlertDialog.Builder(this).setTitle("Mode pemeriksaan")
            .setSingleChoiceItems(new String[]{"Halaman Tokopedia siap","Buka melalui tautan MUF"}, startMode == BulkQueue.StartMode.READY_PAGE ? 0 : 1,
                (dialog, which) -> {
                    if (running() || queue != null && queue.hasUnfinished()) { dialog.dismiss(); return; }
                    startMode = which == 0 ? BulkQueue.StartMode.READY_PAGE : BulkQueue.StartMode.DIRECT_LINK;
                    prefs.edit().putString("start_mode",startMode.name()).apply(); updateModeLabel(); dialog.dismiss();
                }).setNegativeButton("Tutup",null));
    }
    private void buildUi() {
        LinearLayout shell = column(); shell.setBackgroundColor(BACK);
        shell.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets;
        });
        LinearLayout brand = new LinearLayout(this); brand.setGravity(Gravity.CENTER_VERTICAL); brand.setPadding(dp(20), dp(16), dp(20), dp(8));
        TextView wordmark = text("MUF CHECKER", 14, GREEN, true); wordmark.setLetterSpacing(.10f); brand.addView(wordmark);
        shell.addView(brand);
        android.widget.FrameLayout frames = new android.widget.FrameLayout(this); shell.addView(frames, new LinearLayout.LayoutParams(-1, 0, 1));
        // One visible page at a time; input and table retain their positions when navigating.
        LinearLayout pageHost = column(); frames.addView(pageHost);
        LinearLayout checking = page(pageHost, 0);
        LinearLayout form = card(checking);
        LinearLayout periodRow = new LinearLayout(this); periodRow.setGravity(Gravity.CENTER_VERTICAL);
        periodRow.setPadding(dp(12),dp(8),dp(12),dp(8));periodRow.setBackground(bg(MufTheme.SOFT_GREEN,12,false));form.addView(periodRow);
        LinearLayout periodLabels=column();LinearLayout.LayoutParams labelsLp=new LinearLayout.LayoutParams(0,-2,1);labelsLp.rightMargin=dp(8);periodRow.addView(periodLabels,labelsLp);
        periodLabels.addView(text("Periode",11,MUTED,false));periodView=text(periodLabel(period),14,NAVY,true);periodView.setLineSpacing(0,1f);periodLabels.addView(periodView);
        periodButton=button(periodRow,"Ubah",false,v -> pickPeriod());periodButton.setMinWidth(dp(64));periodButton.setMinimumWidth(dp(64));
        periodButton.setTextSize(13);
        periodButton.setLayoutParams(new LinearLayout.LayoutParams(-2,-2));
        gap(form,16); form.addView(text("Nomor kontrak", 14, NAVY, true));
        contract = new EditText(this); contract.setId(1001); contract.setTextSize(16); contract.setTextColor(NAVY); contract.setHintTextColor(MUTED); contract.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); contract.setGravity(Gravity.TOP);
        contract.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        contract.setMinLines(4); contract.setMaxLines(7); contract.setPadding(dp(12), dp(12), dp(12), dp(12));
        contract.setBackground(bg(BACK, 12, true)); contract.setHint("Satu nomor per baris");
        contract.setContentDescription("Nomor kontrak MUF, satu nomor per baris, maksimal " + BulkQueue.LIMIT);
        contract.setText(prefs.getString("last_contracts", prefs.getString("last_contract", "")));
        LinearLayout.LayoutParams fieldLp = new LinearLayout.LayoutParams(-1, -2); fieldLp.topMargin = dp(12); form.addView(contract, fieldLp);
        inputCount = text("", 12, MUTED, false); gap(form, 8); form.addView(inputCount);
        contract.addTextChangedListener(watcher(() -> countInput())); countInput();
        startMode = "DIRECT_LINK".equals(prefs.getString("start_mode", "READY_PAGE"))
            ? BulkQueue.StartMode.DIRECT_LINK : BulkQueue.StartMode.READY_PAGE;
        gap(form,4);
        start = button(form, startMode == BulkQueue.StartMode.READY_PAGE ? "Siapkan pemeriksaan" : "Mulai melalui tautan", true, v -> requestStart());
        gap(form,8); form.addView(text("Buka form MUF, tekan Mulai di panel.",12,MUTED,false));
        gap(checking, 14); LinearLayout live = card(checking);
        live.addView(text("Aktivitas", 15, NAVY, true)); gap(live, 8); state = text("Belum ada pemeriksaan.", 13, MUTED, false); live.addView(state);
        state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        activityNote=text("",12,MUTED,false); activityNote.setMaxLines(3); activityNote.setEllipsize(android.text.TextUtils.TruncateAt.END); live.addView(activityNote);
        activityNote.setMinHeight(dp(48));
        activityNote.setOnClickListener(v -> { if(queue != null && !queue.reason().isEmpty()) showText("Aktivitas",queue.reason()); });
        activityProgress=column();live.addView(activityProgress);activityProgress.setVisibility(View.GONE);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); gap(activityProgress,12);activityProgress.addView(progress,new LinearLayout.LayoutParams(-1,dp(6)));
        progressLabel=text("",12,MUTED,false);gap(activityProgress,8);activityProgress.addView(progressLabel);
        pause = button(live, "Jeda setelah kontrak aktif", false, v -> { if (service() != null) service().requestPause(); });
        resume = button(live, "Lanjutkan antrean", true, v -> requestResume());
        stop = button(live, "STOP sekarang", false, v -> { if (service() != null) service().stopByUser(); });
        stop.setTextColor(RED);
        pause.setContentDescription("Jeda setelah kontrak aktif selesai");stop.setContentDescription("STOP, hentikan pemeriksaan segera");
        resultsShortcut=button(live,"Lihat hasil",false,v -> selectPage(1));resultsShortcut.setVisibility(View.GONE);

        LinearLayout results = page(pageHost, 1);
        LinearLayout totals = card(results); sessionList = column(); totals.addView(sessionList);
        LinearLayout selector = new LinearLayout(this); selector.setGravity(Gravity.CENTER_VERTICAL); sessionList.addView(selector);
        sessionSelect=text("Belum ada sesi",14,MUTED,false); sessionSelect.setPadding(0,dp(8),dp(8),dp(8)); sessionSelect.setGravity(Gravity.CENTER_VERTICAL); sessionSelect.setMinHeight(dp(48));
        sessionSelect.setBackground(MufTheme.touch(this,Color.WHITE,12,false)); sessionSelect.setOnClickListener(v -> chooseDiagnosisSession());
        selector.addView(sessionSelect,new LinearLayout.LayoutParams(0,-2,1));
        sessionMenu=text("",24,MUTED,true);
        android.graphics.drawable.Drawable menuIcon=getDrawable(R.drawable.ic_more); menuIcon.setTint(MUTED);
        menuIcon.setBounds(0,0,dp(24),dp(24)); sessionMenu.setCompoundDrawables(menuIcon,null,null,null); sessionMenu.setPadding(dp(12),dp(12),dp(12),dp(12)); sessionMenu.setGravity(Gravity.CENTER); sessionMenu.setMinHeight(dp(48)); sessionMenu.setContentDescription("Menu sesi");
        selector.addView(sessionMenu,new LinearLayout.LayoutParams(dp(48),dp(48))); sessionMenu.setOnClickListener(v -> sessionActions());
        resultOverview=column(); totals.addView(resultOverview);
        gap(resultOverview,8); LinearLayout metrics=new LinearLayout(this); resultOverview.addView(metrics);
        paidCount=metric(metrics,"Sudah",GREEN,"Sudah"); unpaidCount=metric(metrics,"Belum",AMBER,"Belum"); errorCount=metric(metrics,"Error",RED,"Error");
        gap(resultOverview,12); summary = text("Belum ada sesi", 12, MUTED, false); resultOverview.addView(summary);
        resultOverview.setVisibility(searchText.isEmpty()?View.VISIBLE:View.GONE);
        gap(results, 16); search = new EditText(this); search.setSingleLine(true); search.setTextSize(14); search.setHint("Cari kontrak atau nama"); search.setContentDescription("Cari hasil berdasarkan kontrak atau nama"); search.setTextColor(NAVY); search.setHintTextColor(MUTED);
        search.setPadding(dp(14), dp(10), dp(14), dp(10)); search.setBackground(bg(BACK, 12, true)); search.setMinHeight(dp(52)); results.addView(search, new LinearLayout.LayoutParams(-1, -2));
        search.setText(searchText);
        search.addTextChangedListener(watcher(() -> {
            searchText = search.getText().toString();
            resultOverview.setVisibility(searchText.isEmpty()?View.VISIBLE:View.GONE); updateRetryVisibility();
            handler.removeCallbacks(searchRefresh); handler.postDelayed(searchRefresh, 180);
        }));
        LinearLayout filters = new LinearLayout(this); gap(results, 10); results.addView(filters); filterRow = filters;
        for (String value : new String[]{"Semua", "Belum", "Sudah", "Error"}) {
            TextView chip = text(value, 12, MUTED, true); chip.setGravity(Gravity.CENTER); chip.setMinHeight(dp(48)); chip.setTag(value);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.rightMargin = dp(4); filters.addView(chip, lp);
            chip.setOnClickListener(v -> applyFilter(value));
        }
        styleFilters();
        gap(results, 16);
        empty = text("Belum ada hasil. Siapkan pemeriksaan di tab Periksa.", 14, MUTED, false); results.addView(empty);
        retry = button(results, "Coba ulang ERROR", false, v -> retryErrors());
        gap(results,12);tableHeading=new ResultRow(true);results.addView(tableHeading);tableHeading.setVisibility(View.GONE);
        measureTableColumns(java.util.Collections.emptyList());
        resultAdapter = new ResultAdapter(); resultList.setAdapter(resultAdapter);
        resultList.setOnItemClickListener((parent, view, position, id) -> {
            int row = position - resultList.getHeaderViewsCount();
            if (row >= 0 && row < resultAdapter.getCount()) {
                ResultItem item = resultAdapter.getItem(row); showRow(item.source, item.sessionId);
            }
        });

        LinearLayout settings = page(pageHost, 2);
        LinearLayout access = card(settings); access.addView(text("Akses & layar",17,NAVY,true)); gap(access,8);
        connection = text("Memeriksa akses…", 14, MUTED, false); access.addView(connection);
        button(access, "Kelola akses", false, v -> explainAccessibility());
        LinearLayout awakeCard = access; gap(awakeCard,12);
        awake = new android.widget.Switch(this); awake.setText("Jaga layar menyala");
        awake.setTextColor(NAVY); awake.setTextSize(14); awake.setMinHeight(dp(48));
        awake.setChecked(prefs.getBoolean("keep_screen", true)); awakeCard.addView(awake);
        awake.setOnCheckedChangeListener((b, enabled) -> {
            if (running() && enabled != prefs.getBoolean("keep_screen", true)) { b.setChecked(prefs.getBoolean("keep_screen", true)); toast("Ubah setelah pemeriksaan selesai atau dijeda."); return; }
            prefs.edit().putBoolean("keep_screen", enabled).apply();
        });
        gap(settings,14); LinearLayout mode=card(settings); mode.addView(text("Pemeriksaan",17,NAVY,true)); gap(mode,8); mode.addView(text("Mode pemeriksaan",12,MUTED,false));
        modeButton=button(mode,"",false,v -> chooseMode()); updateModeLabel();
        gap(settings,14);LinearLayout info=card(settings);
        LinearLayout diagnosisHeader=new LinearLayout(this);diagnosisHeader.setGravity(Gravity.CENTER_VERTICAL);info.addView(diagnosisHeader);
        diagnosisHeader.addView(text("Diagnosis",17,NAVY,true),new LinearLayout.LayoutParams(0,-2,1));
        diagnosisChoice=text("Pilih sesi",13,GREEN,true);diagnosisChoice.setPadding(dp(12),dp(8),dp(12),dp(8));
        diagnosisChoice.setBackground(MufTheme.touch(this,Color.WHITE,12,true));diagnosisHeader.addView(diagnosisChoice,new LinearLayout.LayoutParams(-2,-2));
        diagnosisChoice.setMinHeight(dp(48));diagnosisChoice.setGravity(Gravity.CENTER);diagnosisChoice.setOnClickListener(v -> chooseDiagnosisSession());
        LinearLayout diagActions=new LinearLayout(this); info.addView(diagActions);
        Button viewDiag=button(diagActions, "Lihat", false, v -> loadDiagnosis(report -> showText("Diagnosis sesi", report)));
        Button copyDiag=button(diagActions, "Salin", false, v -> loadDiagnosis(report -> {
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Diagnosis sesi MUF Checker", report));
            toast("Diagnosis sesi disalin tanpa nomor kontrak, nama, atau nominal.");
        }));
        Button exportDiag=button(diagActions, "Ekspor", false, v -> exportDiagnosis());
        for(Button b:new Button[]{viewDiag,copyDiag,exportDiag}){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.topMargin=dp(6);if(b!=exportDiag)lp.rightMargin=dp(8);b.setLayoutParams(lp);}
        gap(settings,14); LinearLayout dataCard=card(settings); dataCard.addView(text("Data aplikasi",17,NAVY,true));
        Button clear=button(dataCard,"Hapus seluruh sesi",false,v -> clearData()); clear.setTextColor(RED);
        gap(settings, 24); TextView footer = text("MUF Checker · " + versionName() + "\n© Andika Pramudya 2026", 11, MUTED, false);
        footer.setGravity(Gravity.CENTER); settings.addView(footer);

        navigation=new android.widget.FrameLayout(this);navigation.setBackground(bg(Color.WHITE,28,true));
        navigationIndicator=new View(this);navigationIndicator.setBackground(bg(MufTheme.SOFT_GREEN,24,false));
        android.widget.FrameLayout.LayoutParams indicatorLp=new android.widget.FrameLayout.LayoutParams(0,0);indicatorLp.topMargin=dp(4);navigation.addView(navigationIndicator,indicatorLp);
        LinearLayout nav=new LinearLayout(this);navigationTabs=nav;nav.setPadding(dp(4),dp(4),dp(4),dp(4));navigation.addView(nav,new android.widget.FrameLayout.LayoutParams(-1,-2));
        navigation.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> { if(r-l!=or-ol || b-t!=ob-ot)syncNavigation(false); });
        for (int i = 0; i < 3; i++) { final int index = i; tabs[i] = text(new String[]{"Periksa", "Hasil", "Pengaturan"}[i], 12, MUTED, true);
            android.graphics.drawable.Drawable icon=getDrawable(new int[]{R.drawable.ic_check,R.drawable.ic_results,R.drawable.ic_settings}[i]);
            icon.setBounds(0,0,dp(24),dp(24)); tabs[i].setCompoundDrawables(null,icon,null,null); tabs[i].setCompoundDrawablePadding(dp(4));
            tabs[i].setPadding(dp(4),dp(8),dp(4),dp(8));
            tabs[i].setGravity(Gravity.CENTER); tabs[i].setMinHeight(dp(64)); tabs[i].setOnClickListener(v -> selectPage(index)); tabs[i].setBackground(MufTheme.touch(this,Color.TRANSPARENT,24,false));nav.addView(tabs[i],new LinearLayout.LayoutParams(0,-1,1)); }
        LinearLayout.LayoutParams navLp=new LinearLayout.LayoutParams(-1,-2);navLp.setMargins(dp(20),dp(8),dp(20),dp(12));shell.addView(navigation,navLp);
        setContentView(shell);shell.requestApplyInsets();navigation.post(() -> syncNavigation(false));
    }
    private LinearLayout filterRow;
    private void applyFilter(String value) { filter = value; styleFilters(); renderTable(); }
    /** Chip look and "Belum 8" counts; matching always uses the tag, never the visible text. */
    private void styleFilters() {
        if (filterRow == null) return;
        int paid = 0, unpaid = 0, errors = 0;
        if (viewQueue != null) for (BulkQueue.Row r : viewQueue.rows()) {
            String s = status(r); if (s.equals("SUDAH BAYAR")) paid++; else if (s.equals("BELUM BAYAR")) unpaid++; else if (s.equals("ERROR")) errors++;
        }
        for (int i = 0; i < filterRow.getChildCount(); i++) {
            TextView chip = (TextView) filterRow.getChildAt(i); String value = (String) chip.getTag(); boolean chosen = value.equals(filter);
            int n = value.equals("Sudah") ? paid : value.equals("Belum") ? unpaid : value.equals("Error") ? errors : -1;
            chip.setText(n > 0 ? value + " " + n : value); chip.setSelected(chosen);
            chip.setContentDescription(value + (n >= 0 ? ", " + n + " kontrak" : "") + (chosen ? ", dipilih" : ""));
            chip.setBackground(MufTheme.touch(this, chosen ? MufTheme.SOFT_GREEN : Color.WHITE, 10, !chosen)); chip.setTextColor(chosen ? GREEN : MUTED);
        }
    }
    /** Count-up only while the Hasil tab is visible; otherwise the number just changes. */
    private void setCount(TextView view, int to) {
        Object running = view.getTag();
        if (running instanceof android.animation.ValueAnimator) ((android.animation.ValueAnimator) running).cancel();
        int from; try { from = Integer.parseInt(view.getText().toString()); } catch (NumberFormatException e) { from = to; }
        if (from == to || selectedPage != 1 || !MufTheme.motionOn()) { view.setText(Integer.toString(to)); return; }
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofInt(from, to).setDuration(MufTheme.MOTION_SLOW);
        a.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
        a.addUpdateListener(x -> view.setText(Integer.toString((Integer) x.getAnimatedValue())));
        a.addListener(new android.animation.AnimatorListenerAdapter() { @Override public void onAnimationEnd(android.animation.Animator an) { view.setText(Integer.toString(to)); } });
        view.setTag(a); a.start();
    }
    private TextWatcher watcher(Runnable changed) { return new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
        @Override public void afterTextChanged(Editable e) { changed.run(); }
    }; }
    private void selectPage(int index) {
        boolean changed = selectedPage != index;
        selectedPage = index >= 0 && index < 3 ? index : 0;
        if (changed) ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(contract.getWindowToken(), 0);
        for (int i = 0; i < 3; i++) { pages[i].animate().cancel(); pages[i].setAlpha(1f); pages[i].setTranslationY(0); pages[i].setVisibility(i == selectedPage ? View.VISIBLE : View.GONE);
            tabs[i].setSelected(i == selectedPage); tabs[i].setContentDescription(tabs[i].getText() + (i == selectedPage ? ", dipilih" : ""));
            tabs[i].setTextColor(i == selectedPage ? GREEN : MUTED);
            tabs[i].getCompoundDrawables()[1].setTint(i == selectedPage ? GREEN : MUTED); }
        syncNavigation(changed);
        if (selectedPage == 1 && tableDirty) renderTable();
        if (changed && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            pages[selectedPage].animate().cancel(); pages[selectedPage].setAlpha(.8f);
            pages[selectedPage].setTranslationY(dp(4));
            pages[selectedPage].animate().alpha(1f).translationY(0).setDuration(MufTheme.MOTION_ENTER)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        }
    }
    /** Purely visual selection movement; never schedules queue work. */
    private void syncNavigation(boolean animate) {
        if(navigation==null || navigationTabs==null)return;
        boolean moving=animate && resumed && android.animation.ValueAnimator.areAnimatorsEnabled();
        for(int i=0;i<tabs.length;i++)if(tabs[i]!=null){
            tabs[i].animate().cancel();float scale=1f;
            if(moving)tabs[i].animate().scaleX(scale).scaleY(scale).setDuration(MufTheme.MOTION_ENTER).start();
            else {tabs[i].setScaleX(scale);tabs[i].setScaleY(scale);}
        }
        if(navigationTabs.getWidth()==0 || tabs[selectedPage].getWidth()==0)return;
        android.widget.FrameLayout.LayoutParams lp=(android.widget.FrameLayout.LayoutParams)navigationIndicator.getLayoutParams();
        int width=tabs[selectedPage].getWidth(),height=navigationTabs.getHeight()-dp(8);
        if(lp.width!=width || lp.height!=height){lp.width=width;lp.height=height;navigationIndicator.setLayoutParams(lp);}
        float target=tabs[selectedPage].getLeft();navigationIndicator.animate().cancel();
        if(moving)navigationIndicator.animate().translationX(target).setDuration(MufTheme.MOTION_ENTER).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        else navigationIndicator.setTranslationX(target);
    }
    private void countInput() {
        LinkedHashSet<String> valid = new LinkedHashSet<>(); int duplicate = 0, invalid = 0, lineNo = 0; StringBuilder badLines = new StringBuilder();
        for (String raw : contract.getText().toString().replaceFirst("^\uFEFF", "").split("\\r?\\n")) {
            lineNo++; String value = raw.trim(); if (value.isEmpty()) continue;
            if (!InquiryRules.validContract(value)) { invalid++; if (invalid <= 5) badLines.append(invalid > 1 ? ", " : "").append(lineNo); }
            else if (!valid.add(value)) duplicate++;
        }
        inputCount.setText(valid.size() + " kontrak berbeda · Maksimal " + BulkQueue.LIMIT + (duplicate > 0 ? " · " + duplicate + " duplikat" : "")
            + (invalid > 0 ? " · " + invalid + " tidak valid (baris " + badLines + (invalid > 5 ? ", …" : "") + ")" : ""));
        inputCount.setTextColor(invalid > 0 || valid.size() > BulkQueue.LIMIT ? RED : MUTED);
    }
    private String periodLabel(YearMonth month) { return month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("id-ID"))); }
    private TokopediaService service() { return TokopediaService.instance; }
    private boolean running() { TokopediaService live = service(); return live != null && live.isRunning(); }
    private void requestStart() {
        if (running() || busy) return;
        if (revision.equals("!")) { toast("Tunggu hingga data sesi selesai dimuat."); return; }
        if (queue != null && queue.hasUnfinished()) { toast("Lanjutkan antrean tersimpan atau hapus sesi tersebut di Hasil sebelum membuat sesi baru."); return; }
        BulkQueue.Input input;
        try { input = BulkQueue.parseLines(contract.getText().toString()); } catch (IllegalArgumentException e) { contract.setError(e.getMessage()); return; }
        if (service() == null) { explainAccessibility(); return; }
        prefs.edit().putString("last_contracts", String.join("\n", input.contracts)).putString("selected_period", period.toString()).apply();
        if (input.duplicates > 0) toast(input.duplicates + " duplikat diabaikan.");
        try { service().beginBatch(input.contracts, period, startMode); } catch (RuntimeException e) { toast("Antrean belum dapat disimpan/dibuka. Hasil sebelumnya dipertahankan."); }
        update();
    }
    private void requestResume() {
        if (busy || loading) return;
        if (service() == null) { explainAccessibility(); return; }
        if (queue == null) return;
        period = queue.period; periodView.setText(periodLabel(period));
        if (!service().resumeBatch()) toast("Antrean belum dapat dilanjutkan. Periksa koneksi dan penyimpanan."); update();
    }
    private void retryErrors() {
        if (running() || busy || queue == null || queue.errorCount() == 0) return;
        showDialog(new AlertDialog.Builder(this).setTitle("Periksa ulang error?")
            .setMessage(queue.errorCount() + " kontrak akan disiapkan ulang. Hasil yang berhasil tetap tersimpan.")
            .setNegativeButton("Batal", null).setPositiveButton("Siapkan ulang", (d, w) -> {
                busy = true;
                repository.submit(database -> {
                    if (running()) throw new IllegalStateException("Sesi sedang berjalan");
                    BulkQueue saved = BulkQueue.restore(database.read("batch"));
                    if (saved.recoverInterrupted()) database.save(saved, null, null, null, false);
                    if (saved.retryErrors()) database.save(saved, null, null, null, false);
                    return true;
                }, done -> { busy = false; revision = "!"; update(); selectPage(0); }, e -> { busy = false; toast("Antrean belum dapat diperbarui."); });
            }));
    }
    private void update() {
        if (repository == null || loading || busy || !resumed) return;
        loading = true;
        repository.load(revision, data -> {
            loading = false;
            if(!resumed){revision="!";return;}
            if (data != null) {
                revision = data.revision; queue = data.current; history = data.sessions;
                if (queue != null && !queue.sessionId().equals(latestSession)) {
                    if (!latestSession.isEmpty() || selectedSession.isEmpty()) selectedSession = queue.sessionId();
                    latestSession = queue.sessionId();
                }
                refreshHistory();
            }
            boolean active = running(), pending = queue != null && queue.hasUnfinished();
            boolean preparing = service() != null && service().isPreparing();
            start.setEnabled(!active && !pending && !busy); start.setAlpha(start.isEnabled() ? 1f : .5f);
            contract.setEnabled(!active && !pending && !busy); periodButton.setEnabled(!active && !pending && !busy);
            modeButton.setEnabled(!active && !pending && !busy); awake.setEnabled(!active);
            pause.setVisibility(active && !preparing ? View.VISIBLE : View.GONE); pause.setEnabled(queue == null || !queue.pauseRequested());
            stop.setText(preparing ? "Batalkan persiapan" : "STOP sekarang");
            stop.setVisibility(active ? View.VISIBLE : View.GONE); resume.setVisibility(!active && pending ? View.VISIBLE : View.GONE);
            resume.setText(queue != null && queue.startMode() == BulkQueue.StartMode.READY_PAGE ? "Siapkan ulang panel Mulai" : "Lanjutkan antrean");
            updateRetryVisibility();
            connection.setText(service() != null ? "● Aksesibilitas tersambung" : "○ Aksesibilitas belum tersambung");
            connection.setTextColor(service() != null ? GREEN : AMBER);
            state.setText(preparing ? "Menunggu Mulai di Tokopedia" : queue==null ? "Belum ada pemeriksaan" : !active && pending ? (queue.state() == BulkQueue.RunState.STOPPED ? "Pemeriksaan dihentikan" : "Antrean dijeda")
                : !active && queue != null && queue.state() == BulkQueue.RunState.COMPLETED ? "Pemeriksaan selesai"
                : prefs.getString("progress_short", "Siap memeriksa"));
            String note = preparing ? "Angsuran Kredit · MUF" : !active && pending && !queue.reason().isEmpty() ? "Ketuk untuk melihat alasan jeda." : "";
            activityNote.setContentDescription(!active && pending ? queue.reason() : note);
            activityNote.setText(note); activityNote.setVisibility(note.isEmpty() ? View.GONE : View.VISIBLE);
            int maximum = queue == null ? 1 : queue.rows().size(), value = queue == null ? 0 : queue.completedCount();
            activityProgress.setVisibility(queue==null?View.GONE:View.VISIBLE);
            resultsShortcut.setVisibility(queue!=null && queue.completedCount()>0?View.VISIBLE:View.GONE);
            String eta = queue == null || !active ? "" : UiPresentation.eta(queue.activeMs(), value, maximum);
            progressLabel.setText(queue == null ? "" : value + "/" + maximum + " selesai · " + (value * 100 / maximum) + "%" + (eta.isEmpty() ? "" : " · " + eta));
            progress.setContentDescription(progressLabel.getText());
            if (progress.getMax() != maximum) progress.setMax(maximum);
            if (progress.getProgress() != value) progress.setProgress(value,selectedPage==0 && android.animation.ValueAnimator.areAnimatorsEnabled());
        }, error -> {
            loading = false; if(!resumed){revision="!";return;} state.setText("Data belum dapat dibaca. Jangan mulai antrean baru; tutup dan buka kembali aplikasi.");
            revision = "!"; start.setEnabled(false); resume.setVisibility(View.GONE);
        });
    }
    private String sessionLabel(BulkQueue q) {
        String when = q.createdAt() == 0 ? "Sesi lama v0.4.0" : java.time.Instant.ofEpochMilli(q.createdAt()).atZone(ZoneId.of("Asia/Makassar"))
            .format(DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm:ss", Locale.forLanguageTag("id-ID")));
        String state = q.state() == BulkQueue.RunState.COMPLETED ? "Selesai" : q.state() == BulkQueue.RunState.PREPARED ? "Disiapkan"
            : q.state() == BulkQueue.RunState.RUNNING ? "Berjalan" : q.state() == BulkQueue.RunState.STOPPED ? "Dihentikan" : "Dijeda";
        return when + "\n" + q.rows().size() + " kontrak · " + state;
    }
    private void sessionActions() {
        if (viewQueue == null) return;
        final BulkQueue selectedQueue=viewQueue;
        boolean activeSession=running() && queue!=null && queue.sessionId().equals(selectedQueue.sessionId());
        String[] actions=activeSession?new String[]{"Pilih sesi"}:new String[]{"Pilih sesi","Hapus sesi ini"};
        showDialog(new AlertDialog.Builder(this).setTitle("Sesi pemeriksaan").setItems(actions,(dialog,which)->{
            if(which==0)chooseDiagnosisSession();else deleteSession(selectedQueue);
        }));
    }
    private void refreshHistory() {
        viewQueue = null;
        for (BulkQueue q : history) if (q.sessionId().equals(selectedSession)) viewQueue = q;
        if (viewQueue == null && !history.isEmpty()) { viewQueue = history.get(0); selectedSession = viewQueue.sessionId(); }
        String when = viewQueue == null ? "Belum ada sesi" : viewQueue.createdAt() <= 0 ? "Sesi lama v0.4.0" :
            java.time.Instant.ofEpochMilli(viewQueue.createdAt()).atZone(ZoneId.of("Asia/Makassar")).format(DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm", Locale.forLanguageTag("id-ID")));
        sessionSelect.setText(when);
        sessionSelect.setEnabled(viewQueue!=null);
        sessionSelect.setContentDescription(when + (viewQueue==null?"":", ketuk untuk memilih sesi"));
        sessionMenu.setEnabled(viewQueue!=null);
        summary.setText(viewQueue == null ? "Belum ada sesi" : viewQueue.completedCount() + "/" + viewQueue.rows().size() + " selesai · " + UiPresentation.duration(viewQueue.activeMs()));
        int paid = 0, unpaid = 0;
        if (viewQueue != null) for (BulkQueue.Row r : viewQueue.rows()) { if (status(r).equals("SUDAH BAYAR")) paid++; if (status(r).equals("BELUM BAYAR")) unpaid++; }
        setCount(paidCount, paid); setCount(unpaidCount, unpaid); setCount(errorCount, viewQueue==null?0:viewQueue.errorCount());
        styleFilters();
        diagnosisChoice.setText("Pilih sesi");
        diagnosisChoice.setContentDescription(viewQueue==null?"Pilih sesi diagnosis":"Pilih sesi diagnosis, terpilih " + when);
        updateRetryVisibility();
        String nextTableKey = UiPresentation.tableKey(viewQueue);
        if (!nextTableKey.equals(tableKey)) { tableKey = nextTableKey; tableDirty=true; if(selectedPage==1)renderTable(); }
    }
    private void updateRetryVisibility() {
        if (retry != null) retry.setVisibility(searchText.isEmpty() && !running() && queue != null
            && queue.sessionId().equals(selectedSession) && queue.errorCount() > 0 ? View.VISIBLE : View.GONE);
    }
    private void deleteSession(BulkQueue q) {
        if (busy || running() && queue != null && queue.sessionId().equals(q.sessionId())) return;
        showDialog(new AlertDialog.Builder(this).setTitle("Hapus sesi ini?").setMessage("Hasil dan diagnosis sesi ini akan dihapus.\n\n" + sessionLabel(q))
            .setNegativeButton("Batal", null).setPositiveButton("Hapus", (d, w) -> {
                busy = true;
                repository.submit(database -> {
                    String current = database.read("batch"); boolean latest = !current.isEmpty() && BulkQueue.restore(current).sessionId().equals(q.sessionId());
                    database.deleteSession(q.sessionId()); return latest;
                }, latest -> { busy = false; selectedSession = ""; if (latest) prefs.edit().remove("diagnostic").remove("progress").apply(); revision = "!"; update(); },
                    e -> { busy = false; toast("Sesi belum dapat dihapus. Hentikan pemeriksaan dahulu."); });
            }));
    }
    private void chooseDiagnosisSession() {
        if (history.isEmpty()) { toast("Belum ada sesi."); return; }
        final java.util.List<BulkQueue> choices = new java.util.ArrayList<>(history);
        String[] labels = new String[choices.size()]; for (int i = 0; i < labels.length; i++) labels[i] = sessionLabel(choices.get(i));
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            @Override public View getView(int position, View reusable, android.view.ViewGroup parent) {
                LinearLayout item = reusable instanceof LinearLayout ? (LinearLayout) reusable : null;
                if (item == null) {
                    item = column(); item.setPadding(dp(24), dp(14), dp(24), dp(14)); item.setMinimumHeight(dp(72));
                    item.addView(text("",14,NAVY,true)); item.addView(text("",12,MUTED,false));
                }
                String[] lines = getItem(position).split("\n",2);
                boolean selected = choices.get(position).sessionId().equals(selectedSession);
                item.setBackgroundColor(selected ? MufTheme.SOFT_GREEN : Color.WHITE); item.setSelected(selected);
                ((TextView)item.getChildAt(0)).setText(lines[0] + (selected ? "  ✓" : ""));
                ((TextView)item.getChildAt(1)).setText(lines.length > 1 ? lines[1] : "");
                item.setContentDescription(getItem(position) + (selected ? ", dipilih" : ""));
                return item;
            }
        };
        AlertDialog dialog = showDialog(new AlertDialog.Builder(this).setTitle("Pilih sesi").setAdapter(adapter, (d, which) -> {
            selectedSession = choices.get(which).sessionId(); refreshHistory(); resultList.setSelection(0);
        }).setNegativeButton("Tutup", null));
        if (dialog != null) {
            dialog.getListView().setDivider(new android.graphics.drawable.InsetDrawable(
                new android.graphics.drawable.ColorDrawable(MufTheme.BORDER),dp(24),0,dp(24),0));
            dialog.getListView().setDividerHeight(dp(1)); dialog.getListView().setFooterDividersEnabled(false);
        }
    }
    private void exportDiagnosis() {
        if (!pendingExport.isEmpty()) { toast("Selesaikan penyimpanan sebelumnya dahulu."); return; }
        loadDiagnosis(report -> {
            busy = true;
            repository.submit(database -> {
                java.io.File file = java.io.File.createTempFile("diagnosis-", ".txt", getCacheDir());
                java.nio.file.Files.write(file.toPath(), report.getBytes(java.nio.charset.StandardCharsets.UTF_8)); return file.getAbsolutePath();
            }, path -> {
                busy = false; pendingExport = path;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TITLE, "MUF-Diagnosis-" + java.time.ZonedDateTime.now(ZoneId.of("Asia/Makassar")).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
                try { startActivityForResult(intent, 501); } catch (RuntimeException e) { discardExport(); toast("Pemilih lokasi penyimpanan belum tersedia."); }
            }, e -> { busy = false; toast("Diagnosis belum dapat diekspor."); });
        });
    }
    private void loadDiagnosis(AppRepository.Success<String> success) {
        if (busy || viewQueue == null) { toast("Pilih sesi atau tunggu proses sebelumnya selesai."); return; }
        if (running() && queue != null && queue.sessionId().equals(selectedSession)) { toast("Jeda pemeriksaan sebelum membuka diagnosis lengkap sesi aktif."); return; }
        final String id = selectedSession; busy = true;
        repository.submit(database -> {
            BulkQueue saved = database.session(id); if (saved == null) throw new IllegalStateException("Sesi tidak ditemukan");
            return database.exportDiagnosis(saved);
        }, report -> { busy = false; success.accept(report); }, e -> { busy = false; toast("Diagnosis sesi belum dapat dibaca."); });
    }
    private void discardExport() {
        final String path = pendingExport; pendingExport = "";
        if (path.isEmpty()) return;
        repository.submit(database -> { java.io.File file = checkedExport(path); return file.delete(); }, done -> {}, e -> {});
    }
    private java.io.File checkedExport(String path) throws java.io.IOException {
        java.io.File file = new java.io.File(path).getCanonicalFile();
        if (!file.getParentFile().equals(getCacheDir().getCanonicalFile()) || !file.getName().startsWith("diagnosis-")) throw new java.io.IOException("Berkas ekspor tidak valid");
        return file;
    }
    @Override protected void onActivityResult(int request, int code, Intent data) {
        super.onActivityResult(request, code, data);
        if (request != 501) return;
        if (pendingExport.isEmpty()) { toast("Berkas diagnosis tidak tersedia. Coba ekspor kembali."); return; }
        if (code == RESULT_OK && data != null && data.getData() != null) {
            final String path = pendingExport; final android.net.Uri target = data.getData(); pendingExport = ""; busy = true;
            repository.submit(database -> {
                java.io.File pending = checkedExport(path);
                try (java.io.OutputStream output = getContentResolver().openOutputStream(target, "wt")) {
                    if (output == null) throw new java.io.IOException("Tidak ada tujuan");
                    output.write(java.nio.file.Files.readAllBytes(pending.toPath())); output.flush(); return true;
                } finally { pending.delete(); }
            }, done -> { busy = false; toast("Diagnosis sesi disimpan."); }, e -> { busy = false; toast("Penyimpanan gagal. Coba ekspor kembali."); });
        } else discardExport();
    }
    private String formatResult(String raw) {
        try { JSONObject j = new JSONObject(raw), fields = j.getJSONObject("details");
            return ResultFormatter.compact(j.getString("status"), j.getString("contract"), fields.optString("nama", ""), fields.optString("cicilan ke", ""), fields.optString("tagihan", ""), j.optString("checked", ""));
        } catch (Exception e) { return "Hasil tersimpan tidak dapat dibaca."; }
    }
    private String detailedResult(String raw) {
        try { JSONObject j = new JSONObject(raw); return formatResult(raw) + "\n\nJatuh tempo: " + LocalDate.parse(j.getString("due")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            + "\nPeriode: " + periodLabel(YearMonth.parse(j.getString("period"))) + "\n\n" + j.getString("reason") + "\nSumber: " + j.optString("source", "Tokopedia");
        } catch (Exception e) { return "Rincian belum dapat dibaca."; }
    }
    private String status(BulkQueue.Row row) {
        if (row.state() == BulkQueue.RowState.WAITING) return "MENUNGGU";
        if (row.state() == BulkQueue.RowState.PROCESSING) return "DIPROSES";
        if (row.state() == BulkQueue.RowState.ERROR) return "ERROR";
        try { return ResultFormatter.status(new JSONObject(row.result()).getString("status")); } catch (Exception e) { return "ERROR"; }
    }
    private static final class ResultItem {
        final BulkQueue.Row source;
        final int ordinal;
        final String sessionId, name, due, status;
        ResultItem(String sessionId, BulkQueue.Row source, int ordinal, String name, String due, String status) {
            this.sessionId=sessionId; this.source=source; this.ordinal=ordinal; this.name=name; this.due=due; this.status=status;
        }
    }
    private void renderTable() {
        if (resultAdapter == null) return;
        tableDirty = false;
        final int generation = ++renderGeneration;
        final BulkQueue snapshot = viewQueue;
        final String chosenFilter = filter, query = searchText.trim().toLowerCase(Locale.ROOT);
        repository.submit(database -> {
            java.util.List<ResultItem> items = new java.util.ArrayList<>();
            if (snapshot != null) {
                int index = 0;
                for (BulkQueue.Row row : snapshot.rows()) {
                    index++; String name = "—", due = "—", label = status(row);
                    try { if (row.state() == BulkQueue.RowState.SUCCESS) {
                        JSONObject j = new JSONObject(row.result());
                        name = ResultFormatter.cleanName(j.getJSONObject("details").optString("nama", ""));
                        due = LocalDate.parse(j.getString("due")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                    } } catch (Exception ignored) { }
                    if (!UiPresentation.matchesResult(chosenFilter, query, row.contract, name, label)) continue;
                    items.add(new ResultItem(snapshot.sessionId(), row, index, name, due, label));
                }
            }
            return items;
        }, items -> {
            if (generation != renderGeneration) return;
            // ListView reuses visible rows, keeps its scroll anchor, and never fades the entire list.
            measureTableColumns(items);
            resultAdapter.items = items; resultAdapter.notifyDataSetChanged();
            empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);tableHeading.setVisibility(items.isEmpty()?View.GONE:View.VISIBLE);
            empty.setText(snapshot == null ? "Belum ada hasil. Siapkan pemeriksaan di tab Periksa." : "Tidak ada hasil yang sesuai pencarian atau filter.");
        }, error -> { if (generation == renderGeneration) { tableDirty=true; toast("Hasil belum dapat ditampilkan."); } });
    }
    private final class ResultAdapter extends BaseAdapter {
        java.util.List<ResultItem> items = new java.util.ArrayList<>();
        @Override public int getCount() { return items.size(); }
        @Override public ResultItem getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).ordinal; }
        @Override public boolean hasStableIds() { return true; }
        @Override public View getView(int position, View reusable, android.view.ViewGroup parent) {
            ResultRow item = reusable instanceof ResultRow ? (ResultRow)reusable : new ResultRow(false);
            item.bind(getItem(position));
            return item;
        }
    }
    /** Measure displayed text once per dataset; natural widths never depend on the physical screen. */
    private void measureTableColumns(java.util.List<ResultItem> items) {
        TextView[] labels={tableHeading.ordinal,tableHeading.number,tableHeading.name,tableHeading.badge};
        String[] titles={"100","Kontrak","Nama","Status"};
        int[] next=new int[4];
        for(int i=0;i<4;i++)next[i]=(int)Math.ceil(labels[i].getPaint().measureText(titles[i]))+dp(24);
        next[1]=Math.max(next[1],dp(136));next[2]=Math.max(next[2],dp(180));next[3]=Math.max(next[3],dp(72));
        for(ResultItem item:items){
            String[] values={Integer.toString(item.ordinal),item.source.contract,item.name,UiPresentation.tableStatusLabel(item.status)};
            for(int i=0;i<4;i++)next[i]=Math.max(next[i],(int)Math.ceil(labels[i].getPaint().measureText(values[i]))+dp(24));
        }
        if(!java.util.Arrays.equals(tableColumns,next)){
            System.arraycopy(next,0,tableColumns,0,4);tableHeading.requestLayout();
        }
    }
    /** Share horizontal position only; ListView retains ownership of vertical scrolling and recycling. */
    private void syncTableScroll(int x) {
        if(syncingTableScroll)return;
        syncingTableScroll=true;tableScrollX=x;
        tableHeading.scrollTo(x,0);
        for(int i=0;i<resultList.getChildCount();i++){
            View row=resultList.getChildAt(i);if(row instanceof ResultRow)row.scrollTo(x,0);
        }
        syncingTableScroll=false;
    }
    /** Native horizontal scrolling keeps every identity on one line, with a shared header/row offset. */
    private final class ResultRow extends android.widget.HorizontalScrollView {
        final TextView ordinal=text("",12,MUTED,false),number=text("",13,NAVY,true);
        final TextView name=text("",13,MUTED,false),badge=text("",13,NAVY,true);
        final Cells cells;
        private final boolean heading;
        private int viewport;
        private boolean layingOut;
        private ResultItem data;
        ResultRow(boolean heading) {
            super(MainActivity.this);this.heading=heading;
            setHorizontalScrollBarEnabled(heading);setOverScrollMode(View.OVER_SCROLL_NEVER);
            setClipToPadding(true);cells=new Cells();addView(cells,new android.widget.FrameLayout.LayoutParams(-2,-2));
            setMinimumHeight(dp(heading?40:56));
            cells.setBackground(heading?bg(MufTheme.SOFT_NEUTRAL,0,false):MufTheme.touch(MainActivity.this,Color.WHITE,0,false));
            for(TextView child:new TextView[]{ordinal,number,name,badge}) {
                child.setPadding(dp(12),dp(10),dp(12),dp(10));child.setGravity(Gravity.CENTER_VERTICAL);
                child.setSingleLine(true);child.setEllipsize(null);
                child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);cells.addView(child);
            }
            ordinal.setGravity(Gravity.CENTER);badge.setGravity(Gravity.CENTER);
            cells.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            if(heading){ordinal.setText("No");number.setText("Kontrak");name.setText("Nama");badge.setText("Status");
                name.setTextColor(NAVY);if(android.os.Build.VERSION.SDK_INT>=28)setAccessibilityHeading(true);setContentDescription("Kolom No, Kontrak, Nama, Status");}
            else {
                setOnClickListener(v -> {if(data!=null)showRow(data.source,data.sessionId);});
                cells.setOnClickListener(v -> performClick());
            }
        }
        void bind(ResultItem data) {
            this.data=data;
            ordinal.setText(Integer.toString(data.ordinal));number.setText(data.source.contract);name.setText(data.name);
            badge.setText(UiPresentation.tableStatusLabel(data.status));
            boolean paid="SUDAH BAYAR".equals(data.status),unpaid="BELUM BAYAR".equals(data.status),failed="ERROR".equals(data.status);
            badge.setTextColor(failed?RED:paid?GREEN:unpaid?AMBER:MUTED);
            badge.setBackground(failed?MufTheme.chip(MainActivity.this,MufTheme.SOFT_RED):paid?MufTheme.chip(MainActivity.this,MufTheme.SOFT_GREEN)
                :unpaid?MufTheme.chip(MainActivity.this,MufTheme.SOFT_AMBER):null);
            badge.setPadding(dp(12),dp(10),dp(12),dp(10));
            setContentDescription("Hasil " + data.ordinal + ", kontrak " + data.source.contract + ", nama " + data.name + ", "
                + UiPresentation.statusLabel(data.status) + ", ketuk untuk rincian");
        }
        @Override protected void onMeasure(int widthSpec,int heightSpec) {
            viewport=MeasureSpec.getSize(widthSpec);super.onMeasure(widthSpec,heightSpec);
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
            layingOut=true;super.onLayout(changed,l,t,r,b);
            int clamped=Math.min(tableScrollX,Math.max(0,cells.getMeasuredWidth()-getWidth()));scrollTo(clamped,0);layingOut=false;
            if(clamped!=tableScrollX)syncTableScroll(clamped);
        }
        @Override protected void onScrollChanged(int x,int y,int oldX,int oldY) {
            super.onScrollChanged(x,y,oldX,oldY);if(!layingOut && !syncingTableScroll && tableHeading!=null)syncTableScroll(x);
        }
        @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){
            if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){
                if(flingOwner!=null)flingOwner.stopFling();flingOwner=this;
            }
            return super.dispatchTouchEvent(event);
        }
        private void stopFling(){super.fling(0);}
        @Override protected void onDetachedFromWindow(){
            if(flingOwner==this){stopFling();flingOwner=null;}super.onDetachedFromWindow();
        }
        private final class Cells extends android.view.ViewGroup {
            private final android.graphics.Paint grid=new android.graphics.Paint();
            private final int[] widths=new int[4];
            Cells(){super(MainActivity.this);setWillNotDraw(false);grid.setColor(MufTheme.BORDER);grid.setStrokeWidth(dp(1));}
            @Override protected void onMeasure(int widthSpec,int heightSpec){
                int width=0,height=dp(heading?40:56);
                for(int i=0;i<4;i++){widths[i]=tableColumns[i];width+=widths[i];}
                if(width<viewport){widths[2]+=viewport-width;width=viewport;}
                for(int i=0;i<4;i++){
                    View child=getChildAt(i);child.measure(MeasureSpec.makeMeasureSpec(widths[i],MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
                    height=Math.max(height,child.getMeasuredHeight());
                }
                for(int i=0;i<4;i++)getChildAt(i).measure(MeasureSpec.makeMeasureSpec(widths[i],MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));
                setMeasuredDimension(width,height);
            }
            @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
                int x=0;for(int i=0;i<4;i++){getChildAt(i).layout(x,0,x+widths[i],getMeasuredHeight());x+=widths[i];}
            }
            @Override protected void dispatchDraw(android.graphics.Canvas canvas){
                super.dispatchDraw(canvas);int x=0;float half=grid.getStrokeWidth()/2;
                canvas.drawLine(half,0,half,getHeight(),grid);
                for(int i=0;i<4;i++){x+=widths[i];canvas.drawLine(Math.min(x,getWidth()-half),0,Math.min(x,getWidth()-half),getHeight(),grid);}
                if(heading)canvas.drawLine(0,half,getWidth(),half,grid);
                canvas.drawLine(0,getHeight()-half,getWidth(),getHeight()-half,grid);
            }
        }
    }
    private void detailValue(LinearLayout parent,String label,String value) {
        parent.addView(text(label,11,MUTED,false));
        TextView data=text(value==null||value.isEmpty()?"—":value,14,NAVY,true);
        data.setTextIsSelectable(true);parent.addView(data);
    }
    private void detailPair(LinearLayout parent,String firstLabel,String first,String secondLabel,String second) {
        LinearLayout pair=new LinearLayout(this);parent.addView(pair);
        LinearLayout left=column(),right=column();
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.rightMargin=dp(12);
        pair.addView(left,lp);pair.addView(right,new LinearLayout.LayoutParams(0,-2,1));
        detailValue(left,firstLabel,first);detailValue(right,secondLabel,second);
    }
    /** Parsing is performed through AppRepository's worker; this model contains presentation text only. */
    private static final class RowDetails {
        String name="",due="",period="",amount="",installment="",note="";
        boolean verified;
    }
    private void showRow(BulkQueue.Row row, final String sessionId) {
        repository.submit(database -> {
            RowDetails data=new RowDetails();
            if(row.state()==BulkQueue.RowState.SUCCESS){try{
                JSONObject json=new JSONObject(row.result()),details=json.getJSONObject("details");
                data.name=ResultFormatter.cleanName(details.optString("nama",""));
                data.due=LocalDate.parse(json.getString("due")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                data.period=periodLabel(YearMonth.parse(json.getString("period")));
                data.amount=details.optString("tagihan","");data.installment=details.optString("cicilan ke","");data.verified=true;
            }catch(Exception error){data.note="Rincian tersimpan belum dapat dibaca.";}}
            else data.note=row.reason().isEmpty()?"Belum ada hasil terverifikasi.":row.reason();
            return data;
        },data -> presentRow(row,sessionId,data),error -> toast("Rincian belum dapat dibaca."));
    }
    private void presentRow(BulkQueue.Row row,String sessionId,RowDetails data) {
        ScrollView scroll=new ScrollView(this);LinearLayout content=column();content.setPadding(dp(20),dp(8),dp(20),dp(8));scroll.addView(content);
        String stateLabel=status(row);boolean failed="ERROR".equals(stateLabel),paid="SUDAH BAYAR".equals(stateLabel);
        boolean unpaid="BELUM BAYAR".equals(stateLabel);
        TextView statusChip=text(UiPresentation.statusLabel(stateLabel),13,failed?RED:paid?GREEN:unpaid?AMBER:NAVY,true);
        statusChip.setPadding(dp(12),dp(8),dp(12),dp(8));statusChip.setBackground(bg(failed?MufTheme.SOFT_RED:paid?MufTheme.SOFT_GREEN:unpaid?MufTheme.SOFT_AMBER:MufTheme.SOFT_NEUTRAL,10,false));
        content.addView(statusChip,new LinearLayout.LayoutParams(-2,-2));gap(content,12);
        LinearLayout identity=column();identity.setPadding(dp(12),dp(12),dp(12),dp(12));identity.setBackground(bg(BACK,12,false));content.addView(identity);
        detailValue(identity,"Kontrak",row.contract);
        if(!data.name.isEmpty()){gap(identity,10);detailValue(identity,"Nama",data.name);}
        if(data.verified){
            gap(content,16);detailPair(content,"Jatuh tempo",data.due,"Periode",data.period);
            gap(content,12);View divider=new View(this);divider.setBackgroundColor(MufTheme.BORDER);content.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
            gap(content,12);detailPair(content,"Angsuran",data.amount,"Cicilan ke",data.installment);
        }
        if(!data.note.isEmpty()){
            gap(content,12);LinearLayout note=column();note.setPadding(dp(12),dp(10),dp(12),dp(10));note.setBackground(bg(failed?MufTheme.SOFT_RED:BACK,12,false));content.addView(note);
            note.addView(text("Catatan pemeriksaan",11,failed?RED:MUTED,true));
            TextView reason=text(data.note,13,NAVY,false);reason.setTextIsSelectable(true);note.addView(reason);
        }
        TextView title=text("Rincian kontrak",18,NAVY,true);title.setPadding(dp(20),dp(18),dp(20),dp(8));
        if(android.os.Build.VERSION.SDK_INT>=28)title.setAccessibilityHeading(true);
        showDialog(new AlertDialog.Builder(this).setCustomTitle(title).setView(scroll).setPositiveButton("Tutup", null)
            .setNeutralButton("Diagnosis", (d, w) -> repository.submit(database -> database.rowDiagnostic(sessionId, row.contract), diagnostic ->
                showText("Diagnosis lokal", diagnostic.isEmpty() ? "Diagnosis tersedia setelah percobaan kontrak ini selesai atau dihentikan." : diagnostic), e -> toast("Diagnosis belum dapat dibaca."))));
    }
    private ScrollView textScroll(String value) { ScrollView scroll = new ScrollView(this); TextView t = text(value, 13, NAVY, false);
        t.setTextIsSelectable(true); t.setPadding(dp(22), dp(12), dp(22), dp(20)); scroll.addView(t); return scroll; }
    private void showText(String title, String value) { showDialog(new AlertDialog.Builder(this).setTitle(title).setView(textScroll(value)).setPositiveButton("Tutup", null)); }
    private AlertDialog showDialog(AlertDialog.Builder builder){
        if(isFinishing()||isDestroyed())return null;
        AlertDialog dialog=builder.create();
        if(dialog.getWindow()!=null)dialog.getWindow().setWindowAnimations(android.animation.ValueAnimator.areAnimatorsEnabled()?R.style.MufDialogAnimation:0);
        dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(bg(Color.WHITE,24,false));
            int windowWidth=getWindow().getDecorView().getWidth();
            if(windowWidth<=0)windowWidth=getResources().getDisplayMetrics().widthPixels;
            dialog.getWindow().setLayout(Math.min(Math.max(dp(48),windowWidth-dp(48)),dp(440)),-2);}
        DialogActions actions=new DialogActions();
        android.view.ViewGroup buttonPanel=null;
        for(int which:new int[]{AlertDialog.BUTTON_NEUTRAL,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_POSITIVE}){
            Button b=dialog.getButton(which);if(b==null || b.getVisibility()!=View.VISIBLE)continue;
            android.view.ViewGroup parent=(android.view.ViewGroup)b.getParent(); if(buttonPanel==null)buttonPanel=parent;
            parent.removeView(b); b.setAllCaps(false);b.setTextSize(14);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));
            b.setMinWidth(dp(48)); b.setMinimumWidth(dp(48)); b.setPadding(dp(16),dp(10),dp(16),dp(10));
            MufTheme.flatten(b);
            boolean danger="Hapus".contentEquals(b.getText()),primary=which==AlertDialog.BUTTON_POSITIVE;
            b.setTextColor(primary?Color.WHITE:GREEN);
            b.setBackground(MufTheme.touch(this,danger?RED:primary?GREEN:Color.WHITE,12,false)); actions.addView(b);
        }
        if(buttonPanel!=null){buttonPanel.removeAllViews();buttonPanel.setPadding(dp(20),dp(8),dp(20),dp(16));
            buttonPanel.addView(actions,new android.view.ViewGroup.LayoutParams(-1,-2));}
        TextView message=dialog.findViewById(android.R.id.message);if(message!=null){message.setTextSize(14);message.setTextColor(MUTED);message.setLineSpacing(dp(3),1f);}
        return dialog;
    }
    /** Dialog actions stay right-aligned or stack when the user's font needs more room. */
    private final class DialogActions extends android.view.ViewGroup {
        private boolean stacked;
        DialogActions(){super(MainActivity.this);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int width=MeasureSpec.getSize(widthSpec),total=0,height=0,gap=dp(8),count=getChildCount();
            for(int i=0;i<count;i++){
                View child=getChildAt(i);child.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
                total+=child.getMeasuredWidth()+(i==0?0:gap);height=Math.max(height,child.getMeasuredHeight());
            }
            stacked=total>width;
            if(stacked){height=0;for(int i=0;i<count;i++){View child=getChildAt(i);
                child.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
                height+=child.getMeasuredHeight()+(i==0?0:gap);
            }}
            setMeasuredDimension(resolveSize(width,widthSpec),resolveSize(height,heightSpec));
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
            int count=getChildCount(),left=getWidth(),top=0,gap=dp(8);
            if(!stacked){for(int i=0;i<count;i++)left-=getChildAt(i).getMeasuredWidth()+(i==0?0:gap);}
            for(int i=0;i<count;i++){View child=getChildAt(i);int y=stacked?top:(getHeight()-child.getMeasuredHeight())/2;
                child.layout(stacked?0:left,y,stacked?getWidth():left+child.getMeasuredWidth(),y+child.getMeasuredHeight());
                if(stacked)top+=child.getMeasuredHeight()+gap;else left+=child.getMeasuredWidth()+gap;
            }
        }
    }
    private void pickPeriod() {
        LinearLayout row = new LinearLayout(this); row.setPadding(dp(16), dp(8), dp(16), dp(8));
        NumberPicker month = new NumberPicker(this); month.setMinValue(1); month.setMaxValue(12);
        month.setDisplayedValues(new String[]{"Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember"}); month.setValue(period.getMonthValue());
        NumberPicker year = new NumberPicker(this); year.setMinValue(2020); year.setMaxValue(2099); year.setValue(period.getYear());
        row.addView(month, new LinearLayout.LayoutParams(0, -2, 1)); row.addView(year, new LinearLayout.LayoutParams(0, -2, 1));
        showDialog(new AlertDialog.Builder(this).setTitle("Periode pemeriksaan").setView(row).setNegativeButton("Batal", null)
            .setPositiveButton("Pilih", (d, w) -> { period = YearMonth.of(year.getValue(), month.getValue()); periodView.setText(periodLabel(period)); prefs.edit().putString("selected_period", period.toString()).apply(); }));
    }
    private void explainAccessibility() {
        showDialog(new AlertDialog.Builder(this).setTitle("Aksesibilitas")
            .setMessage("Aktifkan MUF Checker di Aksesibilitas → Aplikasi yang diunduh. Akses dipakai untuk mengisi nomor dan membaca hasil di Tokopedia.\n\nJika dibatasi Android, buka Info aplikasi → Izinkan setelan dibatasi.")
            .setNegativeButton("Tutup", null).setPositiveButton("Buka pengaturan", (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
    }
    private void clearData() {
        if (running() || busy) { toast("Hentikan pemeriksaan atau tunggu proses sebelumnya dahulu."); return; }
        showDialog(new AlertDialog.Builder(this).setTitle("Hapus seluruh sesi?").setMessage("Semua antrean, hasil, dan diagnosis lokal akan dihapus.")
            .setNegativeButton("Batal", null).setPositiveButton("Hapus", (d, w) -> {
                busy = true;
                repository.submit(database -> { if (running()) throw new IllegalStateException("Sesi aktif"); database.clear(); return true; }, done -> {
                    busy = false; prefs.edit().remove("last_contracts").remove("last_contract").remove("diagnostic").remove("progress").remove("progress_short").remove("batch").remove("result").apply();
                    contract.setText(""); selectedSession = ""; latestSession = ""; revision = "!"; update();
                }, e -> { busy = false; toast("Data belum dapat dihapus."); });
            }));
    }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
}
