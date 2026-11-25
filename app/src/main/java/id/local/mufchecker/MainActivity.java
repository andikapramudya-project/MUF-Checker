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
    private java.util.Map<BulkQueue, AppRepository.Summary> summaries = new java.util.IdentityHashMap<>();
    private EditText contract, search;
    private TextView periodView, state, connection, inputCount, summary, empty;
    private Button start, stop, pause, resume, retry, periodButton;
    private ProgressBar progress;
    private TextView modeButton;
    private android.widget.Switch awake;
    private BulkQueue.StartMode startMode = BulkQueue.StartMode.READY_PAGE;
    private ListView resultList;
    private ResultAdapter resultAdapter;
    private int renderGeneration;
    private TextView progressLabel;
    private LinearLayout liveCard;
    private boolean cardMotion;
    private String countSession = "";
    private android.animation.ObjectAnimator progressAnimator;
    private final java.util.Map<TextView, android.animation.ValueAnimator> countAnimators = new java.util.HashMap<>();
    private static final int PROGRESS_STEPS = 100;
    private final Runnable searchRefresh = () -> renderTable();
    private final View[] pages = new View[3];
    private final View[] tabs = new View[3];
    private final TextView[] tabLabels = new TextView[3];
    private final android.widget.ImageView[] tabIcons = new android.widget.ImageView[3];
    private Segmented filterControl;
    private ResultRow measureRow;
    private View modeRow;
    private YearMonth period;
    private String revision = "!", filter = "Semua", searchText = "";
    private int selectedPage;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { update(); handler.postDelayed(this, pollingActive() ? 900 : 3000); }
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
        for(int i=0;i<pages.length;i++)if(pages[i]!=null){pages[i].animate().cancel();pages[i].setAlpha(1f);pages[i].setTranslationY(0);pages[i].setVisibility(i==selectedPage?View.VISIBLE:View.GONE);}
        settleMotion();
        syncNavigation(false);
        if (contract != null) prefs.edit().putString("last_contracts", contract.getText().toString()).putString("selected_period", period.toString()).apply();
        super.onPause();
    }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); if (repository != null) repository.close(); super.onDestroy(); }
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
    /** Grouped surface card: #F7F7F9, radius 20, no border or shadow. */
    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = column(); c.setPadding(dp(16), dp(16), dp(16), dp(16)); c.setBackground(bg(MufTheme.SURFACE, MufTheme.R_CARD, false));
        parent.addView(c, new LinearLayout.LayoutParams(-1, -2)); return c;
    }
    /** Primary: green, 52 dp, radius 14, white 16 sp medium. Secondary: soft fill with green text, no outline. */
    private Button button(LinearLayout parent, String label, boolean primary, View.OnClickListener action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(primary ? 16 : 14); b.setMinHeight(dp(primary ? 52 : 48)); b.setMinimumHeight(dp(primary ? 52 : 48));
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        MufTheme.flatten(b);
        b.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}}, new int[]{MUTED, primary ? Color.WHITE : GREEN}));
        b.setBackground(MufTheme.touch(this, primary ? GREEN : MufTheme.FILL, primary ? MufTheme.R_PRIMARY : MufTheme.R_CONTROL, false));
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(10); parent.addView(b, lp);
        b.setOnClickListener(action); MufTheme.pressable(b); return b;
    }
    private LinearLayout page(android.widget.FrameLayout host, int index) {
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
        host.addView(pages[index], new android.widget.FrameLayout.LayoutParams(-1, -1));
        return body;
    }
    private TextView metric(LinearLayout parent, String label, int color) {
        LinearLayout c = column(); c.setPadding(dp(12),dp(10),dp(12),dp(10)); c.setBackground(bg(MufTheme.FILL,MufTheme.R_CONTROL,false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,-2,1); lp.setMargins(dp(4),0,dp(4),0); parent.addView(c,lp);
        TextView value = text("0",28,color,true); value.setLineSpacing(0,1f); value.setFontFeatureSettings("tnum"); value.setTag(0); c.addView(value);
        TextView caption = text(label,12,MUTED,true); caption.setLetterSpacing(.02f); c.addView(caption); return value;
    }
    private void updateModeLabel() {
        String label = startMode == BulkQueue.StartMode.READY_PAGE ? "Halaman Tokopedia siap" : "Tautan MUF";
        if (modeButton != null) { modeButton.setText(label); modeRow.setContentDescription("Mode pemeriksaan, " + label + ", ketuk untuk mengubah"); }
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
            int bottom = insets.getSystemWindowInsetBottom();
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), bottom);
            // Floating bar sits max(navigation inset, 15 dp) above the screen edge.
            if (navigation != null) { LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) navigation.getLayoutParams();
                int margin = Math.max(0, dp(15) - bottom); if (lp.bottomMargin != margin) { lp.bottomMargin = margin; navigation.setLayoutParams(lp); } }
            return insets;
        });
        LinearLayout brand = new LinearLayout(this); brand.setGravity(Gravity.CENTER_VERTICAL); brand.setPadding(dp(20), dp(16), dp(20), dp(8));
        TextView wordmark = text("MUF CHECKER", 14, GREEN, true); wordmark.setLetterSpacing(.10f); brand.addView(wordmark);
        shell.addView(brand);
        android.widget.FrameLayout frames = new android.widget.FrameLayout(this); shell.addView(frames, new LinearLayout.LayoutParams(-1, 0, 1));
        // One visible page at a time; input and table retain their positions when navigating.
        android.widget.FrameLayout pageHost = new android.widget.FrameLayout(this); frames.addView(pageHost);
        LinearLayout checking = page(pageHost, 0);
        LinearLayout form = card(checking);
        LinearLayout periodRow = new LinearLayout(this); periodRow.setGravity(Gravity.CENTER_VERTICAL);
        periodRow.setPadding(dp(12),dp(6),dp(8),dp(6));periodRow.setBackground(bg(MufTheme.FILL,MufTheme.R_CONTROL,false));form.addView(periodRow);
        LinearLayout periodLabels=column();LinearLayout.LayoutParams labelsLp=new LinearLayout.LayoutParams(0,-2,1);labelsLp.rightMargin=dp(8);periodRow.addView(periodLabels,labelsLp);
        TextView periodCaption=text("Periode",11,MUTED,false);periodCaption.setLineSpacing(0,1f);periodLabels.addView(periodCaption);
        periodView=text(periodLabel(period),16,NAVY,true);periodView.setLineSpacing(0,1f);periodLabels.addView(periodView);
        periodButton=button(periodRow,"Ubah",false,v -> pickPeriod());periodButton.setMinWidth(dp(64));periodButton.setMinimumWidth(dp(64));
        periodButton.setTextSize(13);periodButton.setPadding(dp(16),0,dp(16),0);
        // Small white pill drawn inside a 48 dp touch target.
        GradientDrawable pill=bg(Color.WHITE,999,false);
        periodButton.setBackground(MufTheme.touch(new android.graphics.drawable.InsetDrawable(pill,0,dp(7),0,dp(7))));
        periodButton.setLayoutParams(new LinearLayout.LayoutParams(-2,dp(48)));
        gap(form,16); form.addView(text("Nomor kontrak", 16, NAVY, true));
        contract = new EditText(this); contract.setId(1001); contract.setTextSize(16); contract.setTextColor(NAVY); contract.setHintTextColor(MUTED); contract.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); contract.setGravity(Gravity.TOP);
        contract.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        contract.setMinLines(4); contract.setMaxLines(7); contract.setPadding(dp(12), dp(12), dp(12), dp(12));
        final GradientDrawable fieldShape = bg(MufTheme.FILL, MufTheme.R_CONTROL, false); final int focusStroke = Math.round(1.5f * getResources().getDisplayMetrics().density);
        fieldShape.setStroke(focusStroke, MufTheme.FILL); contract.setBackground(fieldShape); contract.setHint("Satu nomor per baris");
        contract.setOnFocusChangeListener((v, focused) -> fadeStroke(fieldShape, focusStroke, focused ? GREEN : MufTheme.FILL));
        contract.setContentDescription("Nomor kontrak MUF, satu nomor per baris, maksimal " + BulkQueue.LIMIT);
        contract.setText(prefs.getString("last_contracts", prefs.getString("last_contract", "")));
        LinearLayout.LayoutParams fieldLp = new LinearLayout.LayoutParams(-1, -2); fieldLp.topMargin = dp(8); form.addView(contract, fieldLp);
        inputCount = text("", 12, MUTED, false); inputCount.setFontFeatureSettings("tnum"); gap(form, 8); form.addView(inputCount);
        contract.addTextChangedListener(watcher(() -> countInput())); countInput();
        startMode = "DIRECT_LINK".equals(prefs.getString("start_mode", "READY_PAGE"))
            ? BulkQueue.StartMode.DIRECT_LINK : BulkQueue.StartMode.READY_PAGE;
        gap(form,4);
        start = button(form, startMode == BulkQueue.StartMode.READY_PAGE ? "Siapkan pemeriksaan" : "Mulai melalui tautan", true, v -> requestStart());
        gap(form,8); form.addView(text("Buka form MUF, tekan Mulai di panel.",12,MUTED,false));
        gap(checking, 12); LinearLayout live = card(checking); liveCard = live;
        TextView liveTitle = text("Aktivitas", 16, NAVY, true); if (android.os.Build.VERSION.SDK_INT >= 28) liveTitle.setAccessibilityHeading(true);
        live.addView(liveTitle); gap(live, 6); state = text("Belum ada pemeriksaan.", 14, MUTED, false); live.addView(state);
        state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        activityNote=text("",12,MUTED,false); activityNote.setMaxLines(3); activityNote.setEllipsize(android.text.TextUtils.TruncateAt.END); live.addView(activityNote);
        activityNote.setMinHeight(dp(48));
        activityNote.setOnClickListener(v -> { if(queue != null && !queue.reason().isEmpty()) showText("Aktivitas",queue.reason()); });
        activityProgress=column();live.addView(activityProgress);activityProgress.setVisibility(View.GONE);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setProgressDrawable(MufTheme.progressTrack(this));
        gap(activityProgress,12);activityProgress.addView(progress,new LinearLayout.LayoutParams(-1,dp(6)));
        progressLabel=text("",12,MUTED,false);progressLabel.setFontFeatureSettings("tnum");gap(activityProgress,8);activityProgress.addView(progressLabel);
        pause = button(live, "Jeda setelah kontrak aktif", false, v -> { if (service() != null) service().requestPause(); });
        resume = button(live, "Lanjutkan antrean", true, v -> requestResume());
        stop = button(live, "STOP sekarang", false, v -> { if (service() != null) service().stopByUser(); });
        stop.setTextColor(RED);
        pause.setContentDescription("Jeda setelah kontrak aktif selesai");stop.setContentDescription("STOP, hentikan pemeriksaan segera");
        resultsShortcut=button(live,"Lihat hasil",false,v -> selectPage(1));resultsShortcut.setVisibility(View.GONE);

        LinearLayout results = page(pageHost, 1);
        LinearLayout totals = card(results); sessionList = column(); totals.addView(sessionList);
        LinearLayout selector = new LinearLayout(this); selector.setGravity(Gravity.CENTER_VERTICAL); sessionList.addView(selector);
        sessionSelect=text("Belum ada sesi",16,NAVY,true); sessionSelect.setPadding(dp(4),dp(8),dp(8),dp(8)); sessionSelect.setGravity(Gravity.CENTER_VERTICAL); sessionSelect.setMinHeight(dp(48));
        sessionSelect.setBackground(MufTheme.touch(this,MufTheme.SURFACE,MufTheme.R_CONTROL,false)); sessionSelect.setOnClickListener(v -> chooseDiagnosisSession()); MufTheme.pressable(sessionSelect);
        selector.addView(sessionSelect,new LinearLayout.LayoutParams(0,-2,1));
        sessionMenu=text("",24,MUTED,true);
        android.graphics.drawable.Drawable menuIcon=getDrawable(R.drawable.ic_more).mutate(); menuIcon.setTint(MUTED);
        menuIcon.setBounds(0,0,dp(24),dp(24)); sessionMenu.setCompoundDrawables(menuIcon,null,null,null); sessionMenu.setPadding(dp(12),dp(12),dp(12),dp(12)); sessionMenu.setGravity(Gravity.CENTER); sessionMenu.setMinHeight(dp(48)); sessionMenu.setContentDescription("Menu sesi");
        sessionMenu.setBackground(MufTheme.touch(this,MufTheme.SURFACE,999,false));
        selector.addView(sessionMenu,new LinearLayout.LayoutParams(dp(48),dp(48))); sessionMenu.setOnClickListener(v -> sessionActions());
        resultOverview=column(); totals.addView(resultOverview);
        gap(resultOverview,8); LinearLayout metrics=new LinearLayout(this); resultOverview.addView(metrics);
        paidCount=metric(metrics,"Sudah",GREEN); unpaidCount=metric(metrics,"Belum",NAVY); errorCount=metric(metrics,"Error",RED);
        gap(resultOverview,12); summary = text("Belum ada sesi", 12, MUTED, false); summary.setFontFeatureSettings("tnum"); resultOverview.addView(summary);
        resultOverview.setVisibility(searchText.isEmpty()?View.VISIBLE:View.GONE);
        // Search field: glyph, soft fill, clear button only when there is text.
        gap(results, 12); LinearLayout searchRow = new LinearLayout(this); searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setBackground(bg(MufTheme.FILL, MufTheme.R_CONTROL, false)); searchRow.setMinimumHeight(dp(48)); results.addView(searchRow, new LinearLayout.LayoutParams(-1, -2));
        search = new EditText(this); search.setSingleLine(true); search.setTextSize(16); search.setHint("Cari kontrak atau nama"); search.setContentDescription("Cari hasil berdasarkan kontrak atau nama"); search.setTextColor(NAVY); search.setHintTextColor(MUTED);
        search.setBackground(null); search.setPadding(dp(12), dp(10), dp(4), dp(10)); search.setMinHeight(dp(48));
        android.graphics.drawable.Drawable glass = getDrawable(R.drawable.ic_search).mutate(); glass.setTint(MUTED); glass.setBounds(0, 0, dp(18), dp(18));
        search.setCompoundDrawables(glass, null, null, null); search.setCompoundDrawablePadding(dp(8));
        searchRow.addView(search, new LinearLayout.LayoutParams(0, -2, 1));
        final android.widget.ImageView clearSearch = new android.widget.ImageView(this); clearSearch.setImageResource(R.drawable.ic_clear); clearSearch.setColorFilter(MUTED);
        clearSearch.setScaleType(android.widget.ImageView.ScaleType.CENTER); clearSearch.setContentDescription("Hapus pencarian"); clearSearch.setBackground(MufTheme.touch(null));
        clearSearch.setOnClickListener(v -> search.setText("")); searchRow.addView(clearSearch, new LinearLayout.LayoutParams(dp(48), dp(48)));
        search.setText(searchText); clearSearch.setVisibility(searchText.isEmpty() ? View.GONE : View.VISIBLE);
        search.addTextChangedListener(watcher(() -> {
            searchText = search.getText().toString(); clearSearch.setVisibility(searchText.isEmpty() ? View.GONE : View.VISIBLE);
            resultOverview.setVisibility(searchText.isEmpty()?View.VISIBLE:View.GONE); updateRetryVisibility();
            handler.removeCallbacks(searchRefresh); handler.postDelayed(searchRefresh, 180);
        }));
        gap(results, 12);
        filterControl = new Segmented(new String[]{"Semua", "Belum", "Sudah", "Error"}, value -> { filter = value; renderTable(); });
        results.addView(filterControl, new LinearLayout.LayoutParams(-1, -2)); filterControl.select(filter, false);
        gap(results, 16);
        empty = text("Belum ada hasil. Siapkan pemeriksaan di tab Periksa.", 14, MUTED, false); results.addView(empty);
        retry = button(results, "Coba ulang ERROR", false, v -> retryErrors());
        gap(results,12);tableHeading=new ResultRow(true);results.addView(tableHeading);tableHeading.setVisibility(View.GONE);
        measureRow=new ResultRow(false);
        measureTableColumns(java.util.Collections.emptyList());
        resultAdapter = new ResultAdapter(); resultList.setAdapter(resultAdapter);
        resultList.setOnItemClickListener((parent, view, position, id) -> {
            int row = position - resultList.getHeaderViewsCount();
            if (row >= 0 && row < resultAdapter.getCount()) {
                ResultItem item = resultAdapter.getItem(row); showRow(item.source, item.sessionId);
            }
        });

        // Pengaturan: grouped inset rows with a small header above each group.
        LinearLayout settings = page(pageHost, 2);
        LinearLayout access = group(settings, "Akses & layar");
        connection = row(access, R.drawable.ic_access, "Aksesibilitas", "Memeriksa…", false, v -> explainAccessibility());
        awake = new android.widget.Switch(this); awake.setContentDescription("Jaga layar menyala");
        awake.setThumbTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{GREEN, Color.WHITE}));
        awake.setTrackTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{MufTheme.SOFT_GREEN, MufTheme.BORDER}));
        awake.setChecked(prefs.getBoolean("keep_screen", true));
        LinearLayout awakeRow = rowWithControl(access, R.drawable.ic_screen, "Jaga layar menyala", awake); awakeRow.setOnClickListener(v -> { if (awake.isEnabled()) awake.toggle(); });
        awake.setOnCheckedChangeListener((b, enabled) -> {
            if (running() && enabled != prefs.getBoolean("keep_screen", true)) { b.setChecked(prefs.getBoolean("keep_screen", true)); toast("Ubah setelah pemeriksaan selesai atau dijeda."); return; }
            prefs.edit().putBoolean("keep_screen", enabled).apply();
        });
        groupFooter(settings, "Akses dipakai untuk mengisi nomor dan membaca hasil di Tokopedia. Layar dijaga hanya selama pemeriksaan.");
        LinearLayout mode = group(settings, "Pemeriksaan");
        modeButton = row(mode, R.drawable.ic_mode, "Mode pemeriksaan", "", false, v -> { if (modeButton.isEnabled()) chooseMode(); });
        // 0.9.1: the row shows only its title on one line; the chosen mode appears in the dialog when tapped.
        modeButton.setVisibility(View.GONE);
        modeRow = (View) modeButton.getParent(); updateModeLabel();
        LinearLayout info = group(settings, "Diagnosis");
        diagnosisChoice = row(info, R.drawable.ic_session, "Sesi", "Pilih sesi", false, v -> chooseDiagnosisSession());
        LinearLayout diagActions = iconActionRow(info);
        iconAction(diagActions, R.drawable.ic_view, "Lihat diagnosis", v -> loadDiagnosis(report -> showText("Diagnosis sesi", report)));
        iconAction(diagActions, R.drawable.ic_copy, "Salin diagnosis", v -> loadDiagnosis(report -> {
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Diagnosis sesi MUF Checker", report));
            toast("Diagnosis sesi disalin tanpa nomor kontrak, nama, atau nominal.");
        }));
        iconAction(diagActions, R.drawable.ic_export, "Ekspor diagnosis", v -> exportDiagnosis());
        groupFooter(settings, "Diagnosis tidak memuat no. PK, nama, atau nominal.");
        LinearLayout dataCard = group(settings, "Data aplikasi");
        row(dataCard, R.drawable.ic_delete, "Hapus seluruh sesi", "", true, v -> clearData());
        gap(settings, 24); TextView footer = text("MUF Checker · 0.9.1\n© Andika Pramudya 2026", 11, MUTED, false);
        footer.setGravity(Gravity.CENTER); settings.addView(footer);

        // Floating pill bar: white 94%, hairline, soft ambient shadow, spring indicator.
        navigation=new android.widget.FrameLayout(this);
        GradientDrawable barShape=bg(Color.argb(240,255,255,255),999,false);barShape.setStroke(dp(1),MufTheme.BORDER);navigation.setBackground(barShape);
        navigation.setElevation(dp(6));navigation.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        if(android.os.Build.VERSION.SDK_INT>=28){navigation.setOutlineAmbientShadowColor(Color.argb(60,0,0,0));navigation.setOutlineSpotShadowColor(Color.argb(30,0,0,0));}
        navigationIndicator=new View(this);navigationIndicator.setBackground(bg(MufTheme.ACCENT_FILL,999,false));
        android.widget.FrameLayout.LayoutParams indicatorLp=new android.widget.FrameLayout.LayoutParams(0,0);indicatorLp.topMargin=dp(4);navigation.addView(navigationIndicator,indicatorLp);
        TabStrip nav=new TabStrip();navigationTabs=nav;nav.setPadding(dp(4),dp(4),dp(4),dp(4));navigation.addView(nav,new android.widget.FrameLayout.LayoutParams(-1,-2));
        navigation.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> { if(r-l!=or-ol || b-t!=ob-ot)syncNavigation(false); });
        String[] tabNames={"Periksa", "Hasil", "Pengaturan"}; int[] tabIconIds={R.drawable.ic_check,R.drawable.ic_results,R.drawable.ic_settings};
        for (int i = 0; i < 3; i++) { final int index = i;
            LinearLayout tab=column();tab.setGravity(Gravity.CENTER);tab.setMinimumHeight(dp(56));tab.setPadding(dp(4),dp(6),dp(4),dp(6));
            tabIcons[i]=new android.widget.ImageView(this);tabIcons[i].setImageResource(tabIconIds[i]);tabIcons[i].setColorFilter(MUTED);
            tab.addView(tabIcons[i],new LinearLayout.LayoutParams(dp(26),dp(26)));
            tabLabels[i]=text(tabNames[i],11,MUTED,true);tabLabels[i].setLineSpacing(0,1f);tabLabels[i].setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelLp=new LinearLayout.LayoutParams(-2,-2);labelLp.topMargin=dp(2);tab.addView(tabLabels[i],labelLp);
            tabIcons[i].setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);tabLabels[i].setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            tab.setFocusable(true);tab.setClickable(true);tabs[i]=tab;
            tab.setOnClickListener(v -> { if (selectedPage != index) tick(v); selectPage(index); });
            nav.addView(tab,new LinearLayout.LayoutParams(0,-1,1)); }
        LinearLayout.LayoutParams navLp=new LinearLayout.LayoutParams(-1,-2);navLp.setMargins(dp(19),dp(8),dp(19),dp(15));shell.addView(navigation,navLp);
        shell.setClipChildren(false);shell.setClipToPadding(false);
        setContentView(shell);shell.requestApplyInsets();navigation.post(() -> syncNavigation(false));
    }
    /** Focus ring of the contract field: a 1.5 dp stroke that fades in over 150 ms. Visual only. */
    private void fadeStroke(GradientDrawable shape, int width, int to) {
        Integer from = (Integer) contract.getTag(); int start = from == null ? MufTheme.FILL : from; contract.setTag(to);
        if (!resumed || !MufTheme.motionOn() || start == to) { shape.setStroke(width, to); return; }
        android.animation.ValueAnimator fade = android.animation.ValueAnimator.ofArgb(start, to); fade.setDuration(150); fade.setInterpolator(MufTheme.easeOut());
        fade.addUpdateListener(a -> shape.setStroke(width, (int) a.getAnimatedValue())); fade.start();
    }
    /** Selection tick; Android applies the user's haptic setting, and nothing depends on it. */
    private void tick(View view) { view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); }
    /** Grouped settings section: 13 sp header above an inset card whose rows supply their own padding. */
    private LinearLayout group(LinearLayout parent, String header) {
        if (parent.getChildCount() > 0) gap(parent, 20);
        TextView title = text(header, 13, MUTED, false); title.setPadding(dp(16), 0, dp(16), dp(6));
        if (android.os.Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true); parent.addView(title);
        LinearLayout card = column(); card.setBackground(bg(MufTheme.SURFACE, MufTheme.R_CARD, false)); card.setClipToOutline(true);
        parent.addView(card, new LinearLayout.LayoutParams(-1, -2)); return card;
    }
    private void groupFooter(LinearLayout parent, String value) {
        TextView note = text(value, 12, MUTED, false); note.setPadding(dp(16), dp(6), dp(16), 0); parent.addView(note);
    }
    /** Row: 30 dp tinted glyph tile, 16 sp title, right-aligned 14 sp value; hairline inset from the text start. */
    private TextView row(LinearLayout group, int icon, String title, String value, boolean danger, View.OnClickListener action) {
        LinearLayout row = rowShell(group, icon, title, danger);
        TextView valueView = text(value, 14, MUTED, false); valueView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        valueView.setMaxWidth(dp(180)); row.addView(valueView, new LinearLayout.LayoutParams(-2, -2));
        row.setOnClickListener(action); return valueView;
    }
    /** One row of equal icon-only buttons inside a grouped card; hairline inset like the other rows. */
    private LinearLayout iconActionRow(LinearLayout group) {
        if (group.getChildCount() > 0) { View line = new View(this); line.setBackgroundColor(MufTheme.BORDER);
            LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(-1, dp(1)); lineLp.leftMargin = dp(58); group.addView(line, lineLp); }
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(4), dp(12), dp(4)); group.addView(row, new LinearLayout.LayoutParams(-1, -2)); return row;
    }
    /** Icon-only action: 48 dp touch target, 36 dp tinted pill, label exposed as content description and tooltip. */
    private View iconAction(LinearLayout row, int icon, String label, View.OnClickListener action) {
        android.widget.FrameLayout button = new android.widget.FrameLayout(this);
        android.graphics.drawable.InsetDrawable pill = new android.graphics.drawable.InsetDrawable(bg(MufTheme.SOFT_GREEN, 10, false), dp(4), dp(6), dp(4), dp(6));
        android.graphics.drawable.InsetDrawable mask = new android.graphics.drawable.InsetDrawable(bg(Color.BLACK, 10, false), dp(4), dp(6), dp(4), dp(6));
        button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x0A000000), pill, mask)); button.setFocusable(true); button.setClickable(true);
        button.setContentDescription(label); button.setTooltipText(label);
        android.widget.ImageView glyph = new android.widget.ImageView(this); glyph.setImageResource(icon); glyph.setColorFilter(GREEN);
        glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        button.addView(glyph, new android.widget.FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        button.setOnClickListener(action); MufTheme.pressable(button);
        row.addView(button, new LinearLayout.LayoutParams(0, dp(48), 1)); return button;
    }
    private LinearLayout rowWithControl(LinearLayout group, int icon, String title, View control) {
        LinearLayout row = rowShell(group, icon, title, false); row.addView(control, new LinearLayout.LayoutParams(-2, -2)); return row;
    }
    private LinearLayout rowShell(LinearLayout group, int icon, String title, boolean danger) {
        if (group.getChildCount() > 0) { View line = new View(this); line.setBackgroundColor(MufTheme.BORDER);
            LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(-1, dp(1)); lineLp.leftMargin = dp(58); group.addView(line, lineLp); }
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setMinimumHeight(dp(56));
        row.setPadding(dp(16), dp(8), dp(16), dp(8)); row.setBackground(MufTheme.touch(this, Color.TRANSPARENT, 0, false)); row.setFocusable(true); row.setClickable(true);
        android.widget.FrameLayout tile = new android.widget.FrameLayout(this); tile.setBackground(bg(danger ? MufTheme.SOFT_RED : MufTheme.SOFT_GREEN, 8, false));
        android.widget.ImageView glyph = new android.widget.ImageView(this); glyph.setImageResource(icon); glyph.setColorFilter(danger ? RED : GREEN);
        tile.addView(glyph, new android.widget.FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        tile.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(tile, new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView label = text(title, 16, danger ? RED : NAVY, false); label.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, -2, 1); labelLp.leftMargin = dp(12); labelLp.rightMargin = dp(8);
        row.addView(label, labelLp); group.addView(row, new LinearLayout.LayoutParams(-1, -2)); MufTheme.pressable(row); return row;
    }
    /** Counts up from the last shown value once per change; otherwise sets the number directly. */
    private void setCount(TextView view, int value, boolean animate) {
        android.animation.ValueAnimator running = countAnimators.remove(view);
        if (running != null) running.end();
        int shown = view.getTag() instanceof Integer ? (Integer) view.getTag() : 0;
        view.setTag(value);
        if (!animate || shown == value) { view.setText(Integer.toString(value)); return; }
        android.animation.ValueAnimator count = android.animation.ValueAnimator.ofInt(shown, value);
        count.setDuration(MufTheme.MOTION_COUNT); count.setInterpolator(MufTheme.easeOut());
        count.addUpdateListener(a -> view.setText(a.getAnimatedValue().toString()));
        count.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) { view.setText(Integer.toString(value)); countAnimators.remove(view); }
        });
        countAnimators.put(view, count); count.start();
    }
    /** Progress runs at a finer resolution so a single finished contract glides instead of stepping. */
    private void animateProgress(int value, int maximum) {
        int max = maximum * PROGRESS_STEPS, target = value * PROGRESS_STEPS;
        if (progress.getMax() != max) { if (progressAnimator != null) progressAnimator.cancel(); progress.setMax(max); progress.setProgress(target); return; }
        if (progressAnimator != null && progressAnimator.isRunning()) {
            if (((Integer) progressAnimator.getAnimatedValue()) == target) return;
            progressAnimator.cancel();
        } else if (progress.getProgress() == target) return;
        if (selectedPage != 0 || !resumed || !MufTheme.motionOn()) { progress.setProgress(target); return; }
        progressAnimator = android.animation.ObjectAnimator.ofInt(progress, "progress", progress.getProgress(), target);
        progressAnimator.setDuration(MufTheme.MOTION_COUNT); progressAnimator.setInterpolator(MufTheme.easeOut()); progressAnimator.start();
    }
    /**
     * Controls inside Aktivitas fade in/out and the card height eases (parent hierarchy is animated by the transition).
     * Only this card: the contract field is excluded so typing never animates. Attached after the first load so launch is static.
     */
    private void enableCardMotion() {
        if (cardMotion || liveCard == null) return;
        cardMotion = true; liveCard.setLayoutTransition(calmTransition());
    }
    private android.animation.LayoutTransition calmTransition() {
        android.animation.LayoutTransition t = new android.animation.LayoutTransition();
        t.enableTransitionType(android.animation.LayoutTransition.CHANGING);
        int[] types = {android.animation.LayoutTransition.APPEARING, android.animation.LayoutTransition.DISAPPEARING,
            android.animation.LayoutTransition.CHANGE_APPEARING, android.animation.LayoutTransition.CHANGE_DISAPPEARING, android.animation.LayoutTransition.CHANGING};
        for (int type : types) { t.setStartDelay(type, 0); t.setDuration(type, MufTheme.MOTION_ENTER); t.setInterpolator(type, MufTheme.move()); }
        t.setInterpolator(android.animation.LayoutTransition.APPEARING, MufTheme.easeOut());
        t.setDuration(android.animation.LayoutTransition.DISAPPEARING, MufTheme.MOTION_EXIT);
        t.setInterpolator(android.animation.LayoutTransition.DISAPPEARING, MufTheme.easeIn());
        return t;
    }
    /** Jump every running presentation animation to its end state (pause, rotation). */
    private void settleMotion() {
        for (android.animation.ValueAnimator count : new java.util.ArrayList<>(countAnimators.values())) count.end();
        countAnimators.clear();
        if (progressAnimator != null) progressAnimator.end();
        for (android.animation.ValueAnimator tint : tabTints) if (tint != null) tint.end();
        if (indicatorMotion != null) indicatorMotion.end();
        if (filterControl != null) filterControl.settle();
        for (int i = 0; i < tabIcons.length; i++) if (tabIcons[i] != null) { tabIcons[i].animate().cancel(); float scale = i == selectedPage ? 1.08f : 1f; tabIcons[i].setScaleX(scale); tabIcons[i].setScaleY(scale); }
    }
    private TextWatcher watcher(Runnable changed) { return new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
        @Override public void afterTextChanged(Editable e) { changed.run(); }
    }; }
    private void selectPage(int index) {
        int previous = selectedPage;
        boolean changed = selectedPage != index;
        selectedPage = index >= 0 && index < 3 ? index : 0;
        boolean fade = changed && resumed && MufTheme.motionOn() && previous >= 0 && previous < 3 && previous != selectedPage;
        if (changed) ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(contract.getWindowToken(), 0);
        for (int i = 0; i < 3; i++) { pages[i].animate().cancel(); pages[i].setAlpha(1f); pages[i].setTranslationY(0);
            pages[i].setVisibility(i == selectedPage || fade && i == previous ? View.VISIBLE : View.GONE);
            tabs[i].setSelected(i == selectedPage); tabs[i].setContentDescription(tabLabels[i].getText() + (i == selectedPage ? ", dipilih" : ""));
            tintTab(i, i == selectedPage ? GREEN : MUTED, changed && resumed && MufTheme.motionOn()); }
        syncNavigation(changed);
        if (selectedPage == 1 && tableDirty) renderTable();
        if (fade) {
            // Fade-through: the old page leaves quickly, then the new one settles in. Visual only; no queue work is scheduled.
            final View out = pages[previous], in = pages[selectedPage];
            out.animate().alpha(0f).setStartDelay(0).setDuration(MufTheme.MOTION_EXIT).setInterpolator(MufTheme.easeIn()).withLayer()
                .withEndAction(() -> { out.setVisibility(View.GONE); out.setAlpha(1f); }).start();
            in.setAlpha(0f); in.setTranslationY(dp(8));
            in.animate().alpha(1f).translationY(0).setStartDelay(MufTheme.MOTION_EXIT).setDuration(MufTheme.MOTION_ENTER)
                .setInterpolator(MufTheme.easeOut()).withLayer().start();
        }
    }
    private final android.animation.ValueAnimator[] tabTints = new android.animation.ValueAnimator[3];
    /** Label and icon tint tween (200 ms); the selected icon grows to 1.08 on the spring. */
    private void tintTab(int index, int color, boolean animate) {
        TextView label = tabLabels[index]; android.widget.ImageView icon = tabIcons[index]; if (label == null) return;
        if (tabTints[index] != null) { tabTints[index].cancel(); tabTints[index] = null; }
        float scale = color == GREEN ? 1.08f : 1f; icon.animate().cancel();
        label.setTypeface(Typeface.create(color == GREEN ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        int from = label.getCurrentTextColor();
        if (!animate) { label.setTextColor(color); icon.setColorFilter(color); icon.setScaleX(scale); icon.setScaleY(scale); return; }
        icon.animate().scaleX(scale).scaleY(scale).setDuration(MufTheme.MOTION_SPRING).setInterpolator(MufTheme.spring()).start();
        if (from == color) return;
        android.animation.ValueAnimator tint = android.animation.ValueAnimator.ofArgb(from, color);
        tint.setDuration(MufTheme.MOTION_QUICK); tint.setInterpolator(MufTheme.move());
        // Cancelling keeps the in-between colour, so a quick second tap continues from what is on screen.
        tint.addUpdateListener(a -> { int c = (int) a.getAnimatedValue(); label.setTextColor(c); icon.setColorFilter(c); });
        tabTints[index] = tint; tint.start();
    }
    private android.animation.ValueAnimator indicatorMotion;
    /** Purely visual selection movement; never schedules queue work. */
    private void syncNavigation(boolean animate) { moveIndicator(selectedPage, animate); }
    /**
     * Indicator travels on the spring and stretches with its travel lag (wider, slightly flatter mid-move),
     * returning to 1 at rest. Snaps when animations are off, before layout, or while paused.
     */
    private void moveIndicator(int index, boolean animate) {
        if(navigation==null || navigationTabs==null || tabs[index]==null)return;
        if(navigationTabs.getWidth()==0 || tabs[index].getWidth()==0)return;
        android.widget.FrameLayout.LayoutParams lp=(android.widget.FrameLayout.LayoutParams)navigationIndicator.getLayoutParams();
        int width=tabs[index].getWidth(),height=navigationTabs.getHeight()-dp(8);
        if(lp.width!=width || lp.height!=height){lp.width=width;lp.height=height;navigationIndicator.setLayoutParams(lp);}
        final float target=tabs[index].getLeft(), from=navigationIndicator.getTranslationX();
        if(indicatorMotion!=null){indicatorMotion.cancel();indicatorMotion=null;}
        if(!animate || !resumed || !MufTheme.motionOn() || from==target){
            navigationIndicator.setTranslationX(target);navigationIndicator.setScaleX(1f);navigationIndicator.setScaleY(1f);return;
        }
        final float tabsTravelled=Math.abs(target-from)/Math.max(1,width);
        final android.animation.ValueAnimator travel=android.animation.ValueAnimator.ofFloat(0f,1f);
        travel.setDuration(MufTheme.MOTION_SPRING);travel.setInterpolator(new android.view.animation.LinearInterpolator());
        travel.addUpdateListener(a -> {
            float f=a.getAnimatedFraction(),lag=UiPresentation.travelLag(f,tabsTravelled);
            navigationIndicator.setTranslationX(from+(target-from)*UiPresentation.spring(f));
            navigationIndicator.setScaleX(UiPresentation.stretchX(lag));navigationIndicator.setScaleY(UiPresentation.stretchY(lag));
        });
        travel.addListener(new android.animation.AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(android.animation.Animator a){navigationIndicator.setTranslationX(target);navigationIndicator.setScaleX(1f);navigationIndicator.setScaleY(1f);}
        });
        indicatorMotion=travel;travel.start();
    }
    /** Tab row that turns a horizontal drag into a hovered tab (a tick per crossing) and selects it on release. Taps still go to the tabs. */
    private final class TabStrip extends LinearLayout {
        private final int slop=android.view.ViewConfiguration.get(MainActivity.this).getScaledTouchSlop();
        private float downX; private boolean dragging; private int hovered;
        TabStrip(){super(MainActivity.this);}
        private int at(float x){return UiPresentation.segmentAt(x-getPaddingLeft(),getWidth()-getPaddingLeft()-getPaddingRight(),getChildCount());}
        @Override public boolean onInterceptTouchEvent(android.view.MotionEvent e){
            switch(e.getActionMasked()){
                case android.view.MotionEvent.ACTION_DOWN: downX=e.getX();dragging=false;return false;
                case android.view.MotionEvent.ACTION_MOVE:
                    if(!dragging && Math.abs(e.getX()-downX)>slop){dragging=true;hovered=selectedPage;getParent().requestDisallowInterceptTouchEvent(true);}
                    return dragging;
                default: return false;
            }
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            if(!dragging)return super.onTouchEvent(e);
            int action=e.getActionMasked();
            if(action==android.view.MotionEvent.ACTION_MOVE){int index=at(e.getX());if(index!=hovered){hovered=index;tick(this);moveIndicator(index,true);}}
            else if(action==android.view.MotionEvent.ACTION_UP){dragging=false;if(hovered==selectedPage)moveIndicator(selectedPage,true);else selectPage(hovered);}
            else if(action==android.view.MotionEvent.ACTION_CANCEL){dragging=false;moveIndicator(selectedPage,true);}
            return true;
        }
    }
    interface Choice { void chosen(String value); }
    /** Segmented filter: soft track, white thumb sliding on the spring; the selected label is medium weight. */
    private final class Segmented extends android.widget.FrameLayout {
        final String[] values; final TextView[] items; final View thumb; final LinearLayout row; private int selected; private android.animation.ValueAnimator slide;
        Segmented(String[] values, Choice choice) {
            super(MainActivity.this); this.values=values; items=new TextView[values.length];
            setBackground(bg(MufTheme.FILL,MufTheme.R_CONTROL,false)); setPadding(dp(3),dp(3),dp(3),dp(3)); setClipToPadding(false); setClipChildren(false);
            thumb=new View(MainActivity.this); thumb.setBackground(bg(Color.WHITE,10,false)); thumb.setElevation(dp(2));
            thumb.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
            if(android.os.Build.VERSION.SDK_INT>=28){thumb.setOutlineAmbientShadowColor(Color.argb(50,0,0,0));thumb.setOutlineSpotShadowColor(Color.argb(40,0,0,0));}
            addView(thumb,new LayoutParams(0,0));
            row=new LinearLayout(MainActivity.this); row.setTranslationZ(dp(3)); addView(row,new LayoutParams(-1,-2)); // labels above the thumb
            for(int i=0;i<values.length;i++){ final int index=i;
                TextView item=text(values[i],13,NAVY,false); item.setGravity(Gravity.CENTER); item.setMinHeight(dp(42)); item.setLineSpacing(0,1f);
                item.setPadding(dp(4),dp(4),dp(4),dp(4)); item.setTranslationZ(dp(3)); items[i]=item;
                item.setOnClickListener(v -> { if(index!=selected){tick(v);} select(values[index],true); choice.chosen(values[index]); });
                row.addView(item,new LinearLayout.LayoutParams(0,-1,1)); }
        }
        void select(String value, boolean animate) {
            int index=java.util.Arrays.asList(values).indexOf(value); if(index<0)index=0; selected=index;
            for(int i=0;i<items.length;i++){boolean chosen=i==index; items[i].setSelected(chosen);
                items[i].setTypeface(Typeface.create(chosen?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
                items[i].setContentDescription("Filter "+values[i]+(chosen?", dipilih":""));}
            place(animate && resumed && MufTheme.motionOn());
        }
        private void place(boolean animate) {
            int width=row.getWidth()/Math.max(1,items.length); if(width==0)return;
            LayoutParams lp=(LayoutParams)thumb.getLayoutParams();
            if(lp.width!=width || lp.height!=row.getHeight()){lp.width=width;lp.height=row.getHeight();thumb.setLayoutParams(lp);}
            final float target=selected*width, from=thumb.getTranslationX();
            if(slide!=null){slide.cancel();slide=null;}
            if(!animate || from==target){thumb.setTranslationX(target);return;}
            slide=android.animation.ValueAnimator.ofFloat(0f,1f); slide.setDuration(MufTheme.MOTION_SPRING); slide.setInterpolator(new android.view.animation.LinearInterpolator());
            slide.addUpdateListener(a -> thumb.setTranslationX(from+(target-from)*UiPresentation.spring(a.getAnimatedFraction())));
            slide.start();
        }
        void settle(){ if(slide!=null)slide.end(); }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){ super.onLayout(changed,l,t,r,b); if(changed) post(() -> place(false)); }
    }
    private void countInput() {
        LinkedHashSet<String> valid = new LinkedHashSet<>(); int duplicate = 0, invalid = 0;
        for (String raw : contract.getText().toString().replaceFirst("^\uFEFF", "").split("\\r?\\n")) {
            String value = raw.trim(); if (value.isEmpty()) continue;
            if (!InquiryRules.validContract(value)) invalid++; else if (!valid.add(value)) duplicate++;
        }
        inputCount.setText(valid.size() + " kontrak berbeda · Maksimal " + BulkQueue.LIMIT + (duplicate > 0 ? " · " + duplicate + " duplikat" : "") + (invalid > 0 ? " · " + invalid + " tidak valid" : ""));
        inputCount.setTextColor(invalid > 0 || valid.size() > BulkQueue.LIMIT ? RED : MUTED);
    }
    private String periodLabel(YearMonth month) { return month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("id-ID"))); }
    private TokopediaService service() { return TokopediaService.instance; }
    /** Fast refresh while the service is checking or preparing; the revision check makes idle reads cheap but not free. */
    private boolean pollingActive() { TokopediaService live = service(); return live != null && (live.isRunning() || live.isPreparing()) || busy; }
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
                revision = data.revision; queue = data.current; history = data.sessions; summaries = data.summaries;
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
            modeButton.setEnabled(!active && !pending && !busy); modeRow.setEnabled(modeButton.isEnabled()); modeRow.setAlpha(modeButton.isEnabled() ? 1f : .5f);
            awake.setEnabled(!active);
            pause.setVisibility(active && !preparing ? View.VISIBLE : View.GONE); pause.setEnabled(queue == null || !queue.pauseRequested());
            stop.setText(preparing ? "Batalkan persiapan" : "STOP sekarang");
            stop.setVisibility(active ? View.VISIBLE : View.GONE); resume.setVisibility(!active && pending ? View.VISIBLE : View.GONE);
            resume.setText(queue != null && queue.startMode() == BulkQueue.StartMode.READY_PAGE ? "Siapkan ulang panel Mulai" : "Lanjutkan antrean");
            updateRetryVisibility();
            connection.setText(service() != null ? "Tersambung" : "Belum tersambung");
            connection.setTextColor(service() != null ? GREEN : NAVY);
            state.setText(preparing ? "Menunggu Mulai di Tokopedia" : queue==null ? "Belum ada pemeriksaan" : !active && pending ? (queue.state() == BulkQueue.RunState.STOPPED ? "Pemeriksaan dihentikan" : "Antrean dijeda")
                : !active && queue != null && queue.state() == BulkQueue.RunState.COMPLETED ? "Pemeriksaan selesai"
                : prefs.getString("progress_short", "Siap memeriksa"));
            String note = preparing ? "Angsuran Kredit · MUF" : !active && pending && !queue.reason().isEmpty() ? "Ketuk untuk melihat alasan jeda." : "";
            activityNote.setContentDescription(!active && pending ? queue.reason() : note);
            activityNote.setText(note); activityNote.setVisibility(note.isEmpty() ? View.GONE : View.VISIBLE);
            int maximum = queue == null ? 1 : queue.rows().size(), value = queue == null ? 0 : queue.completedCount();
            activityProgress.setVisibility(queue==null?View.GONE:View.VISIBLE);
            resultsShortcut.setVisibility(queue!=null && queue.completedCount()>0?View.VISIBLE:View.GONE);
            progressLabel.setText(queue == null ? "" : value + "/" + maximum + " selesai · " + (value * 100 / maximum) + "%");
            progress.setContentDescription(progressLabel.getText());
            animateProgress(value, maximum);
            enableCardMotion();
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
        AppRepository.Summary counted = viewQueue == null ? null : summaries.get(viewQueue);
        if (viewQueue != null && counted == null) { counted = AppRepository.summarize(viewQueue); summaries.put(viewQueue, counted); }
        int paid = counted == null ? 0 : counted.paid, unpaid = counted == null ? 0 : counted.unpaid;
        String countKey = viewQueue == null ? "" : viewQueue.sessionId();
        boolean countUp = countKey.equals(countSession) && selectedPage == 1 && resumed && MufTheme.motionOn();
        countSession = countKey;
        setCount(paidCount, paid, countUp); setCount(unpaidCount, unpaid, countUp); setCount(errorCount, viewQueue==null?0:viewQueue.errorCount(), countUp);
        diagnosisChoice.setText(viewQueue==null?"Pilih sesi":when);
        ((View) diagnosisChoice.getParent()).setContentDescription(viewQueue==null?"Sesi diagnosis, pilih sesi":"Sesi diagnosis, terpilih " + when + ", ketuk untuk mengganti");
        updateRetryVisibility();
        String nextTableKey = counted == null ? UiPresentation.tableKey(null) : counted.tableKey;
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
    private String status(BulkQueue.Row row) { return AppRepository.rowStatus(row); }
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
            if (filterControl != null && !chosenFilter.equals(filterControl.values[filterControl.selected])) filterControl.select(chosenFilter, false);
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
        // Header captions are smaller than cells, so values are measured with a body row's paints.
        TextView[] cellsPaint={measureRow.ordinal,measureRow.number,measureRow.name,measureRow.badge};
        for(ResultItem item:items){
            String[] values={Integer.toString(item.ordinal),item.source.contract,item.name,UiPresentation.tableStatusLabel(item.status)};
            for(int i=0;i<4;i++)next[i]=Math.max(next[i],(int)Math.ceil(cellsPaint[i].getPaint().measureText(values[i]))+dp(24));
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
            cells.setBackground(heading?bg(MufTheme.SURFACE,0,false):MufTheme.touch(MainActivity.this,Color.WHITE,0,false));
            for(TextView child:new TextView[]{ordinal,number,name,badge}) {
                child.setPadding(dp(12),dp(10),dp(12),dp(10));child.setGravity(Gravity.CENTER_VERTICAL);
                child.setSingleLine(true);child.setEllipsize(null);
                child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);cells.addView(child);
            }
            ordinal.setGravity(Gravity.CENTER);badge.setGravity(Gravity.CENTER);
            cells.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            if(heading){ordinal.setText("No");number.setText("Kontrak");name.setText("Nama");badge.setText("Status");
                for(TextView t:new TextView[]{ordinal,number,name,badge}){t.setTextSize(11);t.setTextColor(MUTED);t.setLetterSpacing(.02f);
                    t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));}
                if(android.os.Build.VERSION.SDK_INT>=28)setAccessibilityHeading(true);setContentDescription("Kolom No, Kontrak, Nama, Status");}
            else {
                setOnClickListener(v -> {if(data!=null)showRow(data.source,data.sessionId);});
                cells.setOnClickListener(v -> performClick());
            }
        }
        void bind(ResultItem data) {
            this.data=data;
            ordinal.setText(Integer.toString(data.ordinal));number.setText(data.source.contract);name.setText(data.name);
            badge.setText(UiPresentation.tableStatusLabel(data.status));
            badge.setTextColor("ERROR".equals(data.status)?RED:"SUDAH BAYAR".equals(data.status)?GREEN:MUTED);
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
                // Hairline separators between rows only; columns are carried by alignment and padding.
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
        LinearLayout content=column();content.setPadding(dp(20),dp(4),dp(20),dp(8));
        String stateLabel=status(row);boolean failed="ERROR".equals(stateLabel),paid="SUDAH BAYAR".equals(stateLabel);
        TextView statusChip=text(UiPresentation.statusLabel(stateLabel),13,failed?RED:paid?GREEN:NAVY,true);statusChip.setLineSpacing(0,1f);
        statusChip.setPadding(dp(12),dp(6),dp(12),dp(6));statusChip.setBackground(bg(failed?MufTheme.SOFT_RED:paid?MufTheme.SOFT_GREEN:MufTheme.FILL,999,false));
        content.addView(statusChip,new LinearLayout.LayoutParams(-2,-2));gap(content,12);
        LinearLayout identity=column();identity.setPadding(dp(12),dp(12),dp(12),dp(12));identity.setBackground(bg(MufTheme.SURFACE,MufTheme.R_CONTROL,false));content.addView(identity);
        detailValue(identity,"Kontrak",row.contract);
        if(!data.name.isEmpty()){gap(identity,10);detailValue(identity,"Nama",data.name);}
        if(data.verified){
            gap(content,16);detailPair(content,"Jatuh tempo",data.due,"Periode",data.period);
            gap(content,12);View divider=new View(this);divider.setBackgroundColor(MufTheme.BORDER);content.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
            gap(content,12);detailPair(content,"Angsuran",data.amount,"Cicilan ke",data.installment);
        }
        if(!data.note.isEmpty()){
            gap(content,12);LinearLayout note=column();note.setPadding(dp(12),dp(10),dp(12),dp(10));note.setBackground(bg(failed?MufTheme.SOFT_RED:MufTheme.SURFACE,MufTheme.R_CONTROL,false));content.addView(note);
            note.addView(text("Catatan pemeriksaan",11,failed?RED:MUTED,true));
            TextView reason=text(data.note,13,NAVY,false);reason.setTextIsSelectable(true);note.addView(reason);
        }
        presentSheet("Rincian kontrak",content,"Diagnosis",() -> repository.submit(database -> database.rowDiagnostic(sessionId, row.contract), diagnostic ->
            showText("Diagnosis lokal", diagnostic.isEmpty() ? "Diagnosis tersedia setelah percobaan kontrak ini selesai atau dihentikan." : diagnostic), e -> toast("Diagnosis belum dapat dibaca.")));
    }
    /**
     * Bottom sheet: full width, 24 dp top corners, grab handle, scrollable body capped at 85% of the window.
     * Enters on the spring with a 200 ms scrim fade; leaves downward in 200 ms. Drag past 25% or fling down to close.
     * Blur behind is used only where Android enables cross-window blur (API 31+); elsewhere a plain dim.
     */
    private void presentSheet(String title,View body,String secondaryLabel,Runnable secondary) {
        if(isFinishing()||isDestroyed())return;
        final android.app.Dialog dialog=new android.app.Dialog(this,android.R.style.Theme_Translucent_NoTitleBar);
        final android.widget.FrameLayout root=new android.widget.FrameLayout(this);
        final View scrim=new View(this);scrim.setBackgroundColor(Color.BLACK);scrim.setAlpha(0f);root.addView(scrim,new android.widget.FrameLayout.LayoutParams(-1,-1));
        final SheetLayout sheet=new SheetLayout();
        float r=dp(MufTheme.R_SHEET);GradientDrawable surface=new GradientDrawable();surface.setColor(Color.WHITE);surface.setCornerRadii(new float[]{r,r,r,r,0,0,0,0});
        sheet.setBackground(surface);sheet.setElevation(dp(8));sheet.setClickable(true);
        LinearLayout top=column();top.setGravity(Gravity.CENTER_HORIZONTAL);
        View handle=new View(this);handle.setBackground(bg(Color.rgb(199,199,204),999,false));
        LinearLayout.LayoutParams handleLp=new LinearLayout.LayoutParams(dp(36),dp(4));handleLp.topMargin=dp(10);handleLp.bottomMargin=dp(6);top.addView(handle,handleLp);
        TextView heading=text(title,18,NAVY,true);heading.setPadding(dp(20),dp(4),dp(20),dp(12));
        if(android.os.Build.VERSION.SDK_INT>=28)heading.setAccessibilityHeading(true);
        top.addView(heading,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        DialogActions actions=new DialogActions();android.widget.FrameLayout actionBar=new android.widget.FrameLayout(this);actionBar.setPadding(dp(20),dp(8),dp(20),dp(12));actionBar.addView(actions,new android.widget.FrameLayout.LayoutParams(-1,-2));
        final boolean[] closing={false};
        final Runnable close=() -> {
            if(closing[0])return;closing[0]=true;
            if(!MufTheme.motionOn()||sheet.getHeight()==0){dialog.dismiss();return;}
            scrim.animate().alpha(0f).setDuration(200).setInterpolator(MufTheme.easeIn()).start();
            sheet.animate().translationY(sheet.getHeight()).setDuration(200).setInterpolator(MufTheme.easeIn()).withEndAction(dialog::dismiss).start();
        };
        if(secondary!=null){Button extra=sheetButton(secondaryLabel,false);extra.setOnClickListener(v -> secondary.run());actions.addView(extra);}
        Button done=sheetButton("Tutup",true);done.setOnClickListener(v -> close.run());actions.addView(done);
        sheet.addView(top);sheet.addView(scroll);sheet.addView(actionBar);
        root.addView(sheet,new android.widget.FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        scrim.setOnClickListener(v -> close.run());
        root.setOnApplyWindowInsetsListener((v,insets) -> {
            // Scrim spans the whole screen; only the sheet keeps clear of the side and navigation insets.
            ((android.widget.FrameLayout.LayoutParams)sheet.getLayoutParams()).setMargins(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),0);
            sheet.setPadding(0,0,0,insets.getSystemWindowInsetBottom());return insets;});
        // Drag down on the handle/title area; release decides between closing and springing back.
        top.setOnTouchListener(new View.OnTouchListener(){
            float downY;android.view.VelocityTracker velocity;
            @Override public boolean onTouch(View v,android.view.MotionEvent e){
                switch(e.getActionMasked()){
                    case android.view.MotionEvent.ACTION_DOWN: downY=e.getRawY();velocity=android.view.VelocityTracker.obtain();velocity.addMovement(e);sheet.animate().cancel();return true;
                    case android.view.MotionEvent.ACTION_MOVE: if(velocity!=null)velocity.addMovement(e);sheet.setTranslationY(Math.max(0f,e.getRawY()-downY));return true;
                    case android.view.MotionEvent.ACTION_UP: case android.view.MotionEvent.ACTION_CANCEL:
                        float speed=0f;if(velocity!=null){velocity.addMovement(e);velocity.computeCurrentVelocity(1000);speed=velocity.getYVelocity();velocity.recycle();velocity=null;}
                        if(e.getActionMasked()==android.view.MotionEvent.ACTION_UP && UiPresentation.dismissSheet(sheet.getTranslationY(),sheet.getHeight(),speed,getResources().getDisplayMetrics().density))close.run();
                        else if(MufTheme.motionOn())sheet.animate().translationY(0).setDuration(MufTheme.MOTION_SPRING).setInterpolator(MufTheme.spring()).start();
                        else sheet.setTranslationY(0);
                        return true;
                    default: return false;
                }
            }
        });
        dialog.setContentView(root);
        dialog.setOnKeyListener((d,code,event) -> { if(code==android.view.KeyEvent.KEYCODE_BACK){ if(event.getAction()==android.view.KeyEvent.ACTION_UP)close.run(); return true; } return false; });
        android.view.Window window=dialog.getWindow();
        float dim=.32f;
        if(window!=null){
            window.setLayout(-1,-1);window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));window.setWindowAnimations(0);
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            // Draw behind the system bars: the scrim covers the status bar and the sheet continues under a white navigation bar.
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);window.setNavigationBarColor(Color.WHITE);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                |View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            if(blurAvailable()){window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                android.view.WindowManager.LayoutParams attributes=window.getAttributes();attributes.setBlurBehindRadius(dp(24));window.setAttributes(attributes);dim=.2f;}
        }
        final float scrimTarget=dim;
        dialog.show();
        if(MufTheme.motionOn()){
            sheet.setVisibility(View.INVISIBLE);
            sheet.post(() -> { sheet.setTranslationY(sheet.getHeight());sheet.setVisibility(View.VISIBLE);
                sheet.animate().translationY(0).setDuration(MufTheme.MOTION_SPRING).setInterpolator(MufTheme.spring()).start();
                scrim.animate().alpha(scrimTarget).setDuration(200).setInterpolator(MufTheme.easeOut()).start(); });
        } else scrim.setAlpha(scrimTarget);
    }
    private boolean blurAvailable() {
        return android.os.Build.VERSION.SDK_INT>=31 && getWindowManager().isCrossWindowBlurEnabled();
    }
    private Button sheetButton(String label,boolean primary) {
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setMinWidth(dp(48));b.setMinimumWidth(dp(48));b.setPadding(dp(18),dp(10),dp(18),dp(10));
        MufTheme.flatten(b);b.setTextColor(primary?Color.WHITE:GREEN);
        b.setBackground(primary?MufTheme.touch(this,GREEN,MufTheme.R_CONTROL,false):MufTheme.touch(this,Color.TRANSPARENT,MufTheme.R_CONTROL,false));
        MufTheme.pressable(b);return b;
    }
    /** Header, body and actions; the body scrolls so the whole sheet stays within 85% of the window height. */
    private final class SheetLayout extends android.view.ViewGroup {
        SheetLayout(){super(MainActivity.this);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int width=MeasureSpec.getSize(widthSpec),available=MeasureSpec.getSize(heightSpec);
            int cap=(int)((available>0?available:getResources().getDisplayMetrics().heightPixels)*.85f)-getPaddingBottom();
            View top=getChildAt(0),body=getChildAt(1),actions=getChildAt(2);
            int exact=MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),free=MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED);
            top.measure(exact,free);actions.measure(exact,free);
            int room=Math.max(dp(96),cap-top.getMeasuredHeight()-actions.getMeasuredHeight());
            body.measure(exact,MeasureSpec.makeMeasureSpec(room,MeasureSpec.AT_MOST));
            setMeasuredDimension(width,top.getMeasuredHeight()+body.getMeasuredHeight()+actions.getMeasuredHeight()+getPaddingBottom());
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
            int y=0;for(int i=0;i<3;i++){View child=getChildAt(i);child.layout(0,y,getWidth(),y+child.getMeasuredHeight());y+=child.getMeasuredHeight();}
        }
    }
    private ScrollView textScroll(String value) { ScrollView scroll = new ScrollView(this); TextView t = text(value, 13, NAVY, false);
        t.setTextIsSelectable(true); t.setPadding(dp(22), dp(12), dp(22), dp(20)); scroll.addView(t); return scroll; }
    private void showText(String title, String value) { showDialog(new AlertDialog.Builder(this).setTitle(title).setView(textScroll(value)).setPositiveButton("Tutup", null)); }
    private AlertDialog showDialog(AlertDialog.Builder builder){
        if(isFinishing()||isDestroyed())return null;
        AlertDialog dialog=builder.create();
        if(dialog.getWindow()!=null)dialog.getWindow().setWindowAnimations(android.animation.ValueAnimator.areAnimatorsEnabled()?R.style.MufDialogAnimation:0);
        if(dialog.getWindow()!=null){
            // Blur behind only where Android enables cross-window blur (API 31+); otherwise a plain dim.
            if(blurAvailable()){dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                android.view.WindowManager.LayoutParams attributes=dialog.getWindow().getAttributes();attributes.setBlurBehindRadius(dp(24));dialog.getWindow().setAttributes(attributes);
                dialog.getWindow().setDimAmount(.2f);}
            else dialog.getWindow().setDimAmount(.32f);
        }
        dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(bg(Color.WHITE,MufTheme.R_SHEET,false));
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
            b.setTextColor(primary?Color.WHITE:GREEN); b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            b.setBackground(MufTheme.touch(this,danger?RED:primary?GREEN:Color.TRANSPARENT,MufTheme.R_CONTROL,false)); MufTheme.pressable(b); actions.addView(b);
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
