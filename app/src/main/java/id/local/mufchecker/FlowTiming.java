package id.local.mufchecker;

/** Read sooner in the ready-page route; action and result guards still decide what may proceed. */
public final class FlowTiming {
    private FlowTiming() { }
    public static final long MIN_INSPECT_MS = 250, FALLBACK_MS = 700, RESULT_STABLE_MS = 400;
    public static long keyboard(boolean readyPage) { return readyPage ? 250 : 900; }
    public static long inquiry(boolean readyPage) { return readyPage ? 250 : 1400; }
    public static long details(boolean readyPage) { return readyPage ? 250 : 1200; }
    public static long nextItem(boolean readyPage, boolean returnFromResult) {
        return returnFromResult ? (readyPage ? 300 : 700) : readyPage ? 0 : 1200;
    }
    /** Only called after the provider and unique contract form have been verified. */
    public static boolean needsFocus(boolean readyPage, boolean fieldFocused) {
        return !readyPage || !fieldFocused;
    }
    public static boolean maySearchDetails(boolean readyPage, boolean opened, long stageAge) {
        return !readyPage || !opened || stageAge >= 1200;
    }
    public static boolean mayVerifyDetails(boolean readyPage, boolean opened, long stageAge, boolean completeExtras) {
        return !readyPage || !opened || stageAge >= 1200 || completeExtras;
    }
    public static long nextInspection(boolean readyPage, long now, long lastInspection,
                                      long actionNotBefore, long stableNotBefore) {
        if (!readyPage) return FALLBACK_MS;
        long delay = actionNotBefore > now ? Math.min(FALLBACK_MS, actionNotBefore - now) : FALLBACK_MS;
        if (stableNotBefore >= 0) delay = Math.min(delay, Math.max(0, stableNotBefore - now));
        return Math.max(Math.max(0, lastInspection + MIN_INSPECT_MS - now), delay);
    }
}
