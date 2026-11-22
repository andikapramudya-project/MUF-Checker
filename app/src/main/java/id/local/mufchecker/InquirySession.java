package id.local.mufchecker;

/** Bounded stages and one-shot actions. Clock supplied by caller for deterministic regression tests. */
public final class InquirySession {
    public enum Stage { OPEN_LINK, RETURN_FORM, FIND_FORM, OPEN_CREDIT, OPEN_FORM, SELECT_PROVIDER, VERIFY_PROVIDER,
        FILL_CONTRACT, VERIFY_CONTRACT, WAIT_RESULT, READ_DETAILS }
    public enum TapState { NONE, RESERVED, DISPATCHED, COMPLETED, CANCELLED, REJECTED }
    public enum ReturnAction { USE_FORM, BACK, OPEN_LINK, PAUSE, WAIT }
    public static final long SESSION_LIMIT = 120000;
    private final long started;
    private final boolean linkRecoveryAllowed;
    private long stageStarted;
    private final java.util.Map<String, Long> stageMillis = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, Long> reportedMillis = new java.util.LinkedHashMap<>();
    private Stage stage = Stage.FIND_FORM;
    private boolean submitted, detailsOpened;
    private TapState tapState = TapState.NONE;
    private boolean resultObserved;
    private boolean returnBackIssued, returnLinkIssued;
    private long returnBackAt, returnProgressAt;
    public void returnProgress(long now) { returnProgressAt = now; }
    public long elapsed(long now) { return Math.max(0, now - started); }
    private int scrolls, inputAttempts;
    public InquirySession(long now) { this(now, true); }
    public InquirySession(long now, boolean linkRecoveryAllowed) {
        started = stageStarted = now; this.linkRecoveryAllowed = linkRecoveryAllowed;
    }
    public Stage stage() { return stage; }
    public long stageAge(long now) { return Math.max(0, now - stageStarted); }
    public void move(Stage next, long now) {
        if (stage != next) {
            String key = stage.name(); stageMillis.put(key, stageMillis.getOrDefault(key, 0L) + stageAge(now));
            stage = next; stageStarted = now; scrolls = 0;
        }
    }
    /** Delta since the previous durable checkpoint, including stages revisited by bounded retries. */
    public java.util.Map<String, Long> drainDurations(long now) {
        java.util.Map<String, Long> totals = new java.util.LinkedHashMap<>(stageMillis);
        String key = stage.name(); totals.put(key, totals.getOrDefault(key, 0L) + stageAge(now));
        java.util.Map<String, Long> delta = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, Long> entry : totals.entrySet()) {
            long difference = Math.max(0, entry.getValue() - reportedMillis.getOrDefault(entry.getKey(), 0L));
            if (difference > 0) delta.put(entry.getKey(), difference);
        }
        reportedMillis.clear(); reportedMillis.putAll(totals); return delta;
    }
    public long stageLimit() {
        return stage == Stage.WAIT_RESULT || stage == Stage.READ_DETAILS ? 25000 : 20000;
    }
    public boolean expired(long now) {
        return now - started >= SESSION_LIMIT || stageAge(now) >= stageLimit();
    }
    public boolean submitted() { return submitted; }
    public ReturnAction returnAction(boolean formReady, boolean tokopediaForeground, boolean checkerForeground, long now) {
        if (stage != Stage.RETURN_FORM) return ReturnAction.WAIT;
        if (formReady && tokopediaForeground) return ReturnAction.USE_FORM;
        if (!returnBackIssued && tokopediaForeground) { returnBackIssued = true; returnBackAt = now; return ReturnAction.BACK; }
        if (returnBackIssued && !returnLinkIssued && now - returnBackAt >= 6000
            && (now - returnProgressAt >= 2000 || now - returnBackAt >= 10000) && (tokopediaForeground || checkerForeground)) {
            returnLinkIssued = true; return linkRecoveryAllowed ? ReturnAction.OPEN_LINK : ReturnAction.PAUSE;
        }
        return ReturnAction.WAIT;
    }
    public TapState tapState() { return tapState; }
    public boolean resultObserved() { return resultObserved; }
    public boolean markTapDispatched() {
        if (tapState != TapState.RESERVED) return false;
        tapState = TapState.DISPATCHED; return true;
    }
    public boolean markTapCompleted() {
        if (tapState != TapState.DISPATCHED) return false;
        tapState = TapState.COMPLETED; return true;
    }
    public boolean markTapCancelled() {
        if (tapState != TapState.DISPATCHED) return false;
        tapState = TapState.CANCELLED; return true;
    }
    public boolean markTapRejected() {
        if (tapState != TapState.RESERVED) return false;
        tapState = TapState.REJECTED; return true;
    }
    public boolean markResultObserved() {
        if (!submitted || (tapState != TapState.DISPATCHED && tapState != TapState.COMPLETED)) return false;
        resultObserved = true; return true;
    }
    public int inputAttempts() { return inputAttempts; }
    public boolean markInputAttempt() {
        if (submitted || stage != Stage.FILL_CONTRACT || inputAttempts >= 2) return false;
        inputAttempts++;
        return true;
    }
    public boolean retryEmptyInput(String actual, long now) {
        if (submitted || stage != Stage.VERIFY_CONTRACT || actual == null || !actual.isEmpty()
            || inputAttempts >= 2 || stageAge(now) < 2000) return false;
        move(Stage.FILL_CONTRACT, now);
        return true;
    }
    public boolean markSubmitted(boolean provider, boolean input, boolean checkout, String expected, String actual, long now) {
        if (stage != Stage.VERIFY_CONTRACT || !InquiryRules.maySubmit(provider, input, checkout, expected, actual, submitted)) return false;
        submitted = true;
        tapState = TapState.RESERVED;
        move(Stage.WAIT_RESULT, now);
        return true;
    }
    public boolean detailsOpened() { return detailsOpened; }
    public boolean markDetailsOpened(long now) {
        if (!submitted || detailsOpened || (stage != Stage.WAIT_RESULT && stage != Stage.READ_DETAILS)) return false;
        detailsOpened = true;
        move(Stage.READ_DETAILS, now);
        return true;
    }
    public boolean allowScroll() { if (scrolls >= 6) return false; scrolls++; return true; }
    public int scrolls() { return scrolls; }
}
