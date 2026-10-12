package com.dez.desktop;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.provider.Settings;
import android.widget.Toast;

/**
 * Starts installed Android apps. When the device allows freeform multi-window (Samsung DeX devices,
 * Android desktop mode, or the developer option "Enable freeform windows"), apps are opened as
 * resizable windows with launch bounds, which is how DeX itself places app windows.
 */
public final class ExternalLauncher {
    private ExternalLauncher() {}

    public static boolean isFreeformSupported(Context c) {
        if (new Prefs(c).isFreeformForced()) return true;
        if (c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)) {
            return true;
        }
        // Samsung phones have pop-up view built in but don't declare the freeform feature.
        if ("samsung".equalsIgnoreCase(android.os.Build.MANUFACTURER)) return true;
        try {
            return Settings.Global.getInt(c.getContentResolver(), "enable_freeform_support", 0) != 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * @param screenBounds window bounds in screen coordinates, or null for full screen.
     * @return true if the app was started.
     */
    @SuppressWarnings("deprecation")
    public static boolean launch(Activity activity, AppInfo app, Rect screenBounds) {
        LauncherApps la = activity.getSystemService(LauncherApps.class);
        ActivityOptions options = ActivityOptions.makeBasic();
        // Keep the app on the display DeZ is running on (phone screen or external monitor).
        options.setLaunchDisplayId(activity.getWindowManager().getDefaultDisplay().getDisplayId());
        if (screenBounds != null) {
            options.setLaunchBounds(screenBounds);
        }
        try {
            la.startMainActivity(app.component, app.user, screenBounds, options.toBundle());
            return true;
        } catch (SecurityException | ActivityNotFoundException | IllegalStateException e) {
            Toast.makeText(activity, activity.getString(R.string.launch_failed, app.label),
                    Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    public static void showAppInfo(Activity activity, AppInfo app) {
        try {
            activity.getSystemService(LauncherApps.class)
                    .startAppDetailsActivity(app.component, app.user, null, null);
        } catch (SecurityException | ActivityNotFoundException e) {
            Toast.makeText(activity, activity.getString(R.string.launch_failed, app.label),
                    Toast.LENGTH_SHORT).show();
        }
    }
}
