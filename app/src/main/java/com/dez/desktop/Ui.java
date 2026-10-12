package com.dez.desktop;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

/** Shared colours and small view factories for the DeZ shell (DeX-like dark theme). */
public final class Ui {
    public static final int TASKBAR_BG = 0xEB0E1116;
    public static final int PANEL_BG = 0xF51A1D23;
    public static final int WINDOW_BG = 0xFF1E2128;
    public static final int SURFACE = 0xFF262A32;
    public static final int TITLE_FOCUSED = 0xFF2E333D;
    public static final int TITLE_UNFOCUSED = 0xFF22252B;
    public static final int ACCENT = 0xFF3D8BFF;
    public static final int TEXT = 0xFFECEFF4;
    public static final int TEXT_DIM = 0xFF9AA3B2;
    public static final int DIVIDER = 0x33FFFFFF;
    public static final int RIPPLE = 0x33FFFFFF;
    public static final int DANGER = 0xFFE5484D;

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static float pxToDp(Context c, int px) {
        return px / c.getResources().getDisplayMetrics().density;
    }

    public static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    /** Ripple bounded by a rounded rectangle, optionally over a solid colour. */
    public static Drawable ripple(Context c, int baseColor, float radiusDp) {
        float r = dp(c, radiusDp);
        Drawable content = baseColor == 0 ? null : rounded(baseColor, r);
        return new RippleDrawable(ColorStateList.valueOf(RIPPLE), content, rounded(0xFFFFFFFF, r));
    }

    public static ImageButton iconButton(Context c, int iconRes, int sizeDp, int padDp,
                                         CharSequence tooltip, View.OnClickListener l) {
        ImageButton b = new ImageButton(c);
        b.setImageResource(iconRes);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        b.setImageTintList(ColorStateList.valueOf(TEXT));
        b.setBackground(ripple(c, 0, 6));
        int p = dp(c, padDp);
        b.setPadding(p, p, p, p);
        b.setMinimumWidth(dp(c, sizeDp));
        b.setMinimumHeight(dp(c, sizeDp));
        if (tooltip != null) {
            b.setContentDescription(tooltip);
            b.setTooltipText(tooltip);
        }
        b.setOnClickListener(l);
        return b;
    }

    public static TextView text(Context c, CharSequence s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    public static Button button(Context c, CharSequence s, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setBackground(ripple(c, SURFACE, 8));
        int h = dp(c, 14);
        b.setPadding(h, 0, h, 0);
        b.setMinHeight(dp(c, 40));
        b.setMinimumHeight(dp(c, 40));
        b.setGravity(Gravity.CENTER);
        b.setOnClickListener(l);
        return b;
    }
}
