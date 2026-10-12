package com.dez.desktop;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Persistent DeZ settings: pinned taskbar apps, desktop shortcuts, wallpaper and window behaviour. */
public final class Prefs {
    private static final String KEY_PINNED = "pinned";
    private static final String KEY_DESKTOP = "desktop";
    private static final String KEY_WALLPAPER = "wallpaper";
    private static final String KEY_LANDSCAPE = "landscape";
    private static final String KEY_FREEFORM = "freeform";
    private static final String KEY_DESKTOP_UA = "desktop_ua";
    private static final String KEY_WINDOW_SIZE = "window_size";
    private static final String KEY_FREEFORM_HINT = "freeform_hint_dismissed";
    private static final String KEY_FREEFORM_FORCED = "freeform_forced";

    public static final int SIZE_SMALL = 0;
    public static final int SIZE_MEDIUM = 1;
    public static final int SIZE_LARGE = 2;

    /** Wallpaper index meaning "show the system wallpaper behind the desktop". */
    public static final int WALLPAPER_SYSTEM = -1;

    private static final List<String> DEFAULT_PINNED = Arrays.asList(
            "dez:files", "dez:browser", "dez:notes");
    private static final List<String> DEFAULT_DESKTOP = Arrays.asList(
            "dez:files", "dez:browser", "dez:notes", "dez:calculator", "dez:settings", "dez:sample");

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences("dez", Context.MODE_PRIVATE);
    }

    public List<String> getPinned() {
        return readList(KEY_PINNED, DEFAULT_PINNED);
    }

    public boolean isPinned(String key) {
        return getPinned().contains(key);
    }

    public void setPinned(String key, boolean pinned) {
        writeToggle(KEY_PINNED, getPinned(), key, pinned);
    }

    public List<String> getDesktop() {
        return readList(KEY_DESKTOP, DEFAULT_DESKTOP);
    }

    public boolean isOnDesktop(String key) {
        return getDesktop().contains(key);
    }

    public void setOnDesktop(String key, boolean onDesktop) {
        writeToggle(KEY_DESKTOP, getDesktop(), key, onDesktop);
    }

    public int getWallpaper() {
        return sp.getInt(KEY_WALLPAPER, 0);
    }

    public void setWallpaper(int index) {
        sp.edit().putInt(KEY_WALLPAPER, index).apply();
    }

    public boolean isLandscapeLocked() {
        return sp.getBoolean(KEY_LANDSCAPE, false);
    }

    public void setLandscapeLocked(boolean v) {
        sp.edit().putBoolean(KEY_LANDSCAPE, v).apply();
    }

    public boolean isFreeformEnabled() {
        return sp.getBoolean(KEY_FREEFORM, true);
    }

    public void setFreeformEnabled(boolean v) {
        sp.edit().putBoolean(KEY_FREEFORM, v).apply();
    }

    public boolean isDesktopUserAgent() {
        return sp.getBoolean(KEY_DESKTOP_UA, true);
    }

    public void setDesktopUserAgent(boolean v) {
        sp.edit().putBoolean(KEY_DESKTOP_UA, v).apply();
    }

    /** Size of pop-up windows for installed Android apps: {@link #SIZE_SMALL} .. {@link #SIZE_LARGE}. */
    public int getWindowSize() {
        return Math.max(SIZE_SMALL, Math.min(SIZE_LARGE, sp.getInt(KEY_WINDOW_SIZE, SIZE_MEDIUM)));
    }

    public void setWindowSize(int size) {
        sp.edit().putInt(KEY_WINDOW_SIZE, size).apply();
    }

    /** Request pop-up windows even when DeZ cannot detect support for them. */
    public boolean isFreeformForced() {
        return sp.getBoolean(KEY_FREEFORM_FORCED, false);
    }

    public void setFreeformForced(boolean v) {
        sp.edit().putBoolean(KEY_FREEFORM_FORCED, v).apply();
    }

    public boolean isFreeformHintDismissed() {
        return sp.getBoolean(KEY_FREEFORM_HINT, false);
    }

    public void setFreeformHintDismissed(boolean v) {
        sp.edit().putBoolean(KEY_FREEFORM_HINT, v).apply();
    }

    private List<String> readList(String key, List<String> def) {
        String raw = sp.getString(key, null);
        if (raw == null) return new ArrayList<>(def);
        List<String> out = new ArrayList<>();
        for (String s : raw.split("\n")) {
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private void writeToggle(String prefKey, List<String> list, String key, boolean present) {
        list.remove(key);
        if (present) list.add(key);
        sp.edit().putString(prefKey, String.join("\n", list)).apply();
    }
}
