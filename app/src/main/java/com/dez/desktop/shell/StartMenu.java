package com.dez.desktop.shell;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.DragEvent;
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
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.dez.desktop.AppInfo;
import com.dez.desktop.Prefs;
import com.dez.desktop.R;
import com.dez.desktop.Ui;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DeX-style app drawer: a panel above the taskbar with search and a grid of apps and folders.
 * Long-press an app and drop it on another app to make a folder, or on a folder to add it there;
 * long-press without moving shows the app's menu.
 */
@SuppressLint("ViewConstructor")
public class StartMenu extends FrameLayout {
    public interface Callbacks {
        void onAppClicked(AppInfo app);

        void onAppLongClicked(AppInfo app, View anchor);

        void onOpenSettings();

        void onExitDesktop();
    }

    // ApplicationInfo.CATEGORY_* values (inlined; some need newer SDKs), used to name new folders.
    private static final int[] CATEGORIES = {AppInfo.CATEGORY_DEZ, 7, 4, 0, 1, 2, 3, 5, 6, 8};
    private static final int[] CATEGORY_LABELS = {R.string.cat_dez, R.string.cat_productivity,
            R.string.cat_social, R.string.cat_games, R.string.cat_audio, R.string.cat_video,
            R.string.cat_image, R.string.cat_news, R.string.cat_maps, R.string.cat_accessibility};

    /** A grid cell: either one app or a folder of apps. */
    private static final class Entry {
        final AppInfo app;
        final String folder;
        final List<AppInfo> apps;

        Entry(AppInfo app) {
            this.app = app;
            this.folder = null;
            this.apps = null;
        }

        Entry(String folder, List<AppInfo> apps) {
            this.app = null;
            this.folder = folder;
            this.apps = apps;
        }

        CharSequence label() {
            return app != null ? app.label : folder;
        }
    }

    private final Callbacks callbacks;
    private final Prefs prefs;
    private final LinearLayout panel;
    private final EditText search;
    private final EntryAdapter adapter = new EntryAdapter();
    private final FolderOverlay folderOverlay;
    private List<AppInfo> allApps = new ArrayList<>();

    // Drag state: a long-press that never leaves its own cell is treated as "show the menu".
    private View dragSource;
    private boolean dragLeftSource;

    public StartMenu(Context c, Prefs prefs, Callbacks callbacks) {
        super(c);
        this.callbacks = callbacks;
        this.prefs = prefs;
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
                adapter.rebuild();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            boolean go = actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
            if (go && adapter.getCount() > 0 && adapter.getItem(0).app != null) {
                callbacks.onAppClicked(adapter.getItem(0).app);
                return true;
            }
            return false;
        });
        searchRow.addView(search, new LinearLayout.LayoutParams(0, Ui.dp(c, 40), 1f));
        panel.addView(searchRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 40)));

        GridView grid = appGrid(c);
        grid.setAdapter(adapter);
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

        folderOverlay = new FolderOverlay(c);
        addView(folderOverlay, new LayoutParams(0, 0, Gravity.BOTTOM | Gravity.START));
    }

    private static GridView appGrid(Context c) {
        GridView grid = new GridView(c);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(Ui.dp(c, 86));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(Ui.dp(c, 4));
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        return grid;
    }

    public void setApps(List<AppInfo> apps) {
        allApps = new ArrayList<>(apps);
        adapter.rebuild();
        folderOverlay.refresh();
    }

    public boolean isShowing() {
        return getVisibility() == VISIBLE;
    }

    public void show() {
        search.setText("");
        folderOverlay.close();
        adapter.rebuild();
        setVisibility(VISIBLE);
        bringToFront();
        panel.setAlpha(0f);
        panel.setTranslationY(Ui.dp(getContext(), 24));
        panel.animate().alpha(1f).translationY(0).setDuration(160).start();
    }

    public void hide() {
        if (!isShowing()) return;
        folderOverlay.close();
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
        int width = Math.min(w - margin * 2, Ui.dp(getContext(), 620));
        int height = Math.min(h - margin * 2, Ui.dp(getContext(), 520));
        post(() -> {
            for (View v : new View[]{panel, folderOverlay}) {
                LayoutParams lp = (LayoutParams) v.getLayoutParams();
                lp.width = width;
                lp.height = height;
                lp.setMargins(margin, margin, margin, margin);
                v.setLayoutParams(lp);
            }
        });
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent e) {
        // Tap outside the panel closes the menu.
        if (e.getActionMasked() == MotionEvent.ACTION_UP) hide();
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Folders
    // ---------------------------------------------------------------------------------------------

    /** Dropped {@code dragged} on {@code target}: make a folder of the two, or add to a folder. */
    private void onDrop(AppInfo dragged, Entry target) {
        if (target.folder != null) {
            prefs.addToFolder(target.folder, dragged.key);
        } else if (target.app != null && !target.app.key.equals(dragged.key)) {
            prefs.createFolder(folderNameFor(target.app, dragged),
                    java.util.Arrays.asList(target.app.key, dragged.key));
        }
        adapter.rebuild();
    }

    /** A shared category ("Social", "Games"...) if both apps have one, else "Folder". */
    private String folderNameFor(AppInfo a, AppInfo b) {
        if (a.category == b.category) {
            for (int i = 0; i < CATEGORIES.length; i++) {
                if (CATEGORIES[i] == a.category) return getContext().getString(CATEGORY_LABELS[i]);
            }
        }
        return getContext().getString(R.string.folder_default);
    }

    private Map<String, AppInfo> appsByKey() {
        Map<String, AppInfo> map = new HashMap<>();
        for (AppInfo a : allApps) map.put(a.key, a);
        return map;
    }

    /** Apps of a folder that are still installed, in the folder's order. */
    private List<AppInfo> folderApps(String folder, Map<String, AppInfo> byKey) {
        List<AppInfo> out = new ArrayList<>();
        for (String key : prefs.getFolderApps(folder)) {
            AppInfo a = byKey.get(key);
            if (a != null) out.add(a);
        }
        return out;
    }

    /** Icon for a folder: up to four of its apps in a 2x2 grid on a translucent tile. */
    private View folderIcon(List<AppInfo> apps, int sizePx) {
        Context c = getContext();
        GridLayout g = new GridLayout(c);
        g.setColumnCount(2);
        int pad = sizePx / 10;
        g.setPadding(pad, pad, pad, pad);
        g.setBackground(Ui.rounded(0x33FFFFFF, sizePx / 4f));
        int mini = (sizePx - pad * 2) / 2;
        for (int i = 0; i < Math.min(4, apps.size()); i++) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(apps.get(i).icon);
            int p = Math.max(1, mini / 12);
            iv.setPadding(p, p, p, p);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = mini;
            lp.height = mini;
            g.addView(iv, lp);
        }
        return g;
    }

    // ---------------------------------------------------------------------------------------------
    // Grid of apps and folders
    // ---------------------------------------------------------------------------------------------

    private class EntryAdapter extends BaseAdapter {
        private final List<Entry> entries = new ArrayList<>();

        /** Top level: folders plus apps not in a folder. While searching: matching apps, flat. */
        void rebuild() {
            entries.clear();
            String q = search.getText().toString().trim().toLowerCase(Locale.getDefault());
            if (!q.isEmpty()) {
                for (AppInfo a : allApps) {
                    if (String.valueOf(a.label).toLowerCase(Locale.getDefault()).contains(q)) {
                        entries.add(new Entry(a));
                    }
                }
            } else {
                Map<String, AppInfo> byKey = appsByKey();
                java.util.Set<String> inFolder = new java.util.HashSet<>();
                for (String f : prefs.getFolders()) {
                    List<AppInfo> apps = folderApps(f, byKey);
                    if (apps.isEmpty()) continue;
                    entries.add(new Entry(f, apps));
                    for (AppInfo a : apps) inFolder.add(a.key);
                }
                for (AppInfo a : allApps) {
                    if (!inFolder.contains(a.key)) entries.add(new Entry(a));
                }
                Collator collator = Collator.getInstance();
                Collections.sort(entries, (x, y) -> collator.compare(
                        String.valueOf(x.label()), String.valueOf(y.label())));
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public Entry getItem(int position) {
            return entries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Entry e = entries.get(position);
            Context c = parent.getContext();
            int iconPx = Ui.dp(c, 44);
            View icon;
            if (e.app != null) {
                ImageView iv = new ImageView(c);
                iv.setImageDrawable(e.app.icon);
                icon = iv;
            } else {
                icon = folderIcon(e.apps, iconPx);
            }
            LinearLayout cell = cell(c, icon, e.label(), iconPx);
            if (e.app != null) {
                cell.setOnClickListener(v -> callbacks.onAppClicked(e.app));
                cell.setOnLongClickListener(v -> {
                    // No dragging into folders while a search shows a flat list.
                    if (search.getText().length() > 0) {
                        callbacks.onAppLongClicked(e.app, v);
                    } else {
                        startDrag(e.app, v, icon);
                    }
                    return true;
                });
            } else {
                cell.setOnClickListener(v -> folderOverlay.open(e.folder));
            }
            cell.setOnDragListener((v, ev) -> onCellDrag(v, ev, e));
            return cell;
        }
    }

    private static LinearLayout cell(Context c, View icon, CharSequence text, int iconPx) {
        LinearLayout cell = new LinearLayout(c);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 6));
        cell.setBackground(Ui.ripple(c, 0, 10));
        cell.addView(icon, new LinearLayout.LayoutParams(iconPx, iconPx));
        TextView label = Ui.text(c, text, 12, Ui.TEXT);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(0, Ui.dp(c, 4), 0, 0);
        cell.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return cell;
    }

    private void startDrag(AppInfo app, View cell, View icon) {
        dragSource = cell;
        dragLeftSource = false;
        cell.startDragAndDrop(ClipData.newPlainText("dez-app", app.key),
                new View.DragShadowBuilder(icon), app, 0);
    }

    private boolean onCellDrag(View cell, DragEvent ev, Entry target) {
        if (!(ev.getLocalState() instanceof AppInfo)) return false;
        AppInfo dragged = (AppInfo) ev.getLocalState();
        Context c = getContext();
        switch (ev.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
                if (cell != dragSource) cell.setBackground(Ui.ripple(c, 0x553D8BFF, 10));
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                if (cell == dragSource) dragLeftSource = true;
                cell.setBackground(Ui.ripple(c, 0, 10));
                return true;
            case DragEvent.ACTION_DROP:
                cell.setBackground(Ui.ripple(c, 0, 10));
                if (cell == dragSource) return false;
                onDrop(dragged, target);
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                cell.setBackground(Ui.ripple(c, 0, 10));
                if (cell == dragSource) {
                    // Long-press released in place: show the app's menu instead.
                    if (!ev.getResult() && !dragLeftSource) callbacks.onAppLongClicked(dragged, cell);
                    dragSource = null;
                }
                return true;
            default:
                return true;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Open folder
    // ---------------------------------------------------------------------------------------------

    /** An open folder over the Apps panel: editable name and the folder's apps. */
    private class FolderOverlay extends FrameLayout {
        private final EditText title;
        private final GridView grid;
        private final FolderAdapter folderAdapter = new FolderAdapter();
        private String folder;

        FolderOverlay(Context c) {
            super(c);
            setVisibility(GONE);
            setClickable(true);
            setBackground(Ui.rounded(0x99000000, Ui.dp(c, 14)));
            setElevation(Ui.dp(c, 30));

            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(c, 18)));
            int p = Ui.dp(c, 14);
            card.setPadding(p, Ui.dp(c, 6), p, p);
            card.setClickable(true);

            title = new EditText(c);
            title.setSingleLine(true);
            title.setGravity(Gravity.CENTER);
            title.setTextColor(Ui.TEXT);
            title.setTextSize(16);
            title.setHint(R.string.folder_name_hint);
            title.setHintTextColor(Ui.TEXT_DIM);
            title.setBackground(null);
            title.setImeOptions(EditorInfo.IME_ACTION_DONE);
            title.setOnEditorActionListener((v, actionId, event) -> {
                commitTitle();
                title.clearFocus();
                getContext().getSystemService(InputMethodManager.class)
                        .hideSoftInputFromWindow(title.getWindowToken(), 0);
                return true;
            });
            card.addView(title, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            grid = appGrid(c);
            grid.setAdapter(folderAdapter);
            card.addView(grid, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER);
            int m = Ui.dp(c, 24);
            lp.setMargins(m, m, m, m);
            addView(card, lp);
        }

        void open(String name) {
            folder = name;
            title.setText(name);
            refresh();
            setVisibility(VISIBLE);
            bringToFront();
            setAlpha(0f);
            animate().alpha(1f).setDuration(140).start();
        }

        void close() {
            if (getVisibility() != VISIBLE) return;
            commitTitle();
            setVisibility(GONE);
            folder = null;
            adapter.rebuild();
        }

        void refresh() {
            if (folder == null) return;
            List<AppInfo> apps = folderApps(folder, appsByKey());
            if (apps.isEmpty()) {
                close();
                return;
            }
            folderAdapter.set(apps);
        }

        private void commitTitle() {
            if (folder == null) return;
            folder = prefs.renameFolder(folder, title.getText().toString());
            title.setText(folder);
        }

        private void showAppMenu(AppInfo app, View anchor) {
            PopupMenu menu = new PopupMenu(getContext(), anchor);
            menu.getMenu().add(0, 1, 0, R.string.folder_remove);
            menu.getMenu().add(0, 2, 1, R.string.folder_more);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    prefs.removeFromFolder(folder, app.key);
                    if (!prefs.getFolders().contains(folder)) {
                        close(); // a folder with one app left dissolves
                    } else {
                        refresh();
                        adapter.rebuild();
                    }
                } else {
                    callbacks.onAppLongClicked(app, anchor);
                }
                return true;
            });
            menu.show();
        }

        @Override
        @SuppressLint("ClickableViewAccessibility")
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) close();
            return true;
        }

        private class FolderAdapter extends BaseAdapter {
            private final List<AppInfo> apps = new ArrayList<>();

            void set(List<AppInfo> list) {
                apps.clear();
                apps.addAll(list);
                notifyDataSetChanged();
            }

            @Override
            public int getCount() {
                return apps.size();
            }

            @Override
            public AppInfo getItem(int position) {
                return apps.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                AppInfo app = apps.get(position);
                Context c = parent.getContext();
                ImageView icon = new ImageView(c);
                icon.setImageDrawable(app.icon);
                LinearLayout cell = cell(c, icon, app.label, Ui.dp(c, 44));
                cell.setOnClickListener(v -> callbacks.onAppClicked(app));
                cell.setOnLongClickListener(v -> {
                    showAppMenu(app, v);
                    return true;
                });
                return cell;
            }
        }
    }
}
