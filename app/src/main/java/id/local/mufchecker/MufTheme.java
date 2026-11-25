package id.local.mufchecker;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.MotionEvent;
import android.view.View;

/**
 * Presentation tokens for 0.9.0; see DESIGN.md. Light theme only. The grouped-surface hierarchy, spring feel and
 * floating bar follow principles observed in BitChord (GPL-3.0); numbers are re-derived here and no code, asset or
 * font is copied. No runtime design dependency.
 */
final class MufTheme {
    /** Primary text, secondary text, page background, grouped card surface, secondary fill, hairline. */
    static final int NAVY = Color.rgb(17, 17, 17), MUTED = Color.rgb(110, 110, 115), BACK = Color.WHITE;
    static final int SURFACE = Color.rgb(247, 247, 249), FILL = Color.rgb(242, 242, 247), BORDER = Color.rgb(229, 229, 234);
    /** The only accent (MUF green) and its 14% fill; red only for Error, STOP and delete. */
    static final int GREEN = Color.rgb(0, 115, 80), ACCENT_FILL = Color.argb(36, 0, 115, 80), RED = Color.rgb(166, 45, 54);
    /** ACCENT_FILL composited on white, for places that need an opaque surface. */
    static final int SOFT_GREEN = Color.rgb(219, 235, 230), SOFT_NEUTRAL = FILL, SOFT_RED = Color.rgb(251, 236, 238);
    static final int AMBER = NAVY, OVERLAY = Color.rgb(30, 39, 34);
    /** Radii (dp). */
    static final int R_CARD = 20, R_CONTROL = 12, R_PRIMARY = 14, R_SHEET = 24;
    /**
     * Motion (ms). SPRING runs long enough for the damped spring to settle; QUICK is the colour tween;
     * EXIT is the short fade-out; ENTER/COUNT keep the 0.8.6 page and number motion.
     */
    static final int MOTION_EXIT = 90, MOTION_QUICK = 200, MOTION_PRESS = 120, MOTION_ENTER = 220, MOTION_SPRING = 450, MOTION_COUNT = 420;
    /** Kept for the 0.8.6 indicator timing name used by older callers. */
    static final int MOTION_MOVE = MOTION_SPRING;
    private MufTheme() { }
    /** Emphasized ease-out for elements arriving or settling (cubic-bezier 0.2, 0, 0, 1). */
    static android.view.animation.Interpolator easeOut() { return new android.view.animation.PathInterpolator(.2f, 0f, 0f, 1f); }
    /** Ease-in for elements leaving the screen (cubic-bezier 0.3, 0, 1, 1). */
    static android.view.animation.Interpolator easeIn() { return new android.view.animation.PathInterpolator(.3f, 0f, 1f, 1f); }
    /** Standard ease-in-out for colour and state changes (cubic-bezier 0.4, 0, 0.2, 1). */
    static android.view.animation.Interpolator move() { return new android.view.animation.PathInterpolator(.4f, 0f, .2f, 1f); }
    /** Slightly bouncy spring (stiffness 320, damping ratio 0.72, ~3.8% overshoot); the math lives in UiPresentation. */
    static android.view.animation.Interpolator spring() { return UiPresentation::spring; }
    static boolean motionOn() { return android.animation.ValueAnimator.areAnimatorsEnabled(); }
    /** Rounded 6 dp track + fill for horizontal progress. */
    static android.graphics.drawable.Drawable progressTrack(Context c) {
        GradientDrawable track = shape(c, FILL, 3, false), fill = shape(c, GREEN, 3, false);
        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
            track, new android.graphics.drawable.ClipDrawable(fill, android.view.Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL)});
        layers.setId(0, android.R.id.background); layers.setId(1, android.R.id.progress);
        return layers;
    }
    static int dp(Context c, int value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable shape(Context c, int color, int radius, boolean border) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c, radius));
        if (border) d.setStroke(dp(c, 1), BORDER); return d;
    }
    /** Pressed overlay is a neutral 4% black wash; the accent stays reserved for selection. */
    static RippleDrawable touch(Context c, int color, int radius, boolean border) { return touch(shape(c, color, radius, border)); }
    static RippleDrawable touch(android.graphics.drawable.Drawable content) {
        GradientDrawable mask = new GradientDrawable(); mask.setColor(Color.BLACK);
        if (content instanceof GradientDrawable) mask.setCornerRadius(((GradientDrawable) content).getCornerRadius());
        return new RippleDrawable(ColorStateList.valueOf(0x0A000000), content, content == null ? null : mask);
    }
    static void flatten(android.widget.Button button) {
        button.setStateListAnimator(null); button.setElevation(0); button.setTranslationZ(0);
    }
    /**
     * Press feedback: scale to 0.97 on touch-down, spring back on release. Purely visual; it never consumes the
     * event, so click handling, focus and accessibility actions are unchanged. Disabled views do not react.
     */
    static void pressable(View view) {
        view.setOnTouchListener((v, event) -> {
            if (!v.isEnabled() || !motionOn()) return false;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                v.animate().cancel();
                v.animate().scaleX(.97f).scaleY(.97f).setDuration(MOTION_PRESS).setInterpolator(easeOut()).start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.animate().cancel();
                v.animate().scaleX(1f).scaleY(1f).setDuration(MOTION_SPRING).setInterpolator(spring()).start();
            }
            return false;
        });
    }
    /** Static translucent surface for the Tokopedia panel; no blur or repeated background capture. */
    static GradientDrawable glass(Context c) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.argb(220, 50, 68, 61), Color.argb(204, 27, 45, 38), Color.argb(212, 24, 39, 33)});
        d.setCornerRadius(dp(c, 22)); d.setStroke(dp(c, 1), Color.argb(105, 255, 255, 255));
        return d;
    }
}
