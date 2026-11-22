package id.local.mufchecker;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Native presentation only. The service owns readiness, callbacks, window flags and queue timing. */
final class FloatingPanel extends LinearLayout {
    final TextView message;
    final Button primary, stop;
    FloatingPanel(Context context,boolean preparing,String label) {
        super(context);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(10),dp(4),dp(8),dp(4));setBackground(MufTheme.glass(context));
        message=new TextView(context);message.setText(label);message.setTextColor(Color.WHITE);message.setTextSize(13);
        message.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));message.setPadding(dp(2),0,dp(8),0);
        message.setLineSpacing(dp(2),1f);message.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        android.graphics.drawable.Drawable mark=context.getDrawable(R.drawable.ic_check).mutate();mark.setTint(Color.WHITE);mark.setBounds(0,0,dp(18),dp(18));
        message.setCompoundDrawables(mark,null,null,null);message.setCompoundDrawablePadding(dp(6));
        addView(message,new LayoutParams(0,-2,1));
        primary=action(preparing?"Mulai":"Jeda",preparing,false);primary.setEnabled(!preparing);primary.setAlpha(preparing?.45f:1f);
        primary.setContentDescription(preparing?"Mulai setelah form MUF siap":"Jeda setelah kontrak aktif selesai");
        addView(primary,new LayoutParams(-2,-2));
        stop=action(preparing?"Batal":"STOP",false,!preparing);
        stop.setContentDescription(preparing?"Batalkan persiapan":"STOP, hentikan pemeriksaan segera");
        LayoutParams stopLp=new LayoutParams(-2,-2);stopLp.leftMargin=dp(6);addView(stop,stopLp);
    }
    private int dp(int value){return MufTheme.dp(getContext(),value);}
    private Button action(String label,boolean main,boolean danger) {
        Button button=new Button(getContext());button.setText(label);button.setAllCaps(false);button.setTextSize(13);
        button.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));button.setTextColor(Color.WHITE);
        button.setMinWidth(dp(64));button.setMinimumWidth(dp(64));button.setMinHeight(dp(48));button.setMinimumHeight(dp(48));
        button.setPadding(dp(10),dp(8),dp(10),dp(8));MufTheme.flatten(button);
        int color=main?MufTheme.GREEN:danger?Color.argb(110,166,45,54):Color.argb(28,255,255,255);
        GradientDrawable surface=MufTheme.shape(getContext(),color,18,false);surface.setStroke(dp(1),Color.argb(main?65:90,255,255,255));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF),surface,null));return button;
    }
}
