package com.dez.desktop.apps;

import android.os.Bundle;
import android.view.View;

import com.dez.desktop.DesktopActivity;

/** A DeZ built-in app whose UI lives inside a {@link com.dez.desktop.wm.DesktopWindow}. */
public abstract class WindowApp {
    public final String id;
    public final int labelRes;
    public final int iconRes;

    protected WindowApp(String id, int labelRes, int iconRes) {
        this.id = id;
        this.labelRes = labelRes;
        this.iconRes = iconRes;
    }

    public String key() {
        return "dez:" + id;
    }

    /** Single-instance apps focus their existing window instead of opening another. */
    public boolean isSingleInstance() {
        return false;
    }

    /** Creates the window content; may implement {@link com.dez.desktop.wm.WindowContent}. */
    public abstract View createView(DesktopActivity host, Bundle args);
}
