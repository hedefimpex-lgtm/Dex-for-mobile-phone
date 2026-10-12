package com.dez.desktop.shell;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dez.desktop.AppInfo;
import com.dez.desktop.R;
import com.dez.desktop.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** DeX-style app drawer: a panel above the taskbar with search and a grid of all apps. */
@SuppressLint("ViewConstructor")
public class StartMenu extends FrameLayout {
    public interface Callbacks {
        void onAppClicked(AppInfo app);

        void onAppLongClicked(AppInfo app, View anchor);

        void onOpenSettings();

        void onExitDesktop();
    }

    private final Callbacks callbacks;
    private final LinearLayout panel;
    private final EditText search;
    private final AppsAdapter adapter = new AppsAdapter();
    private List<AppInfo> allApps = new ArrayList<>();

    public StartMenu(Context c, Callbacks callbacks) {
        super(c);
        this.callbacks = callbacks;
        setVisibility(GONE);
        setClickable(true);

        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.rounded(Ui.PANEL_BG, Ui.dp(c, 14)));
        panel.setElevation(Ui.dp(c, 28));
        int pad = Ui.dp(c, 14);
        panel.setPadding(pad, pad, pad, Ui.dp(c, 8));
        panel.setClickable(true);

        LinearLayout searchRow = new LinearLayout(c);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(c, 20)));
        searchRow.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0);
        ImageView searchIcon = new ImageView(c);
        searchIcon.setImageResource(R.drawable.ic_search);
        searchIcon.setImageTintList(ColorStateList.valueOf(Ui.TEXT_DIM));
        searchRow.addView(searchIcon, new LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18)));
        search = new EditText(c);
        search.setHint(R.string.search_hint);
        search.setHintTextColor(Ui.TEXT_DIM);
        search.setTextColor(Ui.TEXT);
        search.setTextSize(14);
        search.setSingleLine(true);
        search.setBackground(null);
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            boolean go = actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
            if (go && adapter.getCount() > 0) {
                callbacks.onAppClicked(adapter.getItem(0));
                return true;
            }
            return false;
        });
        searchRow.addView(search, new LinearLayout.LayoutParams(0, Ui.dp(c, 40), 1f));
        panel.addView(searchRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 40)));

        GridView grid = new GridView(c);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(Ui.dp(c, 86));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(Ui.dp(c, 4));
        grid.setSelector(Ui.ripple(c, 0, 10));
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> callbacks.onAppClicked(adapter.getItem(pos)));
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            callbacks.onAppLongClicked(adapter.getItem(pos), v);
            return true;
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        glp.topMargin = Ui.dp(c, 10);
        panel.addView(grid, glp);

        LinearLayout bottom = new LinearLayout(c);
        bottom.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        TextView brand = Ui.text(c, c.getString(R.string.app_name), 13, Ui.TEXT_DIM);
        bottom.addView(brand, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bottom.addView(Ui.iconButton(c, R.drawable.ic_settings, 40, 10,
                c.getString(R.string.app_settings), v -> callbacks.onOpenSettings()));
        bottom.addView(Ui.iconButton(c, R.drawable.ic_power, 40, 10,
                c.getString(R.string.start_exit), v -> callbacks.onExitDesktop()));
        panel.addView(bottom, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 44)));

        addView(panel, new LayoutParams(0, 0, Gravity.BOTTOM | Gravity.START));
    }

    public void setApps(List<AppInfo> apps) {
        allApps = new ArrayList<>(apps);
        adapter.filter(search.getText().toString());
    }

    public boolean isShowing() {
        return getVisibility() == VISIBLE;
    }

    public void show() {
        search.setText("");
        adapter.filter("");
        setVisibility(VISIBLE);
        bringToFront();
        panel.setAlpha(0f);
        panel.setTranslationY(Ui.dp(getContext(), 24));
        panel.animate().alpha(1f).translationY(0).setDuration(160).start();
    }

    public void hide() {
        if (!isShowing()) return;
        InputMethodManager imm = getContext().getSystemService(InputMethodManager.class);
        imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
        setVisibility(GONE);
    }

    public void toggle() {
        if (isShowing()) hide();
        else show();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int margin = Ui.dp(getContext(), 8);
        LayoutParams lp = (LayoutParams) panel.getLayoutParams();
        lp.width = Math.min(w - margin * 2, Ui.dp(getContext(), 620));
        lp.height = Math.min(h - margin * 2, Ui.dp(getContext(), 520));
        lp.setMargins(margin, margin, margin, margin);
        post(() -> panel.setLayoutParams(lp));
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent e) {
        // Tap outside the panel closes the menu.
        if (e.getActionMasked() == MotionEvent.ACTION_UP) hide();
        return true;
    }

    private class AppsAdapter extends BaseAdapter {
        private final List<AppInfo> shown = new ArrayList<>();

        void filter(String query) {
            String q = query.trim().toLowerCase(Locale.getDefault());
            shown.clear();
            for (AppInfo a : allApps) {
                if (q.isEmpty() || String.valueOf(a.label).toLowerCase(Locale.getDefault()).contains(q)) {
                    shown.add(a);
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public AppInfo getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Context c = parent.getContext();
            LinearLayout cell;
            ImageView icon;
            TextView label;
            if (convertView instanceof LinearLayout) {
                cell = (LinearLayout) convertView;
                icon = (ImageView) cell.getChildAt(0);
                label = (TextView) cell.getChildAt(1);
            } else {
                cell = new LinearLayout(c);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 6));
                icon = new ImageView(c);
                cell.addView(icon, new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
                label = Ui.text(c, "", 12, Ui.TEXT);
                label.setGravity(Gravity.CENTER_HORIZONTAL);
                label.setMaxLines(2);
                label.setEllipsize(TextUtils.TruncateAt.END);
                label.setPadding(0, Ui.dp(c, 4), 0, 0);
                cell.addView(label, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            AppInfo app = shown.get(position);
            icon.setImageDrawable(app.icon);
            label.setText(app.label);
            return cell;
        }
    }
}
