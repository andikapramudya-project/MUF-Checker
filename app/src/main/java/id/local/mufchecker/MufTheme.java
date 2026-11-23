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
    private MufTheme() { }
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
