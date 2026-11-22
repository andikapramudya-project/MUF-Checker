package id.local.mufchecker;

/** Two fresh form observations plus an explicit user click. No screen actions while armed. */
public final class ReadyPageGate {
    public static final long STABLE_MS = 250, FRESH_MS = 5000;
    private long first = -1, latest = -1;
    private int observations;
    private boolean consumed;

    public void reset() { first = latest = -1; observations = 0; consumed = false; }
    public void observe(boolean ready, long now) {
        if (consumed) return;
        if (!ready) { first = latest = -1; observations = 0; return; }
        if (latest < 0 || now < latest || now - latest > FRESH_MS) { first = now; observations = 0; }
        latest = now; observations++;
    }
    public boolean canStart(long now) {
        return !consumed && observations >= 2 && first >= 0 && now >= latest
            && now - latest <= FRESH_MS && now - first >= STABLE_MS;
    }
    public boolean consume(long now) {
        if (!canStart(now)) return false;
        consumed = true; return true;
    }
    public static boolean formReady(boolean tokopedia, boolean creditTitle, boolean provider,
                                    boolean writableContract, boolean checkout, boolean blocked, boolean providerList) {
        return tokopedia && creditTitle && provider && writableContract && !checkout && !blocked && !providerList;
    }
}
