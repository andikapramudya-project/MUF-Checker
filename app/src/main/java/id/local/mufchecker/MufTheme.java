package id.local.mufchecker;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

/** Presentation tokens; see DESIGN.md. No runtime design dependency. */
final class MufTheme {
    static final int NAVY = Color.rgb(29, 35, 33), GREEN = Color.rgb(0, 115, 80);
    static final int MUTED = Color.rgb(95, 105, 100), BACK = Color.rgb(245, 247, 246);
    static final int RED = Color.rgb(166, 45, 54), AMBER = NAVY;
    static final int SOFT_GREEN = Color.rgb(232, 243, 237), BORDER = Color.rgb(224, 230, 226), OVERLAY = Color.rgb(30, 39, 34);
    static final int SOFT_NEUTRAL = Color.rgb(239, 242, 240), SOFT_RED = Color.rgb(251, 236, 238);
    /**
     * Motion tokens (ms). One calm curve family: content enters on a long ease-out, leaves on a short ease-in.
     * No overshoot or bounce. Every Animator follows the Android animation scale; at 0 it jumps to the end state.
     */
    static final int MOTION_EXIT = 90, MOTION_QUICK = 160, MOTION_ENTER = 220, MOTION_MOVE = 300, MOTION_COUNT = 420;
    private MufTheme() { }
    /** Emphasized ease-out for elements arriving or settling (cubic-bezier 0.2, 0, 0, 1). */
    static android.view.animation.Interpolator easeOut() { return new android.view.animation.PathInterpolator(.2f, 0f, 0f, 1f); }
    /** Ease-in for elements leaving the screen (cubic-bezier 0.3, 0, 1, 1). */
    static android.view.animation.Interpolator easeIn() { return new android.view.animation.PathInterpolator(.3f, 0f, 1f, 1f); }
    /** Standard ease-in-out for elements moving between two resting places (cubic-bezier 0.4, 0, 0.2, 1). */
    static android.view.animation.Interpolator move() { return new android.view.animation.PathInterpolator(.4f, 0f, .2f, 1f); }
    static boolean motionOn() { return android.animation.ValueAnimator.areAnimatorsEnabled(); }
    /** Rounded track + fill for horizontal progress, replacing the platform's square default. */
    static android.graphics.drawable.Drawable progressTrack(Context c) {
        GradientDrawable track = shape(c, SOFT_NEUTRAL, 3, false), fill = shape(c, GREEN, 3, false);
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
    static RippleDrawable touch(Context c, int color, int radius, boolean border) {
        return new RippleDrawable(ColorStateList.valueOf(0x26007350), shape(c,color,radius,border), null);
    }
    static void flatten(android.widget.Button button) {
        button.setStateListAnimator(null); button.setElevation(0); button.setTranslationZ(0);
    }
    /** Static translucent surface; no blur or repeated background capture. */
    static GradientDrawable glass(Context c) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.argb(220, 50, 68, 61), Color.argb(204, 27, 45, 38), Color.argb(212, 24, 39, 33)});
        d.setCornerRadius(dp(c, 22)); d.setStroke(dp(c, 1), Color.argb(105, 255, 255, 255));
        return d;
    }
}
