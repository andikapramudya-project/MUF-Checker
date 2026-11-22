package id.local.mufchecker;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;

/** Bounded screen lock for cross-app inquiry; never wakes/unlocks a sleeping device. */
final class ScreenAwake {
    private final PowerManager power;
    private PowerManager.WakeLock lock;
    private long renewed;
    ScreenAwake(Context context) { power = (PowerManager) context.getSystemService(Context.POWER_SERVICE); }
    @SuppressWarnings("deprecation")
    boolean maintain(boolean enabled) {
        if (!enabled || power == null || !power.isInteractive()) { release(); return false; }
        try {
            // KEEP_SCREEN_ON alone is not reliable when Tokopedia owns the foreground activity.
            // This supported legacy level is bounded, with no ACQUIRE_CAUSES_WAKEUP flag.
            if (lock == null) {
                if (!power.isWakeLockLevelSupported(PowerManager.SCREEN_BRIGHT_WAKE_LOCK)) return false;
                lock = power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "mufchecker:inquiry-screen");
                lock.setReferenceCounted(false);
            }
            if (!lock.isHeld() || SystemClock.elapsedRealtime() - renewed >= 60000) {
                lock.acquire(125000); renewed = SystemClock.elapsedRealtime();
            }
            return lock.isHeld();
        } catch (RuntimeException e) { release(); return false; }
    }
    void release() {
        if (lock != null) { try { if (lock.isHeld()) lock.release(); } catch (RuntimeException ignored) {} lock = null; }
    }
}
