package id.local.mufchecker;

/** Ephemeral receipt for one accepted write. It only permits closing an IME, never an inquiry. */
public final class InputVerification {
    public enum Action { FULL_READ, WAIT, CLOSE_KEYBOARD }
    public static final long PROBE_MS = 250, FULL_READ_MS = 500;
    public static final class Field {
        public final String contract, packageName, viewId, className;
        public final int window, left, top, right, bottom;
        public final boolean visible, enabled, focused, writable;
        public Field(String contract, String packageName, String viewId, String className,
                     int window, int left, int top, int right, int bottom,
                     boolean visible, boolean enabled, boolean focused, boolean writable) {
            this.contract = clean(contract); this.packageName = clean(packageName);
            this.viewId = clean(viewId); this.className = clean(className); this.window = window;
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
            this.visible = visible; this.enabled = enabled; this.focused = focused; this.writable = writable;
        }
    }
    private Field target;
    private long acceptedAt = -1;
    private static String clean(String value) { return value == null ? "" : value; }
    public void clear() { target = null; acceptedAt = -1; }
    public boolean arm(Field verifiedTarget, long now) {
        clear();
        if (verifiedTarget == null || now < 0 || verifiedTarget.window < 0
            || !verifiedTarget.visible || !verifiedTarget.enabled || !verifiedTarget.writable
            || !verifiedTarget.contract.matches("[0-9]{8,20}")
            || !"com.tokopedia.tkpd".equals(verifiedTarget.packageName) || verifiedTarget.className.isEmpty()
            || verifiedTarget.right <= verifiedTarget.left || verifiedTarget.bottom <= verifiedTarget.top) return false;
        target = verifiedTarget; acceptedAt = now; return true;
    }
    public boolean eligible(boolean readyPage, InquirySession.Stage stage, boolean submitted,
                            int attempts, boolean keyboardDismissed, String contract) {
        return target != null && readyPage && stage == InquirySession.Stage.VERIFY_CONTRACT
            && !submitted && attempts > 0 && attempts <= 2 && !keyboardDismissed
            && target.contract.equals(contract);
    }
    public long fullReadAt() { return acceptedAt < 0 ? -1 : acceptedAt + FULL_READ_MS; }
    public Action decide(long now, int rootWindow, boolean keyboard, Field fresh) {
        if (target == null || now < acceptedAt) return Action.FULL_READ;
        if (now - acceptedAt < PROBE_MS) return Action.WAIT;
        if (keyboard && fresh != null && fresh.visible && fresh.enabled && fresh.focused && fresh.writable
            && target.window == rootWindow && fresh.window == rootWindow
            && target.packageName.equals(fresh.packageName) && target.viewId.equals(fresh.viewId)
            && target.className.equals(fresh.className) && target.contract.equals(fresh.contract)
            && target.left == fresh.left && target.top == fresh.top
            && target.right == fresh.right && target.bottom == fresh.bottom) return Action.CLOSE_KEYBOARD;
        return now < fullReadAt() ? Action.WAIT : Action.FULL_READ;
    }
}
