package com.dez.desktop.wm;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dez.desktop.R;
import com.dez.desktop.Ui;

/**
 * The simple frame every DeZ window is drawn with: a rounded, bordered box made of a title bar
 * (icon, title, minimise / maximise / close) above a content area.
 *
 * <pre>
 * ┌──────────────────────────────────────────┐
 * │ [icon] Title                  ─   □   ✕  │  ← title bar
 * ├──────────────────────────────────────────┤
 * │                                          │
 * │                 content                  │
 * │                                          │
 * └──────────────────────────────────────────┘
 * </pre>
 *
 * It only draws and reports button presses; moving, resizing and snapping are done by
 * {@link DesktopWindow}. It can also be used on its own:
 *
 * <pre>
 * WindowFrame frame = new WindowFrame(context);
 * frame.setTitle("Hello");
 * frame.setContent(myView);
 * frame.setListener(new WindowFrame.Listener() { ... });
 * </pre>
 */
public class WindowFrame extends LinearLayout {
    /** Title-bar button presses. */
    public interface Listener {
        void onMinimize();

        void onToggleMaximize();

        void onClose();
    }

    private static final int TITLE_HEIGHT_DP = 38;
    private static final int BUTTON_DP = 40;
    private static final int CORNER_DP = 10;

    private final LinearLayout titleBar;
    private final ImageView iconView;
    private final TextView titleView;
    private final ImageButton maxButton;
    private final FrameLayout contentArea;
    private final GradientDrawable background;
    private Listener listener;

    public WindowFrame(Context c) {
        super(c);
        setOrientation(VERTICAL);
        background = Ui.rounded(Ui.WINDOW_BG, Ui.dp(c, CORNER_DP));
        setBackground(background);
        setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        setClipToOutline(true);

        // Title bar: [icon] [title .........] [─] [□] [✕]
        titleBar = new LinearLayout(c);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 2), 0);
        addView(titleBar, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, TITLE_HEIGHT_DP)));

        iconView = new ImageView(c);
        titleBar.addView(iconView, new LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20)));

        titleView = Ui.text(c, "", 13, Ui.TEXT);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 8), 0);
        titleBar.addView(titleView, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        addTitleButton(R.drawable.ic_minimize, R.string.win_minimize, v -> {
            if (listener != null) listener.onMinimize();
        });
        maxButton = addTitleButton(R.drawable.ic_maximize, R.string.win_maximize, v -> {
            if (listener != null) listener.onToggleMaximize();
        });
        addTitleButton(R.drawable.ic_close, R.string.win_close, v -> {
            if (listener != null) listener.onClose();
        });

        // Content area fills the rest of the frame.
        contentArea = new FrameLayout(c);
        addView(contentArea, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        setActive(true);
    }

    private ImageButton addTitleButton(int icon, int label, OnClickListener l) {
        Context c = getContext();
        ImageButton b = Ui.iconButton(c, icon, BUTTON_DP, 10, c.getString(label), l);
        titleBar.addView(b, new LayoutParams(Ui.dp(c, BUTTON_DP), LayoutParams.MATCH_PARENT));
        return b;
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setTitle(CharSequence title) {
        titleView.setText(title);
    }

    public CharSequence getTitle() {
        return titleView.getText();
    }

    public void setIcon(Drawable icon) {
        iconView.setImageDrawable(icon);
        iconView.setVisibility(icon == null ? GONE : VISIBLE);
    }

    /** Replaces whatever is shown below the title bar. */
    public void setContent(View content) {
        contentArea.removeAllViews();
        if (content != null) {
            contentArea.addView(content, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
    }

    public View getContent() {
        return contentArea.getChildCount() > 0 ? contentArea.getChildAt(0) : null;
    }

    /** The view to attach drag / double-tap handling to. */
    public View getTitleBar() {
        return titleBar;
    }

    /** Active windows get a brighter title bar and an accent border. */
    public void setActive(boolean active) {
        Context c = getContext();
        titleBar.setBackgroundColor(active ? Ui.TITLE_FOCUSED : Ui.TITLE_UNFOCUSED);
        titleView.setAlpha(active ? 1f : 0.6f);
        background.setStroke(Ui.dp(c, 1), active ? 0x803D8BFF : Ui.DIVIDER);
    }

    /** Swaps the middle button between "maximise" (□) and "restore" (⧉). */
    public void setMaximized(boolean maximized) {
        maxButton.setImageResource(maximized ? R.drawable.ic_restore : R.drawable.ic_maximize);
        String label = getContext().getString(maximized ? R.string.win_restore : R.string.win_maximize);
        maxButton.setContentDescription(label);
        maxButton.setTooltipText(label);
    }
}
