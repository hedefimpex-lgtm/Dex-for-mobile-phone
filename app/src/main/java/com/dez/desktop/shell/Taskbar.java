package com.dez.desktop.shell;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.dez.desktop.R;
import com.dez.desktop.Ui;

import java.util.List;

/**
 * DeX-style taskbar: Apps button and navigation keys (back / home / recents) on the left,
 * pinned and running apps in the middle (with running / focused indicators), and the system
 * tray (network, battery, clock, notifications) on the right.
 */
@SuppressLint("ViewConstructor")
public class Taskbar extends LinearLayout {
    public interface Callbacks {
        void onAppsButton();

        void onBackButton();

        void onHomeButton();

        void onRecentsButton();

        void onNotificationsButton();

        void onTaskbarItemClick(String key, View anchor);

        void onTaskbarItemLongClick(String key, View anchor);
    }

    public static final class Item {
        public final String key;
        public final CharSequence label;
        public final Drawable icon;
        public final boolean running;
        public final boolean focused;

        public Item(String key, CharSequence label, Drawable icon, boolean running, boolean focused) {
            this.key = key;
            this.label = label;
            this.icon = icon;
            this.running = running;
            this.focused = focused;
        }
    }

    private final Callbacks callbacks;
    private final LinearLayout itemsRow;
    private final ImageView netIcon;
    private final ImageView batteryIcon;
    private final TextView batteryText;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ConnectivityManager.NetworkCallback netCallback;
    private boolean receiversRegistered;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            if (level < 0 || scale <= 0) return;
            int pct = Math.round(level * 100f / scale);
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            batteryText.setText(pct + "%" + (charging ? "+" : ""));
            batteryIcon.setImageTintList(ColorStateList.valueOf(
                    charging ? 0xFF4CD964 : pct <= 15 ? Ui.DANGER : Ui.TEXT));
        }
    };

    public Taskbar(Context c, Callbacks callbacks) {
        super(c);
        this.callbacks = callbacks;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundColor(Ui.TASKBAR_BG);
        setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0);
        setElevation(Ui.dp(c, 24));

        addNav(R.drawable.ic_apps, R.string.tb_apps, v -> callbacks.onAppsButton());
        addNav(R.drawable.ic_recents, R.string.tb_recents, v -> callbacks.onRecentsButton());
        addNav(R.drawable.ic_home, R.string.tb_home, v -> callbacks.onHomeButton());
        addNav(R.drawable.ic_back, R.string.tb_back, v -> callbacks.onBackButton());

        View divider = new View(c);
        divider.setBackgroundColor(Ui.DIVIDER);
        LayoutParams dlp = new LayoutParams(Ui.dp(c, 1), Ui.dp(c, 24));
        dlp.setMargins(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0);
        addView(divider, dlp);

        HorizontalScrollView scroller = new HorizontalScrollView(c);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        itemsRow = new LinearLayout(c);
        itemsRow.setOrientation(HORIZONTAL);
        itemsRow.setGravity(Gravity.CENTER_VERTICAL);
        scroller.addView(itemsRow, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT));
        addView(scroller, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

        // --- system tray ---
        LinearLayout tray = new LinearLayout(c);
        tray.setOrientation(HORIZONTAL);
        tray.setGravity(Gravity.CENTER_VERTICAL);
        tray.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 4), 0);

        netIcon = trayIcon(c, R.drawable.ic_wifi);
        tray.addView(netIcon, new LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18)));
        batteryIcon = trayIcon(c, R.drawable.ic_battery);
        LayoutParams blp = new LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18));
        blp.leftMargin = Ui.dp(c, 8);
        tray.addView(batteryIcon, blp);
        batteryText = Ui.text(c, "", 12, Ui.TEXT);
        tray.addView(batteryText);

        LinearLayout clock = new LinearLayout(c);
        clock.setOrientation(VERTICAL);
        clock.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        clock.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 6), 0);
        TextClock time = new TextClock(c);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("h:mm a");
        time.setTextColor(Ui.TEXT);
        time.setTextSize(13);
        TextClock date = new TextClock(c);
        date.setFormat24Hour("d MMM EEE");
        date.setFormat12Hour("EEE, MMM d");
        date.setTextColor(Ui.TEXT_DIM);
        date.setTextSize(10);
        clock.addView(time);
        clock.addView(date);
        tray.addView(clock);

        tray.addView(Ui.iconButton(c, R.drawable.ic_bell, 36, 9,
                        c.getString(R.string.tb_notifications), v -> callbacks.onNotificationsButton()),
                new LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)));
        tray.setBackground(Ui.ripple(c, 0, 6));
        tray.setOnClickListener(v -> callbacks.onNotificationsButton());
        addView(tray, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
    }

    private void addNav(int icon, int label, OnClickListener l) {
        Context c = getContext();
        addView(Ui.iconButton(c, icon, 42, 10, c.getString(label), l),
                new LayoutParams(Ui.dp(c, 42), Ui.dp(c, 42)));
    }

    private static ImageView trayIcon(Context c, int res) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setImageTintList(ColorStateList.valueOf(Ui.TEXT));
        return v;
    }

    /** Sets how see-through the taskbar background is (0 = transparent, 100 = solid). */
    public void setOpacity(int percent) {
        int alpha = Math.round(Math.max(0, Math.min(100, percent)) * 2.55f);
        setBackgroundColor((alpha << 24) | (Ui.TASKBAR_BG & 0x00FFFFFF));
        // A fully transparent bar should not cast a shadow over the wallpaper.
        setElevation(Ui.dp(getContext(), 24) * (alpha / 255f));
    }

    public void setItems(List<Item> items) {
        Context c = getContext();
        itemsRow.removeAllViews();
        for (Item item : items) {
            FrameLayout cell = new FrameLayout(c);
            cell.setBackground(Ui.ripple(c, item.focused ? 0x22FFFFFF : 0, 6));
            ImageView icon = new ImageView(c);
            icon.setImageDrawable(item.icon);
            cell.addView(icon, new FrameLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30), Gravity.CENTER));
            if (item.running) {
                View indicator = new View(c);
                indicator.setBackground(Ui.rounded(item.focused ? Ui.ACCENT : Ui.TEXT_DIM, Ui.dp(c, 2)));
                FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(
                        Ui.dp(c, item.focused ? 18 : 6), Ui.dp(c, 3),
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                ilp.bottomMargin = Ui.dp(c, 2);
                cell.addView(indicator, ilp);
            }
            cell.setTooltipText(item.label);
            cell.setContentDescription(item.label);
            cell.setOnClickListener(v -> callbacks.onTaskbarItemClick(item.key, v));
            cell.setOnLongClickListener(v -> {
                callbacks.onTaskbarItemLongClick(item.key, v);
                return true;
            });
            LayoutParams lp = new LayoutParams(Ui.dp(c, 46), Ui.dp(c, 44));
            lp.setMargins(Ui.dp(c, 1), 0, Ui.dp(c, 1), 0);
            itemsRow.addView(cell, lp);
        }
    }

    /** Starts battery and network monitoring; call from onStart. */
    public void start() {
        if (receiversRegistered) return;
        receiversRegistered = true;
        Context c = getContext();
        Intent sticky = c.registerReceiver(batteryReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (sticky != null) batteryReceiver.onReceive(c, sticky);

        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        updateNetwork(cm, cm.getActiveNetwork());
        netCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                main.post(() -> updateNetwork(cm, network));
            }

            @Override
            public void onLost(Network network) {
                main.post(() -> updateNetwork(cm, null));
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                main.post(() -> updateNetwork(cm, network));
            }
        };
        try {
            cm.registerDefaultNetworkCallback(netCallback);
        } catch (RuntimeException e) {
            netCallback = null;
        }
    }

    public void stop() {
        if (!receiversRegistered) return;
        receiversRegistered = false;
        Context c = getContext();
        c.unregisterReceiver(batteryReceiver);
        if (netCallback != null) {
            try {
                c.getSystemService(ConnectivityManager.class).unregisterNetworkCallback(netCallback);
            } catch (RuntimeException ignored) {
            }
            netCallback = null;
        }
    }

    private void updateNetwork(ConnectivityManager cm, Network network) {
        NetworkCapabilities caps = network == null ? null : cm.getNetworkCapabilities(network);
        boolean online = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        netIcon.setAlpha(online ? 1f : 0.35f);
    }
}
