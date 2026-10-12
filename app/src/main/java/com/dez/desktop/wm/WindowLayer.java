package com.dez.desktop.wm;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;

import com.dez.desktop.AppInfo;
import com.dez.desktop.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * The window manager of the DeZ desktop. Keeps z-order and focus, cascades new windows, handles
 * minimise / maximise / snap-to-half (with a live snap preview while dragging) and keeps windows
 * on screen when the desktop is resized (rotation, external display, split screen).
 */
public class WindowLayer extends FrameLayout implements DesktopWindow.Host {
    public interface Listener {
        void onWindowsChanged();
    }

    private static final int PREVIEW_NONE = 0;
    private static final int PREVIEW_LEFT = 1;
    private static final int PREVIEW_RIGHT = 2;
    private static final int PREVIEW_MAX = 3;

    private final List<DesktopWindow> windows = new ArrayList<>();
    private final View snapPreview;
    private DesktopWindow focused;
    private Listener listener;
    private int cascade;
    private int pendingSnap = PREVIEW_NONE;
    private final int[] screenPos = new int[2];

    public WindowLayer(Context c) {
        super(c);
        setClipChildren(true);
        snapPreview = new View(c);
        GradientDrawable d = Ui.rounded(0x403D8BFF, Ui.dp(c, 10));
        d.setStroke(Ui.dp(c, 2), 0xAA3D8BFF);
        snapPreview.setBackground(d);
        snapPreview.setVisibility(GONE);
        addView(snapPreview, new LayoutParams(0, 0));
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public List<DesktopWindow> getWindows() {
        return new ArrayList<>(windows);
    }

    public DesktopWindow getFocused() {
        return focused;
    }

    public List<DesktopWindow> windowsFor(String appKey) {
        List<DesktopWindow> out = new ArrayList<>();
        for (DesktopWindow w : windows) {
            if (w.getApp().key.equals(appKey)) out.add(w);
        }
        return out;
    }

    /** True when the desktop is too small for overlapping windows (phone in portrait). */
    public boolean isCompact() {
        return Ui.pxToDp(getContext(), getWidth()) < 600 || Ui.pxToDp(getContext(), getHeight()) < 340;
    }

    public DesktopWindow open(AppInfo app, View content) {
        DesktopWindow w = new DesktopWindow(getContext(), app, this);
        w.setContent(content);
        int W = getWidth(), H = getHeight();
        int width = Math.min(Math.round(W * 0.66f), Ui.dp(getContext(), 960));
        int height = Math.min(Math.round(H * 0.78f), Ui.dp(getContext(), 680));
        int step = Ui.dp(getContext(), 28);
        int left = Math.max(0, (W - width) / 2 - step * 2 + (cascade % 5) * step);
        int top = Math.max(0, (H - height) / 2 - step * 2 + (cascade % 5) * step);
        cascade++;
        w.normalBounds.set(left, top, left + width, top + height);
        addView(w);
        if (isCompact() || W == 0) {
            w.maximized = true;
            w.applyBounds(0, 0, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        } else {
            w.applyBounds(w.normalBounds);
        }
        w.updateMaximizeButton();
        windows.add(w);
        w.setAlpha(0f);
        w.setScaleX(0.94f);
        w.setScaleY(0.94f);
        w.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).start();
        activateWindow(w);
        return w;
    }

    @Override
    public void activateWindow(DesktopWindow w) {
        if (!windows.contains(w)) return; // already closing
        boolean changed = focused != w || w.minimized;
        if (w.minimized) {
            w.minimized = false;
            w.setVisibility(VISIBLE);
        }
        if (indexOfChild(w) != getChildCount() - 1) w.bringToFront();
        focused = w;
        for (DesktopWindow other : windows) other.setActive(other == w);
        if (changed) notifyChanged();
    }

    @Override
    public void closeWindow(DesktopWindow w) {
        if (!windows.remove(w)) return;
        if (w.getContent() instanceof WindowContent) {
            ((WindowContent) w.getContent()).onWindowClosed();
        }
        w.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).setDuration(120)
                .withEndAction(() -> removeView(w)).start();
        if (focused == w) focusTopMost();
        notifyChanged();
    }

    @Override
    public void minimizeWindow(DesktopWindow w) {
        w.minimized = true;
        w.setVisibility(GONE);
        w.setActive(false);
        if (focused == w) focusTopMost();
        notifyChanged();
    }

    @Override
    public void toggleMaximize(DesktopWindow w) {
        if (w.maximized || w.snap != DesktopWindow.SNAP_NONE) {
            w.maximized = false;
            w.snap = DesktopWindow.SNAP_NONE;
            if (w.normalBounds.isEmpty() || isCompact()) {
                w.maximized = true;
                w.applyBounds(0, 0, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            } else {
                w.applyBounds(clampToLayer(w.normalBounds));
            }
        } else {
            maximize(w);
        }
        w.updateMaximizeButton();
    }

    private void maximize(DesktopWindow w) {
        w.maximized = true;
        w.snap = DesktopWindow.SNAP_NONE;
        w.applyBounds(0, 0, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        w.updateMaximizeButton();
    }

    public void snap(DesktopWindow w, int side) {
        w.maximized = false;
        w.snap = side;
        int half = getWidth() / 2;
        if (side == DesktopWindow.SNAP_LEFT) w.applyBounds(0, 0, half, getHeight());
        else w.applyBounds(half, 0, getWidth() - half, getHeight());
        w.updateMaximizeButton();
    }

    /** Minimise everything; if everything is already minimised, bring it all back (DeX "show desktop"). */
    public void toggleShowDesktop() {
        boolean anyVisible = false;
        for (DesktopWindow w : windows) anyVisible |= !w.minimized;
        if (anyVisible) {
            showDesktop();
        } else {
            for (DesktopWindow w : windows) {
                w.minimized = false;
                w.setVisibility(VISIBLE);
            }
            focusTopMost();
            notifyChanged();
        }
    }

    public void showDesktop() {
        for (DesktopWindow w : windows) {
            w.minimized = true;
            w.setVisibility(GONE);
            w.setActive(false);
        }
        focused = null;
        notifyChanged();
    }

    public void closeAll() {
        for (DesktopWindow w : new ArrayList<>(windows)) closeWindow(w);
    }

    /** Back: let the focused window's content handle it, otherwise close that window. */
    public boolean handleBack() {
        if (focused == null || focused.minimized) return false;
        View content = focused.getContent();
        if (content instanceof WindowContent && ((WindowContent) content).onBackPressed()) return true;
        closeWindow(focused);
        return true;
    }

    public void dispatchHostResume() {
        for (DesktopWindow w : windows) {
            if (w.getContent() instanceof WindowContent) ((WindowContent) w.getContent()).onHostResume();
        }
    }

    private void focusTopMost() {
        focused = null;
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View v = getChildAt(i);
            if (v instanceof DesktopWindow && windows.contains(v) && !((DesktopWindow) v).minimized) {
                focused = (DesktopWindow) v;
                break;
            }
        }
        for (DesktopWindow w : windows) w.setActive(w == focused);
    }

    private void notifyChanged() {
        if (listener != null) listener.onWindowsChanged();
    }

    // --- Drag-to-snap ------------------------------------------------------------------------

    @Override
    public void onTitleDrag(DesktopWindow w, float rawX, float rawY) {
        getLocationOnScreen(screenPos);
        float x = rawX - screenPos[0];
        float y = rawY - screenPos[1];
        int edge = Ui.dp(getContext(), 24);
        int target = PREVIEW_NONE;
        if (x <= edge) target = PREVIEW_LEFT;
        else if (x >= getWidth() - edge) target = PREVIEW_RIGHT;
        else if (y <= edge / 2f) target = PREVIEW_MAX;
        if (target == pendingSnap) return;
        pendingSnap = target;
        if (target == PREVIEW_NONE) {
            snapPreview.setVisibility(GONE);
            return;
        }
        int half = getWidth() / 2;
        int margin = Ui.dp(getContext(), 6);
        LayoutParams lp = (LayoutParams) snapPreview.getLayoutParams();
        lp.leftMargin = (target == PREVIEW_RIGHT ? half : 0) + margin;
        lp.topMargin = margin;
        lp.width = (target == PREVIEW_MAX ? getWidth() : half) - margin * 2;
        lp.height = getHeight() - margin * 2;
        snapPreview.setLayoutParams(lp);
        snapPreview.setVisibility(VISIBLE);
        snapPreview.bringToFront();
        w.bringToFront();
    }

    @Override
    public void onTitleDragEnd(DesktopWindow w, float rawX, float rawY) {
        snapPreview.setVisibility(GONE);
        int target = pendingSnap;
        pendingSnap = PREVIEW_NONE;
        if (target == PREVIEW_LEFT) snap(w, DesktopWindow.SNAP_LEFT);
        else if (target == PREVIEW_RIGHT) snap(w, DesktopWindow.SNAP_RIGHT);
        else if (target == PREVIEW_MAX) maximize(w);
        else w.applyBounds(clampToLayer(w.normalBounds));
    }

    /** Keeps at least the title bar reachable and shrinks windows larger than the desktop. */
    private Rect clampToLayer(Rect r) {
        int W = getWidth(), H = getHeight();
        if (W == 0 || H == 0) return new Rect(r);
        int w = Math.min(r.width(), W);
        int h = Math.min(r.height(), H);
        int keep = Ui.dp(getContext(), 64);
        int left = Math.max(keep - w, Math.min(r.left, W - keep));
        int top = Math.max(0, Math.min(r.top, H - Ui.dp(getContext(), 40)));
        return new Rect(left, top, left + w, top + h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (oldw == 0 || oldh == 0) return;
        post(() -> {
            for (DesktopWindow win : windows) {
                if (win.snap != DesktopWindow.SNAP_NONE) {
                    snap(win, win.snap);
                } else if (!win.maximized) {
                    if (isCompact()) {
                        maximize(win);
                    } else {
                        win.normalBounds.set(clampToLayer(win.normalBounds));
                        win.applyBounds(win.normalBounds);
                    }
                }
            }
        });
    }
}
