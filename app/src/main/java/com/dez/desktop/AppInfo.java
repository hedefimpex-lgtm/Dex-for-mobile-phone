package com.dez.desktop;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

import com.dez.desktop.apps.WindowApp;

/** Something that can be launched from DeZ: either a built-in windowed app or an installed Android app. */
public final class AppInfo {
    public final String key;
    public final CharSequence label;
    public final Drawable icon;
    /** Set for installed Android apps. */
    public final ComponentName component;
    public final UserHandle user;
    /** Set for DeZ's own windowed apps. */
    public final WindowApp builtIn;
    /**
     * Automatic group: an {@code ApplicationInfo.CATEGORY_*} value, {@link #CATEGORY_DEZ} for
     * DeZ's own apps or {@code ApplicationInfo.CATEGORY_UNDEFINED} (-1).
     */
    public final int category;

    public static final int CATEGORY_DEZ = -2;

    private AppInfo(String key, CharSequence label, Drawable icon, ComponentName component,
                    UserHandle user, WindowApp builtIn, int category) {
        this.key = key;
        this.label = label;
        this.icon = icon;
        this.component = component;
        this.user = user;
        this.builtIn = builtIn;
        this.category = category;
    }

    public static AppInfo builtIn(WindowApp app, CharSequence label, Drawable icon) {
        return new AppInfo(app.key(), label, icon, null, null, app, CATEGORY_DEZ);
    }

    public static AppInfo external(String key, CharSequence label, Drawable icon,
                                   ComponentName component, UserHandle user, int category) {
        return new AppInfo(key, label, icon, component, user, null, category);
    }

    public boolean isBuiltIn() {
        return builtIn != null;
    }
}
