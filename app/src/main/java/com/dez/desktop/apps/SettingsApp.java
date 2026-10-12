package com.dez.desktop.apps;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.dez.desktop.BuildConfig;
import com.dez.desktop.DesktopActivity;
import com.dez.desktop.ExternalLauncher;
import com.dez.desktop.Prefs;
import com.dez.desktop.R;
import com.dez.desktop.Ui;
import com.dez.desktop.Wallpapers;
import com.dez.desktop.wm.WindowContent;

/** DeZ settings: wallpaper, orientation, freeform windows, default home, external display. */
final class SettingsApp extends WindowApp {
    SettingsApp() {
        super(BuiltInApps.SETTINGS, R.string.app_settings, R.drawable.ic_app_settings);
    }

    @Override
    public boolean isSingleInstance() {
        return true;
    }

    @Override
    public View createView(DesktopActivity host, Bundle args) {
        return new SettingsView(host);
    }

    @SuppressLint("ViewConstructor")
    static final class SettingsView extends ScrollView implements WindowContent {
        private final DesktopActivity host;
        private final LinearLayout column;

        SettingsView(DesktopActivity host) {
            super(host);
            this.host = host;
            setBackgroundColor(Ui.WINDOW_BG);
            column = new LinearLayout(host);
            column.setOrientation(LinearLayout.VERTICAL);
            int p = Ui.dp(host, 18);
            column.setPadding(p, Ui.dp(host, 8), p, p);
            addView(column);
            build();
        }

        @Override
        public void onHostResume() {
            build();
        }

        private void build() {
            Context c = host;
            Prefs prefs = host.getPrefs();
            column.removeAllViews();

            header(R.string.settings_wallpaper);
            HorizontalScrollView swatchScroll = new HorizontalScrollView(c);
            swatchScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout swatches = new LinearLayout(c);
            for (int i = Prefs.WALLPAPER_SYSTEM; i < Wallpapers.count(); i++) {
                swatches.addView(swatch(i, prefs.getWallpaper() == i));
            }
            swatchScroll.addView(swatches);
            column.addView(swatchScroll);

            header(R.string.settings_display);
            toggle(R.string.settings_landscape, prefs.isLandscapeLocked(), (b, on) -> {
                prefs.setLandscapeLocked(on);
                host.applyPrefs();
            });
            taskbarOpacity(prefs);

            header(R.string.settings_windows);
            toggle(R.string.settings_freeform, prefs.isFreeformEnabled(), (b, on) -> {
                prefs.setFreeformEnabled(on);
            });
            boolean freeform = ExternalLauncher.isFreeformSupported(c);
            body(freeform ? c.getString(R.string.settings_freeform_on)
                    : c.getString(R.string.settings_freeform_off));
            if (!freeform) {
                TextView cmd = body(c.getString(R.string.settings_freeform_cmd));
                cmd.setTypeface(android.graphics.Typeface.MONOSPACE);
                cmd.setTextIsSelectable(true);
                cmd.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(c, 6)));
                int p = Ui.dp(c, 8);
                cmd.setPadding(p, p, p, p);
                action(R.string.settings_dev_options, host::openDeveloperOptions);
            }
            toggle(R.string.settings_freeform_force, prefs.isFreeformForced(), (b, on) -> {
                prefs.setFreeformForced(on);
                build();
            });
            body(c.getString(R.string.settings_window_size));
            column.addView(sizeChooser(prefs));
            toggle(R.string.settings_desktop_ua, prefs.isDesktopUserAgent(),
                    (b, on) -> prefs.setDesktopUserAgent(on));

            header(R.string.settings_home);
            body(c.getString(R.string.settings_home_desc));
            action(R.string.settings_set_home, host::openHomeSettings);

            header(R.string.settings_external);
            Display[] displays = host.getExternalDisplays();
            if (displays.length == 0) {
                body(c.getString(R.string.settings_external_none));
            } else {
                for (Display d : displays) {
                    body(c.getString(R.string.settings_external_found, d.getName()));
                    action(R.string.external_display_start,
                            () -> host.startOnDisplay(d.getDisplayId()));
                }
            }

            header(R.string.settings_storage);
            body(c.getString(FilesApp.hasStorageAccess(c)
                    ? R.string.settings_storage_granted : R.string.files_no_access));
            if (!FilesApp.hasStorageAccess(c)) {
                action(R.string.files_grant, () -> FilesApp.requestStorageAccess(host));
            }

            header(R.string.settings_about);
            body(c.getString(R.string.settings_version, BuildConfig.VERSION_NAME));
        }

        private void header(int res) {
            TextView t = Ui.text(host, host.getString(res), 13, Ui.ACCENT);
            t.setPadding(0, Ui.dp(host, 18), 0, Ui.dp(host, 8));
            t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
            column.addView(t);
        }

        private TextView body(CharSequence s) {
            TextView t = Ui.text(host, s, 13, Ui.TEXT_DIM);
            t.setPadding(0, Ui.dp(host, 4), 0, Ui.dp(host, 6));
            column.addView(t);
            return t;
        }

        private void toggle(int label, boolean checked, CompoundButton.OnCheckedChangeListener l) {
            Switch s = new Switch(host);
            s.setText(label);
            s.setTextColor(Ui.TEXT);
            s.setTextSize(14);
            s.setChecked(checked);
            s.setOnCheckedChangeListener(l);
            s.setMinHeight(Ui.dp(host, 44));
            column.addView(s, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        private void action(int label, Runnable r) {
            TextView b = Ui.button(host, host.getString(label), v -> r.run());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(host, 4);
            lp.bottomMargin = Ui.dp(host, 4);
            column.addView(b, lp);
        }

        /** Slider for the taskbar's background opacity; applies live while dragging. */
        private void taskbarOpacity(Prefs prefs) {
            Context c = host;
            TextView label = body(c.getString(R.string.settings_taskbar_opacity, prefs.getTaskbarOpacity()));
            SeekBar bar = new SeekBar(c);
            bar.setMax(100);
            bar.setProgress(prefs.getTaskbarOpacity());
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    prefs.setTaskbarOpacity(progress);
                    label.setText(c.getString(R.string.settings_taskbar_opacity, progress));
                    host.applyTaskbarOpacity();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            column.addView(bar, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 40)));
        }

        /** Small / Medium / Large segmented choice for Android apps' pop-up windows. */
        private View sizeChooser(Prefs prefs) {
            Context c = host;
            LinearLayout row = new LinearLayout(c);
            int[] labels = {R.string.size_small, R.string.size_medium, R.string.size_large};
            for (int size = Prefs.SIZE_SMALL; size <= Prefs.SIZE_LARGE; size++) {
                int s = size;
                boolean selected = prefs.getWindowSize() == s;
                TextView b = Ui.button(c, c.getString(labels[s]), v -> {
                    prefs.setWindowSize(s);
                    build();
                });
                if (selected) b.setBackground(Ui.ripple(c, Ui.ACCENT, 8));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lp.rightMargin = Ui.dp(c, 6);
                row.addView(b, lp);
            }
            return row;
        }

        private View swatch(int index, boolean selected) {
            Context c = host;
            FrameLayout frame = new FrameLayout(c);
            int size = Ui.dp(c, 64);
            GradientDrawable d;
            if (index == Prefs.WALLPAPER_SYSTEM) {
                d = Ui.rounded(Ui.SURFACE, Ui.dp(c, 10));
                TextView t = Ui.text(c, c.getString(R.string.settings_system_wallpaper), 11, Ui.TEXT);
                t.setGravity(Gravity.CENTER);
                frame.addView(t, new FrameLayout.LayoutParams(size, size));
            } else {
                d = Wallpapers.create(index);
                d.setCornerRadius(Ui.dp(c, 10));
            }
            if (selected) d.setStroke(Ui.dp(c, 3), Ui.ACCENT);
            frame.setBackground(d);
            frame.setForeground(Ui.ripple(c, 0, 10));
            frame.setOnClickListener(v -> {
                host.getPrefs().setWallpaper(index);
                host.applyPrefs();
                build();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.rightMargin = Ui.dp(c, 10);
            frame.setLayoutParams(lp);
            return frame;
        }
    }
}
