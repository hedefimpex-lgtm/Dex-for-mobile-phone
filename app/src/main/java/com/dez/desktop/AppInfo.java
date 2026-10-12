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

    private AppInfo(String key, CharSequence label, Drawable icon, ComponentName component,
                    UserHandle user, WindowApp builtIn) {
        this.key = key;
        this.label = label;
        this.icon = icon;
        this.component = component;
        this.user = user;
        this.builtIn = builtIn;
    }

    public static AppInfo builtIn(WindowApp app, CharSequence label, Drawable icon) {
        return new AppInfo(app.key(), label, icon, null, null, app);
    }

    public static AppInfo external(String key, CharSequence label, Drawable icon,
                                   ComponentName component, UserHandle user) {
        return new AppInfo(key, label, icon, component, user, null);
    }

    public boolean isBuiltIn() {
        return builtIn != null;
    }
}
