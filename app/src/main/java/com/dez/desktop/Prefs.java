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
    private static final String KEY_TASKBAR_OPACITY = "taskbar_opacity";
    private static final String KEY_FOLDERS = "folders";
    private static final String KEY_FOLDER_PREFIX = "folder.";
    private static final String KEY_DESKTOP_ICON_SIZE = "desktop_icon_size";
    private static final String KEY_DESKTOP_ICON_SPACING = "desktop_icon_spacing";
    private static final String KEY_TASKBAR_ICON_SIZE = "taskbar_icon_size";
    private static final String KEY_TASKBAR_ICON_SPACING = "taskbar_icon_spacing";

    // Icon size / spacing ranges and defaults, in dp.
    public static final int DESKTOP_ICON_MIN = 32, DESKTOP_ICON_MAX = 72, DESKTOP_ICON_DEFAULT = 46;
    public static final int DESKTOP_SPACING_MIN = 0, DESKTOP_SPACING_MAX = 40, DESKTOP_SPACING_DEFAULT = 6;
    public static final int TASKBAR_ICON_MIN = 20, TASKBAR_ICON_MAX = 44, TASKBAR_ICON_DEFAULT = 30;
    public static final int TASKBAR_SPACING_MIN = 0, TASKBAR_SPACING_MAX = 24, TASKBAR_SPACING_DEFAULT = 2;

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

    /** Taskbar background opacity in percent: 0 = fully transparent, 100 = solid. */
    public int getTaskbarOpacity() {
        return Math.max(0, Math.min(100, sp.getInt(KEY_TASKBAR_OPACITY, 92)));
    }

    public void setTaskbarOpacity(int percent) {
        sp.edit().putInt(KEY_TASKBAR_OPACITY, percent).apply();
    }

    public int getDesktopIconSize() {
        return clampInt(KEY_DESKTOP_ICON_SIZE, DESKTOP_ICON_DEFAULT, DESKTOP_ICON_MIN, DESKTOP_ICON_MAX);
    }

    public void setDesktopIconSize(int dp) {
        sp.edit().putInt(KEY_DESKTOP_ICON_SIZE, dp).apply();
    }

    public int getDesktopIconSpacing() {
        return clampInt(KEY_DESKTOP_ICON_SPACING, DESKTOP_SPACING_DEFAULT, DESKTOP_SPACING_MIN,
                DESKTOP_SPACING_MAX);
    }

    public void setDesktopIconSpacing(int dp) {
        sp.edit().putInt(KEY_DESKTOP_ICON_SPACING, dp).apply();
    }

    public int getTaskbarIconSize() {
        return clampInt(KEY_TASKBAR_ICON_SIZE, TASKBAR_ICON_DEFAULT, TASKBAR_ICON_MIN, TASKBAR_ICON_MAX);
    }

    public void setTaskbarIconSize(int dp) {
        sp.edit().putInt(KEY_TASKBAR_ICON_SIZE, dp).apply();
    }

    public int getTaskbarIconSpacing() {
        return clampInt(KEY_TASKBAR_ICON_SPACING, TASKBAR_SPACING_DEFAULT, TASKBAR_SPACING_MIN,
                TASKBAR_SPACING_MAX);
    }

    public void setTaskbarIconSpacing(int dp) {
        sp.edit().putInt(KEY_TASKBAR_ICON_SPACING, dp).apply();
    }

    /** App folders in the Apps panel, in creation order. An app is in at most one folder. */
    public List<String> getFolders() {
        return readList(KEY_FOLDERS, new ArrayList<>());
    }

    public List<String> getFolderApps(String folder) {
        return readList(KEY_FOLDER_PREFIX + folder, new ArrayList<>());
    }

    /** @return the folder holding {@code appKey}, or null. */
    public String folderOf(String appKey) {
        for (String f : getFolders()) {
            if (getFolderApps(f).contains(appKey)) return f;
        }
        return null;
    }

    /** Creates a folder with these apps; the name gets a number if it is taken. @return its name. */
    public String createFolder(String name, List<String> appKeys) {
        String base = name.replace('\n', ' ').trim();
        String n = base;
        for (int i = 2; getFolders().contains(n); i++) n = base + " " + i;
        for (String key : appKeys) removeFromAnyFolder(key);
        writeToggle(KEY_FOLDERS, getFolders(), n, true);
        sp.edit().putString(KEY_FOLDER_PREFIX + n, String.join("\n", appKeys)).apply();
        return n;
    }

    public void addToFolder(String folder, String appKey) {
        removeFromAnyFolder(appKey);
        writeToggle(KEY_FOLDER_PREFIX + folder, getFolderApps(folder), appKey, true);
    }

    /** Takes an app out of its folder; a folder left with one app or none is removed. */
    public void removeFromFolder(String folder, String appKey) {
        List<String> apps = getFolderApps(folder);
        apps.remove(appKey);
        if (apps.size() <= 1) {
            deleteFolder(folder);
        } else {
            sp.edit().putString(KEY_FOLDER_PREFIX + folder, String.join("\n", apps)).apply();
        }
    }

    private void removeFromAnyFolder(String appKey) {
        String f = folderOf(appKey);
        if (f != null) removeFromFolder(f, appKey);
    }

    public void deleteFolder(String folder) {
        writeToggle(KEY_FOLDERS, getFolders(), folder, false);
        sp.edit().remove(KEY_FOLDER_PREFIX + folder).apply();
    }

    /** @return the folder's name afterwards (unchanged if the new name is empty or taken). */
    public String renameFolder(String from, String to) {
        String n = to.replace('\n', ' ').trim();
        List<String> folders = getFolders();
        if (n.isEmpty() || n.equals(from) || folders.contains(n) || !folders.contains(from)) return from;
        folders.set(folders.indexOf(from), n);
        List<String> apps = getFolderApps(from);
        sp.edit().putString(KEY_FOLDERS, String.join("\n", folders))
                .putString(KEY_FOLDER_PREFIX + n, String.join("\n", apps))
                .remove(KEY_FOLDER_PREFIX + from).apply();
        return n;
    }

    private int clampInt(String key, int def, int min, int max) {
        return Math.max(min, Math.min(max, sp.getInt(key, def)));
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
