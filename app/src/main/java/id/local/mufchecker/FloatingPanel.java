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
    private float shown, target; private android.animation.ValueAnimator mover;
    private final android.graphics.Paint line=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    /** Thin bottom progress line; presentation only, never read by the queue. */
    void setProgress(int done,int total){
        float next=total<=0?0f:Math.max(0f,Math.min(1f,done/(float)total));
        if(next==target)return;target=next;
        if(mover!=null)mover.cancel();
        if(!MufTheme.motionOn()||!isAttachedToWindow()){shown=next;invalidate();return;}
        mover=android.animation.ValueAnimator.ofFloat(shown,next).setDuration(MufTheme.MOTION_SLOW);
        mover.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
        mover.addUpdateListener(a->{shown=(Float)a.getAnimatedValue();invalidate();});mover.start();
    }
    @Override protected void dispatchDraw(android.graphics.Canvas canvas){
        super.dispatchDraw(canvas);
        if(shown<=0f)return;
        float inset=dp(14),width=(getWidth()-2*inset),y=getHeight()-dp(3);
        line.setStrokeCap(android.graphics.Paint.Cap.ROUND);line.setStrokeWidth(dp(2));
        line.setColor(Color.argb(60,255,255,255));canvas.drawLine(inset,y,inset+width,y,line);
        line.setColor(Color.rgb(110,220,170));canvas.drawLine(inset,y,inset+width*shown,y,line);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();MufTheme.rise(this,8,MufTheme.MOTION_ENTER);}
    @Override protected void onDetachedFromWindow(){if(mover!=null)mover.cancel();super.onDetachedFromWindow();}
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
