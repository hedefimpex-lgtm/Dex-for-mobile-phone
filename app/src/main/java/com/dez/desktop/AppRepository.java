package com.dez.desktop;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import com.dez.desktop.apps.BuiltInApps;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads DeZ's built-in apps plus every launchable app of every user profile (incl. work profile). */
public final class AppRepository {
    public interface Callback {
        void onAppsLoaded(List<AppInfo> apps);
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public AppRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Built-in apps only; cheap, safe to call on the main thread. */
    public List<AppInfo> builtIns() {
        return BuiltInApps.all(context);
    }

    public void load(Callback cb) {
        executor.execute(() -> {
            List<AppInfo> result = new ArrayList<>(builtIns());
            LauncherApps la = context.getSystemService(LauncherApps.class);
            UserManager um = context.getSystemService(UserManager.class);
            UserHandle me = Process.myUserHandle();
            for (UserHandle user : la.getProfiles()) {
                for (LauncherActivityInfo info : la.getActivityList(null, user)) {
                    if (info.getComponentName().getPackageName().equals(context.getPackageName())) {
                        continue;
                    }
                    String key = info.getComponentName().flattenToShortString();
                    if (!user.equals(me)) key += "@" + um.getSerialNumberForUser(user);
                    result.add(AppInfo.external(key, info.getLabel(), info.getBadgedIcon(0),
                            info.getComponentName(), user, info.getApplicationInfo().category));
                }
            }
            Collator collator = Collator.getInstance();
            Collections.sort(result, (a, b) -> collator.compare(
                    String.valueOf(a.label), String.valueOf(b.label)));
            main.post(() -> cb.onAppsLoaded(result));
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
