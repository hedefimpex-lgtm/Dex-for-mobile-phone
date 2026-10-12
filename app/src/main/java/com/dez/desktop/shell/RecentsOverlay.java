package com.dez.desktop.shell;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dez.desktop.R;
import com.dez.desktop.Ui;

import java.util.List;

/** "Recent apps" overview: a card with a live thumbnail for every open window. */
@SuppressLint("ViewConstructor")
public class RecentsOverlay extends FrameLayout {
    public static final class Card {
        public final Object token;
        public final CharSequence title;
        public final Drawable icon;
        /** View to snapshot for the thumbnail, or null to show the icon. */
        public final View snapshotSource;

        public Card(Object token, CharSequence title, Drawable icon, View snapshotSource) {
            this.token = token;
            this.title = title;
            this.icon = icon;
            this.snapshotSource = snapshotSource;
        }
    }

    public interface Callbacks {
        void onRecentSelected(Object token);

        void onRecentClosed(Object token);

        void onRecentsCloseAll();
    }

    private final Callbacks callbacks;
    private final LinearLayout row;
    private final TextView empty;

    public RecentsOverlay(Context c, Callbacks callbacks) {
        super(c);
        this.callbacks = callbacks;
        setBackgroundColor(0xB3000000);
        setVisibility(GONE);
        setClickable(true);

        HorizontalScrollView scroller = new HorizontalScrollView(c);
        scroller.setHorizontalScrollBarEnabled(false);
        row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(c, 24), 0, Ui.dp(c, 24), 0);
        scroller.addView(row);
        addView(scroller, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        empty = Ui.text(c, c.getString(R.string.recents_empty), 16, Ui.TEXT_DIM);
        addView(empty, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        TextView closeAll = Ui.button(c, c.getString(R.string.recents_close_all),
                v -> callbacks.onRecentsCloseAll());
        LayoutParams clp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        clp.bottomMargin = Ui.dp(c, 20);
        addView(closeAll, clp);
    }

    public boolean isShowing() {
        return getVisibility() == VISIBLE;
    }

    public void show(List<Card> cards) {
        bind(cards);
        setVisibility(VISIBLE);
        bringToFront();
        setAlpha(0f);
        animate().alpha(1f).setDuration(140).start();
    }

    public void hide() {
        setVisibility(GONE);
        row.removeAllViews();
    }

    private void bind(List<Card> cards) {
        Context c = getContext();
        row.removeAllViews();
        empty.setVisibility(cards.isEmpty() ? VISIBLE : GONE);
        int cardW = Ui.dp(c, 240);
        int thumbH = Ui.dp(c, 150);
        for (Card card : cards) {
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackground(Ui.ripple(c, Ui.WINDOW_BG, 12));
            box.setElevation(Ui.dp(c, 8));
            box.setClipToOutline(true);

            LinearLayout header = new LinearLayout(c);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(Ui.dp(c, 10), 0, 0, 0);
            ImageView icon = new ImageView(c);
            icon.setImageDrawable(card.icon);
            header.addView(icon, new LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18)));
            TextView title = Ui.text(c, card.title, 13, Ui.TEXT);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setPadding(Ui.dp(c, 8), 0, 0, 0);
            header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            header.addView(Ui.iconButton(c, R.drawable.ic_close, 36, 9,
                            c.getString(R.string.win_close), v -> callbacks.onRecentClosed(card.token)),
                    new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)));
            box.addView(header);

            ImageView thumb = new ImageView(c);
            Bitmap shot = snapshot(card.snapshotSource, cardW, thumbH);
            if (shot != null) {
                thumb.setImageBitmap(shot);
                thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
            } else {
                thumb.setImageDrawable(card.icon);
                thumb.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                int p = Ui.dp(c, 40);
                thumb.setPadding(p, p, p, p);
            }
            thumb.setBackgroundColor(Ui.SURFACE);
            box.addView(thumb, new LinearLayout.LayoutParams(cardW, thumbH));

            box.setOnClickListener(v -> callbacks.onRecentSelected(card.token));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(Ui.dp(c, 10), 0, Ui.dp(c, 10), 0);
            row.addView(box, lp);
        }
    }

    private static Bitmap snapshot(View v, int maxW, int maxH) {
        if (v == null || v.getWidth() <= 0 || v.getHeight() <= 0) return null;
        float scale = Math.min(maxW / (float) v.getWidth(), maxH / (float) v.getHeight());
        int w = Math.max(1, Math.round(v.getWidth() * scale));
        int h = Math.max(1, Math.round(v.getHeight() * scale));
        try {
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            canvas.scale(scale, scale);
            v.draw(canvas);
            return bmp;
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP) hide();
        return true;
    }
}
