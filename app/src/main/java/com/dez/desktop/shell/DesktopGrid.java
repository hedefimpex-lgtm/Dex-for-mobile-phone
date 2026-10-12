package com.dez.desktop.shell;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dez.desktop.AppInfo;
import com.dez.desktop.Ui;

import java.util.List;

/** Desktop shortcut icons, laid out top-to-bottom then left-to-right like a PC / DeX desktop. */
@SuppressLint("ViewConstructor")
public class DesktopGrid extends ViewGroup {
    public interface Callbacks {
        void onDesktopIconClick(AppInfo app);

        void onDesktopIconLongClick(AppInfo app, View anchor);

        void onDesktopLongPress(float x, float y);
    }

    private final Callbacks callbacks;
    private int iconSize;
    private int cellW;
    private int cellH;
    private final int pad;
    private float lastX, lastY;

    public DesktopGrid(Context c, Callbacks callbacks) {
        super(c);
        this.callbacks = callbacks;
        pad = Ui.dp(c, 10);
        setIconMetrics(46, 6);
        setLongClickable(true);
        setOnLongClickListener(v -> {
            callbacks.onDesktopLongPress(lastX, lastY);
            return true;
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            lastX = ev.getX();
            lastY = ev.getY();
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * Icon size and the extra gap between icons, in dp. Each cell leaves room for a two-line
     * label; call {@link #setApps} afterwards to rebuild the icons.
     */
    public void setIconMetrics(int iconDp, int spacingDp) {
        Context c = getContext();
        iconSize = Ui.dp(c, iconDp);
        cellW = Ui.dp(c, Math.max(iconDp + 36, 72) + spacingDp);
        cellH = Ui.dp(c, iconDp + 44 + spacingDp);
        requestLayout();
    }

    public void setApps(List<AppInfo> apps) {
        removeAllViews();
        Context c = getContext();
        for (AppInfo app : apps) {
            LinearLayout cell = new LinearLayout(c);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 4));
            cell.setBackground(Ui.ripple(c, 0, 10));
            ImageView icon = new ImageView(c);
            icon.setImageDrawable(app.icon);
            cell.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
            TextView label = Ui.text(c, app.label, 12, 0xFFFFFFFF);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            label.setMaxLines(2);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setShadowLayer(4, 0, 1, 0xCC000000);
            label.setPadding(0, Ui.dp(c, 4), 0, 0);
            cell.addView(label, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            cell.setOnClickListener(v -> callbacks.onDesktopIconClick(app));
            cell.setOnLongClickListener(v -> {
                callbacks.onDesktopIconLongClick(app, v);
                return true;
            });
            addView(cell);
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int cw = MeasureSpec.makeMeasureSpec(cellW, MeasureSpec.EXACTLY);
        int ch = MeasureSpec.makeMeasureSpec(cellH, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(cw, ch);
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int rows = Math.max(1, (b - t - pad * 2) / cellH);
        for (int i = 0; i < getChildCount(); i++) {
            int col = i / rows;
            int row = i % rows;
            int x = pad + col * cellW;
            int y = pad + row * cellH;
            getChildAt(i).layout(x, y, x + cellW, y + cellH);
        }
    }
}
