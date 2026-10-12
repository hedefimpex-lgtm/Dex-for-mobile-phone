package com.dez.desktop;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.dez.desktop.shell.DesktopGrid;
import com.dez.desktop.shell.RecentsOverlay;
import com.dez.desktop.shell.StartMenu;
import com.dez.desktop.shell.Taskbar;
import com.dez.desktop.wm.DesktopWindow;
import com.dez.desktop.wm.WindowLayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The DeZ desktop: wallpaper with desktop icons, a window layer for DeZ's own windowed apps,
 * a DeX-style taskbar, an app drawer and a recent-apps overview. Installed Android apps are
 * opened as freeform windows when the device supports it, otherwise full screen.
 */
public class DesktopActivity extends Activity implements Taskbar.Callbacks, StartMenu.Callbacks,
        DesktopGrid.Callbacks, RecentsOverlay.Callbacks, WindowLayer.Listener {

    public static final int LAUNCH_DEFAULT = 0;
    public static final int LAUNCH_WINDOW = 1;
    public static final int LAUNCH_FULLSCREEN = 2;
    public static final int LAUNCH_NEW_INSTANCE = 3;

    private Prefs prefs;
    private AppRepository repository;
    private final Map<String, AppInfo> appsByKey = new LinkedHashMap<>();
    private final List<AppInfo> allApps = new ArrayList<>();
    /** Installed apps DeZ started; shown in the taskbar until removed (DeZ cannot see other tasks). */
    private final Set<String> externalRunning = new LinkedHashSet<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private FrameLayout workspace;
    private DesktopGrid desktopGrid;
    private WindowLayer windowLayer;
    private StartMenu startMenu;
    private RecentsOverlay recents;
    private Taskbar taskbar;
    private View popupAnchor;
    private LinearLayout displayBanner;
    private boolean bannerDismissed;
    private int externalCascade;
    private boolean metaAlone;

    private DisplayManager displayManager;
    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
            bannerDismissed = false;
            updateDisplayBanner();
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            updateDisplayBanner();
        }

        @Override
        public void onDisplayChanged(int displayId) {
        }
    };

    private final LauncherApps.Callback packageCallback = new LauncherApps.Callback() {
        @Override
        public void onPackageRemoved(String packageName, UserHandle user) {
            reloadApps();
        }

        @Override
        public void onPackageAdded(String packageName, UserHandle user) {
            reloadApps();
        }

        @Override
        public void onPackageChanged(String packageName, UserHandle user) {
            reloadApps();
        }

        @Override
        public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
            reloadApps();
        }

        @Override
        public void onPackagesUnavailable(String[] packageNames, UserHandle user, boolean replacing) {
            reloadApps();
        }
    };

    // ---------------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        repository = new AppRepository(this);
        setupWindow();
        buildUi();

        setApps(repository.builtIns());
        reloadApps();
        getSystemService(LauncherApps.class).registerCallback(packageCallback, main);

        displayManager = getSystemService(DisplayManager.class);
        displayManager.registerDisplayListener(displayListener, main);
        applyPrefs();
    }

    @Override
    protected void onStart() {
        super.onStart();
        taskbar.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        windowLayer.dispatchHostResume();
        updateDisplayBanner();
    }

    @Override
    protected void onStop() {
        taskbar.stop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        getSystemService(LauncherApps.class).unregisterCallback(packageCallback);
        displayManager.unregisterDisplayListener(displayListener);
        windowLayer.setListener(null);
        windowLayer.closeAll();
        repository.shutdown();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while DeZ is already in front: close overlays, then show the desktop.
        if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (startMenu.isShowing() || recents.isShowing()) {
                startMenu.hide();
                recents.hide();
            } else {
                windowLayer.showDesktop();
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (startMenu.isShowing()) {
            startMenu.hide();
        } else if (recents.isShowing()) {
            recents.hide();
        } else if (!windowLayer.handleBack() && !isDefaultHome()) {
            moveTaskToBack(true);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Window & UI setup
    // ---------------------------------------------------------------------------------------------

    private void setupWindow() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            w.setDecorFitsSystemWindows(false);
        }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @SuppressWarnings("deprecation")
    private void buildUi() {
        root = new FrameLayout(this);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                android.graphics.Insets cut = insets.getInsets(WindowInsets.Type.displayCutout());
                v.setPadding(cut.left, cut.top, cut.right, Math.max(ime.bottom, cut.bottom));
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), 0,
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        workspace = new FrameLayout(this);
        column.addView(workspace, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        desktopGrid = new DesktopGrid(this, this);
        workspace.addView(desktopGrid, matchParent());

        windowLayer = new WindowLayer(this);
        windowLayer.setListener(this);
        workspace.addView(windowLayer, matchParent());

        popupAnchor = new View(this);
        workspace.addView(popupAnchor, new FrameLayout.LayoutParams(1, 1));

        displayBanner = buildDisplayBanner();
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        blp.topMargin = Ui.dp(this, 12);
        workspace.addView(displayBanner, blp);

        recents = new RecentsOverlay(this, this);
        workspace.addView(recents, matchParent());

        startMenu = new StartMenu(this, this);
        workspace.addView(startMenu, matchParent());

        taskbar = new Taskbar(this, this);
        column.addView(taskbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, taskbar.preferredHeight()));

        setContentView(root);
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
    }

    /** Applies wallpaper and orientation preferences. */
    public void applyTaskbarOpacity() {
        taskbar.setOpacity(prefs.getTaskbarOpacity());
    }

    /** Re-applies icon size / spacing settings to the desktop and taskbar. */
    public void applyIconMetrics() {
        desktopGrid.setIconMetrics(prefs.getDesktopIconSize(), prefs.getDesktopIconSpacing());
        taskbar.setIconMetrics(prefs.getTaskbarIconSize(), prefs.getTaskbarIconSpacing());
        refreshDesktop();
        refreshTaskbar();
    }

    public void applyPrefs() {
        applyTaskbarOpacity();
        applyIconMetrics();
        int wp = prefs.getWallpaper();
        if (wp == Prefs.WALLPAPER_SYSTEM) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            root.setBackground(null);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            root.setBackground(Wallpapers.create(wp));
        }
        boolean lock = prefs.isLandscapeLocked() && isOnDefaultDisplay();
        setRequestedOrientation(lock ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    public Prefs getPrefs() {
        return prefs;
    }

    // ---------------------------------------------------------------------------------------------
    // Apps
    // ---------------------------------------------------------------------------------------------

    private void reloadApps() {
        repository.load(this::setApps);
    }

    private void setApps(List<AppInfo> apps) {
        allApps.clear();
        allApps.addAll(apps);
        appsByKey.clear();
        for (AppInfo a : apps) appsByKey.put(a.key, a);
        boolean complete = false;
        for (AppInfo a : apps) complete |= !a.isBuiltIn();
        if (complete) externalRunning.retainAll(appsByKey.keySet()); // drop uninstalled apps
        startMenu.setApps(allApps);
        refreshDesktop();
        refreshTaskbar();
    }

    private void refreshDesktop() {
        List<AppInfo> icons = new ArrayList<>();
        for (String key : prefs.getDesktop()) {
            AppInfo a = appsByKey.get(key);
            if (a != null) icons.add(a);
        }
        desktopGrid.setApps(icons);
    }

    private void refreshTaskbar() {
        Map<String, Taskbar.Item> items = new LinkedHashMap<>();
        DesktopWindow focused = windowLayer.getFocused();
        String focusedKey = focused != null && !focused.isMinimized() ? focused.getApp().key : null;
        List<String> keys = new ArrayList<>(prefs.getPinned());
        for (DesktopWindow w : windowLayer.getWindows()) keys.add(w.getApp().key);
        keys.addAll(externalRunning);
        for (String key : keys) {
            if (items.containsKey(key)) continue;
            AppInfo app = appsByKey.get(key);
            if (app == null) continue;
            boolean running = !windowLayer.windowsFor(key).isEmpty() || externalRunning.contains(key);
            items.put(key, new Taskbar.Item(key, app.label, app.icon, running, key.equals(focusedKey)));
        }
        taskbar.setItems(new ArrayList<>(items.values()));
    }

    @Override
    public void onWindowsChanged() {
        refreshTaskbar();
    }

    /** Opens a built-in app by id (used by apps, e.g. "My Files" opening a text file in Notes). */
    public void openBuiltIn(String id, Bundle args) {
        AppInfo app = appsByKey.get("dez:" + id);
        if (app != null) openBuiltIn(app, args, args != null);
    }

    private void openBuiltIn(AppInfo app, Bundle args, boolean forceNew) {
        List<DesktopWindow> existing = windowLayer.windowsFor(app.key);
        if (!existing.isEmpty() && (app.builtIn.isSingleInstance() || !forceNew)) {
            windowLayer.activateWindow(existing.get(existing.size() - 1));
            return;
        }
        windowLayer.open(app, app.builtIn.createView(this, args));
    }

    public void launchApp(AppInfo app, int mode) {
        launchApp(app, mode, PLACE_CASCADE);
    }

    /**
     * @param placement where an installed app's pop-up window goes on the DeZ desktop
     *                  ({@link #PLACE_CASCADE}, {@link #PLACE_LEFT}, {@link #PLACE_RIGHT}, {@link #PLACE_FILL}).
     */
    public void launchApp(AppInfo app, int mode, int placement) {
        startMenu.hide();
        recents.hide();
        if (app.isBuiltIn()) {
            openBuiltIn(app, null, mode == LAUNCH_NEW_INSTANCE);
            return;
        }
        boolean freeform = ExternalLauncher.isFreeformSupported(this);
        boolean wantWindow = mode == LAUNCH_WINDOW
                || ((mode == LAUNCH_DEFAULT || mode == LAUNCH_NEW_INSTANCE) && prefs.isFreeformEnabled());
        Rect bounds = null;
        if (wantWindow && freeform) {
            bounds = externalBounds(placement);
        } else if (wantWindow && (mode == LAUNCH_WINDOW || !prefs.isFreeformHintDismissed())) {
            showFreeformHelp();
        }
        if (ExternalLauncher.launch(this, app, bounds)) {
            externalRunning.remove(app.key);
            externalRunning.add(app.key);
            refreshTaskbar();
        }
    }

    public static final int PLACE_CASCADE = 0;
    public static final int PLACE_LEFT = 1;
    public static final int PLACE_RIGHT = 2;
    public static final int PLACE_FILL = 3;

    /**
     * Bounds, in screen coordinates, for an installed app's pop-up window: inside the DeZ desktop
     * area (never under the taskbar), sized by the "Window size" setting.
     */
    private Rect externalBounds(int placement) {
        int[] loc = new int[2];
        workspace.getLocationOnScreen(loc);
        int W = workspace.getWidth(), H = workspace.getHeight();
        switch (placement) {
            case PLACE_LEFT:
                return new Rect(loc[0], loc[1], loc[0] + W / 2, loc[1] + H);
            case PLACE_RIGHT:
                return new Rect(loc[0] + W / 2, loc[1], loc[0] + W, loc[1] + H);
            case PLACE_FILL:
                return new Rect(loc[0], loc[1], loc[0] + W, loc[1] + H);
            default:
                break;
        }
        // {width, height} fractions and dp caps for small / medium / large.
        float[][] desk = {{0.45f, 0.55f}, {0.6f, 0.75f}, {0.8f, 0.88f}};
        float[][] phone = {{0.8f, 0.7f}, {0.92f, 0.86f}, {1f, 1f}};
        int[][] caps = {{640, 480}, {900, 700}, {1280, 900}};
        int size = prefs.getWindowSize();
        boolean compact = windowLayer.isCompact();
        float[] f = (compact ? phone : desk)[size];
        int w = Math.round(W * f[0]);
        int h = Math.round(H * f[1]);
        if (!compact) {
            w = Math.min(w, Ui.dp(this, caps[size][0]));
            h = Math.min(h, Ui.dp(this, caps[size][1]));
        }
        int step = Ui.dp(this, 32);
        int offset = (externalCascade++ % 5) * step;
        int left = loc[0] + Math.max(0, Math.min(W - w, (W - w) / 2 - step * 2 + offset));
        int top = loc[1] + Math.max(0, Math.min(H - h, (H - h) / 2 - step * 2 + offset));
        return new Rect(left, top, left + w, top + h);
    }

    /** Explains once how to turn on pop-up windows; apps open full screen meanwhile. */
    private void showFreeformHelp() {
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.freeform_help_title)
                .setMessage(getString(R.string.settings_freeform_off) + "\n\n"
                        + getString(R.string.settings_freeform_cmd))
                .setPositiveButton(R.string.settings_dev_options, (d, w) -> openDeveloperOptions())
                .setNeutralButton(R.string.freeform_help_dismiss,
                        (d, w) -> prefs.setFreeformHintDismissed(true))
                .setNegativeButton(R.string.dialog_ok, null)
                .show();
    }

    public void openDeveloperOptions() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Context menus
    // ---------------------------------------------------------------------------------------------

    private static final int M_OPEN = 1, M_WINDOW = 2, M_FULLSCREEN = 3, M_NEW = 4, M_PIN = 5,
            M_DESKTOP = 6, M_INFO = 7, M_CLOSE_ALL = 8, M_FORGET = 9, M_LEFT = 10, M_RIGHT = 11,
            M_FILL = 12;

    /** "Open in pop-up window" plus, when pop-up windows work, left / right half and fill. */
    private void addWindowItems(Menu m, int order) {
        m.add(0, M_WINDOW, order, R.string.menu_open_window);
        if (ExternalLauncher.isFreeformSupported(this)) {
            m.add(0, M_LEFT, order, R.string.menu_open_left);
            m.add(0, M_RIGHT, order, R.string.menu_open_right);
            m.add(0, M_FILL, order, R.string.menu_open_fill);
        }
        m.add(0, M_FULLSCREEN, order, R.string.menu_open_fullscreen);
    }

    private void showAppMenu(AppInfo app, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu m = popup.getMenu();
        m.add(0, M_OPEN, 0, R.string.menu_open);
        if (app.isBuiltIn()) {
            if (!app.builtIn.isSingleInstance()) m.add(0, M_NEW, 1, R.string.menu_new_window);
        } else {
            addWindowItems(m, 1);
        }
        m.add(0, M_PIN, 3, prefs.isPinned(app.key) ? R.string.menu_unpin : R.string.menu_pin);
        m.add(0, M_DESKTOP, 4, prefs.isOnDesktop(app.key)
                ? R.string.menu_remove_desktop : R.string.menu_add_desktop);
        if (!app.isBuiltIn()) m.add(0, M_INFO, 5, R.string.menu_app_info);
        popup.setOnMenuItemClickListener(item -> handleAppMenu(app, item.getItemId()));
        popup.show();
    }

    private boolean handleAppMenu(AppInfo app, int id) {
        switch (id) {
            case M_OPEN:
                launchApp(app, LAUNCH_DEFAULT);
                return true;
            case M_WINDOW:
                launchApp(app, LAUNCH_WINDOW);
                return true;
            case M_FULLSCREEN:
                launchApp(app, LAUNCH_FULLSCREEN);
                return true;
            case M_LEFT:
                launchApp(app, LAUNCH_WINDOW, PLACE_LEFT);
                return true;
            case M_RIGHT:
                launchApp(app, LAUNCH_WINDOW, PLACE_RIGHT);
                return true;
            case M_FILL:
                launchApp(app, LAUNCH_WINDOW, PLACE_FILL);
                return true;
            case M_NEW:
                launchApp(app, LAUNCH_NEW_INSTANCE);
                return true;
            case M_PIN:
                prefs.setPinned(app.key, !prefs.isPinned(app.key));
                refreshTaskbar();
                return true;
            case M_DESKTOP:
                prefs.setOnDesktop(app.key, !prefs.isOnDesktop(app.key));
                refreshDesktop();
                return true;
            case M_INFO:
                ExternalLauncher.showAppInfo(this, app);
                return true;
            case M_CLOSE_ALL:
                for (DesktopWindow w : windowLayer.windowsFor(app.key)) windowLayer.closeWindow(w);
                return true;
            case M_FORGET:
                externalRunning.remove(app.key);
                refreshTaskbar();
                return true;
            default:
                return false;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Taskbar callbacks
    // ---------------------------------------------------------------------------------------------

    @Override
    public void onAppsButton() {
        recents.hide();
        startMenu.toggle();
    }

    @Override
    public void onBackButton() {
        onBackPressed();
    }

    @Override
    public void onHomeButton() {
        startMenu.hide();
        recents.hide();
        windowLayer.toggleShowDesktop();
    }

    @Override
    public void onRecentsButton() {
        startMenu.hide();
        if (recents.isShowing()) recents.hide();
        else showRecents();
    }

    private void showRecents() {
        List<RecentsOverlay.Card> cards = new ArrayList<>();
        List<DesktopWindow> windows = windowLayer.getWindows();
        for (int i = windows.size() - 1; i >= 0; i--) {
            DesktopWindow w = windows.get(i);
            cards.add(new RecentsOverlay.Card(w, w.getTitle(), w.getApp().icon, w));
        }
        List<String> ext = new ArrayList<>(externalRunning);
        for (int i = ext.size() - 1; i >= 0; i--) {
            AppInfo a = appsByKey.get(ext.get(i));
            if (a != null) cards.add(new RecentsOverlay.Card(a.key, a.label, a.icon, null));
        }
        recents.show(cards);
    }

    @Override
    public void onNotificationsButton() {
        try {
            Object sb = getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Not allowed on this device: reveal the system bars so the shade can be pulled down.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && getWindow().getInsetsController() != null) {
                getWindow().getInsetsController().show(WindowInsets.Type.statusBars());
            }
        }
    }

    @Override
    public void onTaskbarItemClick(String key, View anchor) {
        startMenu.hide();
        recents.hide();
        List<DesktopWindow> windows = windowLayer.windowsFor(key);
        if (windows.size() == 1) {
            DesktopWindow w = windows.get(0);
            if (w == windowLayer.getFocused() && !w.isMinimized()) windowLayer.minimizeWindow(w);
            else windowLayer.activateWindow(w);
        } else if (windows.size() > 1) {
            PopupMenu popup = new PopupMenu(this, anchor);
            for (int i = 0; i < windows.size(); i++) popup.getMenu().add(0, i, i, windows.get(i).getTitle());
            popup.setOnMenuItemClickListener(item -> {
                windowLayer.activateWindow(windows.get(item.getItemId()));
                return true;
            });
            popup.show();
        } else {
            AppInfo app = appsByKey.get(key);
            if (app != null) launchApp(app, LAUNCH_DEFAULT);
        }
    }

    @Override
    public void onTaskbarItemLongClick(String key, View anchor) {
        AppInfo app = appsByKey.get(key);
        if (app == null) return;
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu m = popup.getMenu();
        if (app.isBuiltIn()) {
            if (!app.builtIn.isSingleInstance()) m.add(0, M_NEW, 0, R.string.menu_new_window);
            else m.add(0, M_OPEN, 0, R.string.menu_open);
        } else {
            addWindowItems(m, 0);
        }
        m.add(0, M_PIN, 2, prefs.isPinned(key) ? R.string.menu_unpin : R.string.menu_pin);
        if (!windowLayer.windowsFor(key).isEmpty()) m.add(0, M_CLOSE_ALL, 3, R.string.menu_close_all);
        if (externalRunning.contains(key)) m.add(0, M_FORGET, 4, R.string.menu_remove_running);
        if (!app.isBuiltIn()) m.add(0, M_INFO, 5, R.string.menu_app_info);
        popup.setOnMenuItemClickListener(item -> handleAppMenu(app, item.getItemId()));
        popup.show();
    }

    // ---------------------------------------------------------------------------------------------
    // Start menu / desktop / recents callbacks
    // ---------------------------------------------------------------------------------------------

    @Override
    public void onAppClicked(AppInfo app) {
        launchApp(app, LAUNCH_DEFAULT);
    }

    @Override
    public void onAppLongClicked(AppInfo app, View anchor) {
        showAppMenu(app, anchor);
    }

    @Override
    public void onOpenSettings() {
        startMenu.hide();
        openBuiltIn(com.dez.desktop.apps.BuiltInApps.SETTINGS, null);
    }

    @Override
    public void onExitDesktop() {
        startMenu.hide();
        if (isDefaultHome()) openHomeSettings();
        else moveTaskToBack(true);
    }

    @Override
    public void onDesktopIconClick(AppInfo app) {
        launchApp(app, LAUNCH_DEFAULT);
    }

    @Override
    public void onDesktopIconLongClick(AppInfo app, View anchor) {
        showAppMenu(app, anchor);
    }

    @Override
    public void onDesktopLongPress(float x, float y) {
        popupAnchor.setX(x);
        popupAnchor.setY(y);
        PopupMenu popup = new PopupMenu(this, popupAnchor);
        Menu m = popup.getMenu();
        m.add(0, 1, 0, R.string.tb_apps);
        m.add(0, 2, 1, R.string.menu_wallpaper);
        m.add(0, 3, 2, R.string.menu_show_desktop);
        m.add(0, 4, 3, R.string.app_settings);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    startMenu.show();
                    break;
                case 2:
                    prefs.setWallpaper(prefs.getWallpaper() + 1 >= Wallpapers.count()
                            ? Prefs.WALLPAPER_SYSTEM : prefs.getWallpaper() + 1);
                    applyPrefs();
                    break;
                case 3:
                    windowLayer.showDesktop();
                    break;
                default:
                    onOpenSettings();
            }
            return true;
        });
        popup.show();
    }

    @Override
    public void onRecentSelected(Object token) {
        recents.hide();
        if (token instanceof DesktopWindow) {
            windowLayer.activateWindow((DesktopWindow) token);
        } else {
            AppInfo app = appsByKey.get((String) token);
            if (app != null) launchApp(app, LAUNCH_DEFAULT);
        }
    }

    @Override
    public void onRecentClosed(Object token) {
        if (token instanceof DesktopWindow) windowLayer.closeWindow((DesktopWindow) token);
        else externalRunning.remove((String) token);
        refreshTaskbar();
        showRecents();
    }

    @Override
    public void onRecentsCloseAll() {
        windowLayer.closeAll();
        externalRunning.clear();
        refreshTaskbar();
        recents.hide();
    }

    // ---------------------------------------------------------------------------------------------
    // Keyboard shortcuts (DeX-like, for Bluetooth / USB keyboards)
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean metaKey = code == KeyEvent.KEYCODE_META_LEFT || code == KeyEvent.KEYCODE_META_RIGHT;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (metaKey) {
                if (e.getRepeatCount() == 0) metaAlone = true;
                return true;
            }
            metaAlone = false;
            DesktopWindow focused = windowLayer.getFocused();
            if (e.isMetaPressed()) {
                switch (code) {
                    case KeyEvent.KEYCODE_D:
                        onHomeButton();
                        return true;
                    case KeyEvent.KEYCODE_E:
                        openBuiltIn(com.dez.desktop.apps.BuiltInApps.FILES, null);
                        return true;
                    case KeyEvent.KEYCODE_A:
                        onAppsButton();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if (focused != null) windowLayer.snap(focused, DesktopWindow.SNAP_LEFT);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if (focused != null) windowLayer.snap(focused, DesktopWindow.SNAP_RIGHT);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_UP:
                        if (focused != null && !focused.isMaximized()) windowLayer.toggleMaximize(focused);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if (focused != null) windowLayer.minimizeWindow(focused);
                        return true;
                    default:
                        break;
                }
            }
            if (e.isAltPressed() && code == KeyEvent.KEYCODE_F4 && focused != null) {
                windowLayer.closeWindow(focused);
                return true;
            }
            if (e.isAltPressed() && code == KeyEvent.KEYCODE_TAB) {
                cycleWindows();
                return true;
            }
            if (code == KeyEvent.KEYCODE_ESCAPE && (startMenu.isShowing() || recents.isShowing())) {
                startMenu.hide();
                recents.hide();
                return true;
            }
        } else if (e.getAction() == KeyEvent.ACTION_UP && metaKey) {
            if (metaAlone) onAppsButton();
            metaAlone = false;
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    /** Alt+Tab: bring the least recently focused window to the front. */
    private void cycleWindows() {
        List<DesktopWindow> windows = windowLayer.getWindows();
        if (windows.isEmpty()) return;
        DesktopWindow bottom = null;
        for (int i = 0; i < windowLayer.getChildCount(); i++) {
            View v = windowLayer.getChildAt(i);
            if (v instanceof DesktopWindow && windows.contains(v)) {
                bottom = (DesktopWindow) v;
                break;
            }
        }
        if (bottom != null) windowLayer.activateWindow(bottom);
    }

    // ---------------------------------------------------------------------------------------------
    // Home app & external displays
    // ---------------------------------------------------------------------------------------------

    public boolean isDefaultHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = getPackageManager().resolveActivity(home, 0);
        return ri != null && ri.activityInfo != null && getPackageName().equals(ri.activityInfo.packageName);
    }

    public void openHomeSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    @SuppressWarnings("deprecation")
    private int currentDisplayId() {
        return getWindowManager().getDefaultDisplay().getDisplayId();
    }

    private boolean isOnDefaultDisplay() {
        return currentDisplayId() == Display.DEFAULT_DISPLAY;
    }

    /** Presentation-capable displays other than the one DeZ is on (HDMI, USB-C, Miracast). */
    public Display[] getExternalDisplays() {
        List<Display> out = new ArrayList<>();
        for (Display d : displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
            if (d.getDisplayId() != currentDisplayId()) out.add(d);
        }
        return out.toArray(new Display[0]);
    }

    /** Starts a DeZ desktop on another display, like DeX starting on a connected monitor. */
    public void startOnDisplay(int displayId) {
        Intent i = new Intent(this, SecondaryDesktopActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        ActivityOptions o = ActivityOptions.makeBasic().setLaunchDisplayId(displayId);
        try {
            startActivity(i, o.toBundle());
            bannerDismissed = true;
            updateDisplayBanner();
        } catch (SecurityException | IllegalArgumentException | ActivityNotFoundException e) {
            Toast.makeText(this, getString(R.string.launch_failed, getString(R.string.app_name)),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout buildDisplayBanner() {
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setBackground(Ui.rounded(Ui.PANEL_BG, Ui.dp(this, 12)));
        b.setElevation(Ui.dp(this, 20));
        b.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6));
        b.setVisibility(View.GONE);
        TextView t = Ui.text(this, getString(R.string.external_display_found), 13, Ui.TEXT);
        t.setPadding(0, 0, Ui.dp(this, 10), 0);
        b.addView(t);
        b.addView(Ui.button(this, getString(R.string.external_display_start), v -> {
            Display[] displays = getExternalDisplays();
            if (displays.length > 0) startOnDisplay(displays[0].getDisplayId());
        }));
        b.addView(Ui.iconButton(this, R.drawable.ic_close, 36, 9, getString(R.string.win_close), v -> {
            bannerDismissed = true;
            updateDisplayBanner();
        }));
        return b;
    }

    private void updateDisplayBanner() {
        boolean show = !bannerDismissed && isOnDefaultDisplay() && getExternalDisplays().length > 0;
        displayBanner.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) displayBanner.bringToFront();
    }
}
