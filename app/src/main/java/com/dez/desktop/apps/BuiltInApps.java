package com.dez.desktop.apps;

import android.content.Context;

import com.dez.desktop.AppInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Registry of DeZ's own windowed apps. */
public final class BuiltInApps {
    public static final String FILES = "files";
    public static final String NOTES = "notes";
    public static final String CALCULATOR = "calculator";
    public static final String BROWSER = "browser";
    public static final String SETTINGS = "settings";

    private static final List<WindowApp> APPS = Arrays.asList(
            new FilesApp(), new NotesApp(), new CalculatorApp(), new BrowserApp(), new SettingsApp());

    private BuiltInApps() {}

    public static List<AppInfo> all(Context c) {
        List<AppInfo> out = new ArrayList<>();
        for (WindowApp app : APPS) {
            out.add(AppInfo.builtIn(app, c.getString(app.labelRes), c.getDrawable(app.iconRes)));
        }
        return out;
    }
}
