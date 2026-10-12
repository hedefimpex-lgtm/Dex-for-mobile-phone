package com.dez.desktop.wm;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.dez.desktop.AppInfo;
import com.dez.desktop.Ui;

/**
 * A DeX-style app window: a {@link WindowFrame} (title bar + content) that is draggable by its title bar (snaps to screen halves or maximises at the edges), resizable from
 * its left, right and bottom edges and corners, double-tap on the title bar to maximise.
 */
@SuppressLint("ViewConstructor")
public class DesktopWindow extends FrameLayout {
    public interface Host {
        void activateWindow(DesktopWindow w);

        void closeWindow(DesktopWindow w);

        void minimizeWindow(DesktopWindow w);

        void toggleMaximize(DesktopWindow w);

        void onTitleDrag(DesktopWindow w, float rawX, float rawY);

        void onTitleDragEnd(DesktopWindow w, float rawX, float rawY);
    }

    public static final int SNAP_NONE = 0;
    public static final int SNAP_LEFT = 1;
    public static final int SNAP_RIGHT = 2;

    private static final int EDGE_LEFT = 1;
    private static final int EDGE_RIGHT = 2;
    private static final int EDGE_BOTTOM = 4;

    private final Host host;
    private final AppInfo app;
    private final WindowFrame frame;

    /** Bounds (in the window layer) used when the window is neither maximised nor snapped. */
    final Rect normalBounds = new Rect();
    boolean maximized;
    boolean minimized;
    int snap = SNAP_NONE;
    private boolean active;

    public DesktopWindow(Context c, AppInfo app, Host host) {
        super(c);
        this.app = app;
        this.host = host;

        frame = new WindowFrame(c);
        frame.setIcon(app.icon);
        frame.setTitle(app.label);
        frame.setListener(new WindowFrame.Listener() {
            @Override
            public void onMinimize() {
                host.minimizeWindow(DesktopWindow.this);
            }

            @Override
            public void onToggleMaximize() {
                host.toggleMaximize(DesktopWindow.this);
            }

            @Override
            public void onClose() {
                host.closeWindow(DesktopWindow.this);
            }
        });
        frame.getTitleBar().setOnTouchListener(new TitleDragListener());
        addView(frame, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        addResizeHandles(c);
        setActive(false);
    }

    /** Finds the window hosting {@code v}, or null. */
    public static DesktopWindow of(View v) {
        while (v != null) {
            if (v instanceof DesktopWindow) return (DesktopWindow) v;
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return null;
    }

    public AppInfo getApp() {
        return app;
    }

    public View getContent() {
        return frame.getContent();
    }

    public void setContent(View v) {
        frame.setContent(v);
    }

    public CharSequence getTitle() {
        return frame.getTitle();
    }

    public void setTitle(CharSequence title) {
        frame.setTitle(title);
    }

    public boolean isMinimized() {
        return minimized;
    }

    public boolean isMaximized() {
        return maximized;
    }

    public boolean isActive() {
        return active;
    }

    void setActive(boolean active) {
        this.active = active;
        frame.setActive(active);
        setElevation(Ui.dp(getContext(), active ? 18 : 6));
    }

    void updateMaximizeButton() {
        frame.setMaximized(maximized || snap != SNAP_NONE);
    }

    /** Places the window at {@code r} (layer coordinates); width/height < 0 means match parent. */
    void applyBounds(int left, int top, int width, int height) {
        ViewGroup.LayoutParams raw = getLayoutParams();
        LayoutParams lp = raw instanceof LayoutParams ? (LayoutParams) raw
                : new LayoutParams(width, height);
        lp.width = width;
        lp.height = height;
        lp.leftMargin = left;
        lp.topMargin = top;
        lp.gravity = Gravity.TOP | Gravity.START;
        setLayoutParams(lp);
    }

    void applyBounds(Rect r) {
        applyBounds(r.left, r.top, r.width(), r.height());
    }

    /** Current bounds in layer coordinates, as laid out (falls back to layout params). */
    Rect currentBounds() {
        LayoutParams lp = (LayoutParams) getLayoutParams();
        int w = getWidth() > 0 ? getWidth() : lp.width;
        int h = getHeight() > 0 ? getHeight() : lp.height;
        int l = maximized ? 0 : lp.leftMargin;
        int t = maximized ? 0 : lp.topMargin;
        return new Rect(l, t, l + w, t + h);
    }

    /** Leaves maximised / snapped state, keeping the current on-screen bounds as normal bounds. */
    private void detachFromLayout() {
        if (!maximized && snap == SNAP_NONE) return;
        Rect r = currentBounds();
        maximized = false;
        snap = SNAP_NONE;
        normalBounds.set(r);
        applyBounds(r);
        updateMaximizeButton();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) host.activateWindow(this);
        return false;
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent ev) {
        // Swallow touches so they never fall through to windows or desktop icons underneath.
        return true;
    }

    private int minWidth() {
        return Ui.dp(getContext(), 220);
    }

    private int minHeight() {
        return Ui.dp(getContext(), 160);
    }

    private class TitleDragListener implements OnTouchListener {
        private final GestureDetector doubleTap = new GestureDetector(getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        host.toggleMaximize(DesktopWindow.this);
                        return true;
                    }
                });
        private final int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        private float downX, downY;
        private int startLeft, startTop;
        private boolean dragging;

        @Override
        @SuppressLint("ClickableViewAccessibility")
        public boolean onTouch(View v, MotionEvent e) {
            doubleTap.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    host.activateWindow(DesktopWindow.this);
                    downX = e.getRawX();
                    downY = e.getRawY();
                    LayoutParams lp = (LayoutParams) getLayoutParams();
                    startLeft = lp.leftMargin;
                    startTop = lp.topMargin;
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - downX;
                    float dy = e.getRawY() - downY;
                    if (!dragging && Math.hypot(dx, dy) > slop) {
                        dragging = true;
                        if (maximized || snap != SNAP_NONE) {
                            restoreUnderFinger(e.getX());
                            LayoutParams p = (LayoutParams) getLayoutParams();
                            startLeft = p.leftMargin;
                            startTop = p.topMargin;
                        }
                    }
                    if (dragging) {
                        LayoutParams p = (LayoutParams) getLayoutParams();
                        p.leftMargin = startLeft + Math.round(dx);
                        p.topMargin = Math.max(0, startTop + Math.round(dy));
                        setLayoutParams(p);
                        host.onTitleDrag(DesktopWindow.this, e.getRawX(), e.getRawY());
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) {
                        LayoutParams p = (LayoutParams) getLayoutParams();
                        normalBounds.set(p.leftMargin, p.topMargin,
                                p.leftMargin + p.width, p.topMargin + p.height);
                        host.onTitleDragEnd(DesktopWindow.this, e.getRawX(), e.getRawY());
                    }
                    dragging = false;
                    return true;
                default:
                    return true;
            }
        }

        /** Like DeX: dragging a maximised window restores it, keeping the grab point under the finger. */
        private void restoreUnderFinger(float xInTitle) {
            Rect cur = currentBounds();
            int w = normalBounds.width() > 0 ? normalBounds.width() : cur.width() * 2 / 3;
            int h = normalBounds.height() > 0 ? normalBounds.height() : cur.height() * 2 / 3;
            float frac = cur.width() > 0 ? xInTitle / cur.width() : 0.5f;
            int left = cur.left + Math.round(xInTitle - frac * w);
            maximized = false;
            snap = SNAP_NONE;
            normalBounds.set(left, cur.top, left + w, cur.top + h);
            applyBounds(normalBounds);
            updateMaximizeButton();
        }
    }

    private void addResizeHandles(Context c) {
        int edge = Ui.dp(c, 8);
        int corner = Ui.dp(c, 22);
        addHandle(EDGE_LEFT, new LayoutParams(edge, LayoutParams.MATCH_PARENT, Gravity.START));
        addHandle(EDGE_RIGHT, new LayoutParams(edge, LayoutParams.MATCH_PARENT, Gravity.END));
        addHandle(EDGE_BOTTOM, new LayoutParams(LayoutParams.MATCH_PARENT, edge, Gravity.BOTTOM));
        addHandle(EDGE_BOTTOM | EDGE_LEFT,
                new LayoutParams(corner, corner, Gravity.BOTTOM | Gravity.START));
        addHandle(EDGE_BOTTOM | EDGE_RIGHT,
                new LayoutParams(corner, corner, Gravity.BOTTOM | Gravity.END));
    }

    @SuppressLint("ClickableViewAccessibility")
    private void addHandle(int edges, LayoutParams lp) {
        View h = new View(getContext());
        h.setOnTouchListener(new OnTouchListener() {
            float downX, downY;
            Rect start;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        host.activateWindow(DesktopWindow.this);
                        detachFromLayout();
                        downX = e.getRawX();
                        downY = e.getRawY();
                        start = currentBounds();
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        int dx = Math.round(e.getRawX() - downX);
                        int dy = Math.round(e.getRawY() - downY);
                        Rect r = new Rect(start);
                        if ((edges & EDGE_RIGHT) != 0) r.right = Math.max(r.left + minWidth(), start.right + dx);
                        if ((edges & EDGE_LEFT) != 0) r.left = Math.min(r.right - minWidth(), start.left + dx);
                        if ((edges & EDGE_BOTTOM) != 0) r.bottom = Math.max(r.top + minHeight(), start.bottom + dy);
                        normalBounds.set(r);
                        applyBounds(r);
                        return true;
                    }
                    default:
                        return true;
                }
            }
        });
        addView(h, lp);
    }
}
