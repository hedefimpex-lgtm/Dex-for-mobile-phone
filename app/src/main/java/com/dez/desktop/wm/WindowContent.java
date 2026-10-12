package com.dez.desktop.wm;

/** Optional hooks for views hosted inside a {@link DesktopWindow}. */
public interface WindowContent {
    /** @return true if the content consumed the back press (e.g. browser history, parent folder). */
    default boolean onBackPressed() {
        return false;
    }

    default void onWindowClosed() {
    }

    /** DeZ came back to the foreground (e.g. after a permission screen). */
    default void onHostResume() {
    }
}
