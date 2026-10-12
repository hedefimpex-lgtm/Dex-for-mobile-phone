package com.dez.desktop.apps;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dez.desktop.DesktopActivity;
import com.dez.desktop.R;
import com.dez.desktop.Ui;

/**
 * Smallest possible DeZ window app; copy it to start a new one. The window frame (title bar,
 * minimise / maximise / close, drag, resize, snap) comes for free — an app only supplies the
 * content view. Register new apps in {@link BuiltInApps}.
 */
final class SampleApp extends WindowApp {
    SampleApp() {
        super(BuiltInApps.SAMPLE, R.string.app_sample, R.drawable.ic_app_sample);
    }

    @Override
    public View createView(DesktopActivity host, Bundle args) {
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setBackgroundColor(Ui.WINDOW_BG);

        TextView text = Ui.text(host, host.getString(R.string.sample_text), 16, Ui.TEXT);
        text.setGravity(Gravity.CENTER);
        content.addView(text);

        int[] clicks = {0};
        content.addView(Ui.button(host, host.getString(R.string.sample_button), v -> {
            clicks[0]++;
            text.setText(host.getString(R.string.sample_clicked, clicks[0]));
        }));
        return content;
    }
}
