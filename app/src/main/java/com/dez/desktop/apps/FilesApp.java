package com.dez.desktop.apps;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.dez.desktop.DesktopActivity;
import com.dez.desktop.FileShareProvider;
import com.dez.desktop.R;
import com.dez.desktop.Ui;
import com.dez.desktop.wm.DesktopWindow;
import com.dez.desktop.wm.WindowContent;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** "My Files": two-pane file manager (places sidebar + folder listing), like the DeX file manager. */
final class FilesApp extends WindowApp {
    private static final List<String> TEXT_EXTENSIONS = Arrays.asList(
            "txt", "md", "log", "json", "xml", "csv", "ini", "conf", "cfg", "yml", "yaml", "properties",
            "java", "kt", "py", "js", "ts", "html", "htm", "css", "sh", "c", "cpp", "h", "gradle");

    FilesApp() {
        super(BuiltInApps.FILES, R.string.app_files, R.drawable.ic_app_files);
    }

    @Override
    public View createView(DesktopActivity host, Bundle args) {
        return new FilesView(host);
    }

    static boolean hasStorageAccess(Context c) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return Environment.isExternalStorageManager();
        return c.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    static void requestStorageAccess(DesktopActivity host) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                host.startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + host.getPackageName())));
            } catch (ActivityNotFoundException e) {
                host.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            host.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    @SuppressLint("ViewConstructor")
    static final class FilesView extends LinearLayout implements WindowContent {
        private final DesktopActivity host;
        private final TextView pathView;
        private final ScrollView sidebar;
        private final LinearLayout notice;
        private final TextView emptyView;
        private final EntryAdapter adapter = new EntryAdapter();
        private File root;
        private File current;

        FilesView(DesktopActivity host) {
            super(host);
            this.host = host;
            Context c = host;
            setOrientation(VERTICAL);
            setBackgroundColor(Ui.WINDOW_BG);

            LinearLayout toolbar = new LinearLayout(c);
            toolbar.setGravity(Gravity.CENTER_VERTICAL);
            toolbar.setBackgroundColor(Ui.SURFACE);
            toolbar.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
            toolbar.addView(Ui.iconButton(c, R.drawable.ic_up, 40, 10,
                    c.getString(R.string.files_up), v -> goUp()));
            pathView = Ui.text(c, "", 13, Ui.TEXT);
            pathView.setSingleLine(true);
            pathView.setEllipsize(TextUtils.TruncateAt.START);
            pathView.setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0);
            toolbar.addView(pathView, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            toolbar.addView(Ui.iconButton(c, R.drawable.ic_add, 40, 10,
                    c.getString(R.string.files_new_folder), v -> newFolder()));
            toolbar.addView(Ui.iconButton(c, R.drawable.ic_refresh, 40, 10,
                    c.getString(R.string.files_refresh), v -> refresh()));
            addView(toolbar, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 44)));

            notice = new LinearLayout(c);
            notice.setGravity(Gravity.CENTER_VERTICAL);
            notice.setBackgroundColor(0xFF3A2F12);
            notice.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6));
            TextView msg = Ui.text(c, c.getString(R.string.files_no_access), 12, 0xFFFFE08A);
            notice.addView(msg, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            notice.addView(Ui.button(c, c.getString(R.string.files_grant), v -> requestStorageAccess(host)));
            addView(notice);

            LinearLayout body = new LinearLayout(c);
            body.setOrientation(HORIZONTAL);
            sidebar = new ScrollView(c);
            sidebar.setBackgroundColor(0xFF1A1C22);
            body.addView(sidebar, new LayoutParams(Ui.dp(c, 168), LayoutParams.MATCH_PARENT));

            android.widget.FrameLayout listFrame = new android.widget.FrameLayout(c);
            ListView list = new ListView(c);
            list.setDivider(null);
            list.setSelector(Ui.ripple(c, 0, 0));
            list.setAdapter(adapter);
            list.setOnItemClickListener((p, v, pos, id) -> openEntry(adapter.getItem(pos)));
            list.setOnItemLongClickListener((p, v, pos, id) -> {
                showEntryMenu(adapter.getItem(pos), v);
                return true;
            });
            listFrame.addView(list);
            emptyView = Ui.text(c, c.getString(R.string.files_empty), 14, Ui.TEXT_DIM);
            listFrame.addView(emptyView, new android.widget.FrameLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            body.addView(listFrame, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
            addView(body, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

            onHostResume();
        }

        private File appStorage() {
            File ext = host.getExternalFilesDir(null);
            return ext != null ? ext : host.getFilesDir();
        }

        @Override
        public void onHostResume() {
            boolean access = hasStorageAccess(host);
            notice.setVisibility(access ? GONE : VISIBLE);
            File newRoot = access ? Environment.getExternalStorageDirectory() : appStorage();
            if (!newRoot.equals(root)) {
                root = newRoot;
                buildSidebar(access);
                navigate(root);
            } else {
                refresh();
            }
        }

        private void buildSidebar(boolean access) {
            Context c = getContext();
            LinearLayout places = new LinearLayout(c);
            places.setOrientation(VERTICAL);
            places.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
            if (access) {
                addPlace(places, R.string.files_internal, Environment.getExternalStorageDirectory());
                addPlace(places, R.string.files_downloads, publicDir(Environment.DIRECTORY_DOWNLOADS));
                addPlace(places, R.string.files_documents, publicDir(Environment.DIRECTORY_DOCUMENTS));
                addPlace(places, R.string.files_pictures, publicDir(Environment.DIRECTORY_PICTURES));
                addPlace(places, R.string.files_dcim, publicDir(Environment.DIRECTORY_DCIM));
                addPlace(places, R.string.files_music, publicDir(Environment.DIRECTORY_MUSIC));
            }
            addPlace(places, R.string.files_dez, appStorage());
            addPlace(places, R.string.app_notes, NotesApp.notesDir(c));
            sidebar.removeAllViews();
            sidebar.addView(places);
        }

        private static boolean isUnder(File f, File dir) {
            String p = f.getAbsolutePath();
            String d = dir.getAbsolutePath();
            return p.equals(d) || p.startsWith(d.endsWith("/") ? d : d + "/");
        }

        private static File publicDir(String type) {
            return Environment.getExternalStoragePublicDirectory(type);
        }

        private void addPlace(LinearLayout places, int label, File dir) {
            Context c = getContext();
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 8), 0);
            row.setBackground(Ui.ripple(c, 0, 6));
            ImageView icon = new ImageView(c);
            icon.setImageResource(R.drawable.ic_folder);
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFC14D));
            row.addView(icon, new LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18)));
            TextView t = Ui.text(c, c.getString(label), 13, Ui.TEXT);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(Ui.dp(c, 10), 0, 0, 0);
            row.addView(t);
            File storage = Environment.getExternalStorageDirectory();
            File placeRoot = hasStorageAccess(c) && isUnder(dir, storage) ? storage : dir;
            row.setOnClickListener(v -> {
                if (dir.isDirectory() || dir.mkdirs()) {
                    root = placeRoot;
                    navigate(dir);
                }
            });
            places.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 40)));
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            boolean wide = Ui.pxToDp(getContext(), w) >= 520;
            post(() -> sidebar.setVisibility(wide ? VISIBLE : GONE));
        }

        private void navigate(File dir) {
            current = dir;
            pathView.setText(dir.getAbsolutePath());
            File[] files = dir.listFiles();
            List<File> entries = new ArrayList<>();
            if (files != null) {
                for (File f : files) {
                    if (!f.isHidden()) entries.add(f);
                }
            }
            Collections.sort(entries, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            adapter.set(entries);
            emptyView.setVisibility(entries.isEmpty() ? VISIBLE : GONE);
            DesktopWindow w = DesktopWindow.of(this);
            if (w != null) w.setTitle(dir.getName() + " — " + getContext().getString(R.string.app_files));
        }

        private void refresh() {
            if (current != null) navigate(current.isDirectory() ? current : root);
        }

        private boolean goUp() {
            if (current == null || current.equals(root)) return false;
            File parent = current.getParentFile();
            if (parent == null || !isUnder(parent, root)) return false;
            navigate(parent);
            return true;
        }

        @Override
        public boolean onBackPressed() {
            return goUp();
        }

        private void openEntry(File f) {
            if (f.isDirectory()) {
                navigate(f);
            } else if (isText(f)) {
                openInNotes(f);
            } else {
                Intent i = new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(FileShareProvider.uriFor(f), FileShareProvider.mimeTypeOf(f))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    host.startActivity(Intent.createChooser(i, f.getName())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(host, R.string.files_no_app, Toast.LENGTH_SHORT).show();
                }
            }
        }

        private static boolean isText(File f) {
            String n = f.getName();
            int dot = n.lastIndexOf('.');
            return dot >= 0 && TEXT_EXTENSIONS.contains(n.substring(dot + 1).toLowerCase(Locale.ROOT));
        }

        private void openInNotes(File f) {
            Bundle args = new Bundle();
            args.putString(NotesApp.ARG_PATH, f.getAbsolutePath());
            host.openBuiltIn(BuiltInApps.NOTES, args);
        }

        private void showEntryMenu(File f, View anchor) {
            PopupMenu menu = new PopupMenu(getContext(), anchor, Gravity.END);
            menu.getMenu().add(0, 1, 0, R.string.menu_open);
            if (f.isFile()) {
                menu.getMenu().add(0, 2, 1, R.string.files_open_notes);
                menu.getMenu().add(0, 3, 2, R.string.files_share);
            }
            menu.getMenu().add(0, 4, 3, R.string.files_rename);
            menu.getMenu().add(0, 5, 4, R.string.files_delete);
            menu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1: openEntry(f); break;
                    case 2: openInNotes(f); break;
                    case 3: share(f); break;
                    case 4: rename(f); break;
                    case 5: confirmDelete(f); break;
                    default: return false;
                }
                return true;
            });
            menu.show();
        }

        private void share(File f) {
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType(FileShareProvider.mimeTypeOf(f))
                    .putExtra(Intent.EXTRA_STREAM, FileShareProvider.uriFor(f))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                host.startActivity(Intent.createChooser(send, f.getName()));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(host, R.string.files_no_app, Toast.LENGTH_SHORT).show();
            }
        }

        private void newFolder() {
            promptName(R.string.files_new_folder, "", name -> {
                if (!new File(current, name).mkdir()) fail();
                refresh();
            });
        }

        private void rename(File f) {
            promptName(R.string.files_rename, f.getName(), name -> {
                if (!f.renameTo(new File(f.getParentFile(), name))) fail();
                refresh();
            });
        }

        private void confirmDelete(File f) {
            new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setMessage(getContext().getString(R.string.files_delete_confirm, f.getName()))
                    .setPositiveButton(R.string.files_delete, (d, w) -> {
                        if (!deleteRecursively(f)) fail();
                        refresh();
                    })
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show();
        }

        private static boolean deleteRecursively(File f) {
            File[] children = f.isDirectory() ? f.listFiles() : null;
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursively(child)) return false;
                }
            }
            return f.delete();
        }

        private interface NameCallback {
            void onName(String name);
        }

        private void promptName(int title, String initial, NameCallback cb) {
            EditText input = new EditText(getContext());
            input.setSingleLine(true);
            input.setText(initial);
            new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(title)
                    .setView(input)
                    .setPositiveButton(R.string.dialog_ok, (d, w) -> {
                        String name = input.getText().toString().trim();
                        if (name.isEmpty() || name.contains("/") || name.equals(".") || name.equals("..")) {
                            fail();
                            return;
                        }
                        cb.onName(name);
                    })
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show();
        }

        private void fail() {
            Toast.makeText(getContext(), R.string.files_failed, Toast.LENGTH_SHORT).show();
        }

        private final class EntryAdapter extends BaseAdapter {
            private final List<File> items = new ArrayList<>();

            void set(List<File> files) {
                items.clear();
                items.addAll(files);
                notifyDataSetChanged();
            }

            @Override
            public int getCount() {
                return items.size();
            }

            @Override
            public File getItem(int position) {
                return items.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                Context c = parent.getContext();
                LinearLayout row;
                if (convertView instanceof LinearLayout) {
                    row = (LinearLayout) convertView;
                } else {
                    row = new LinearLayout(c);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
                    row.addView(new ImageView(c), new LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26)));
                    LinearLayout texts = new LinearLayout(c);
                    texts.setOrientation(VERTICAL);
                    texts.setPadding(Ui.dp(c, 12), 0, 0, 0);
                    TextView name = Ui.text(c, "", 14, Ui.TEXT);
                    name.setSingleLine(true);
                    name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                    texts.addView(name);
                    texts.addView(Ui.text(c, "", 11, Ui.TEXT_DIM));
                    row.addView(texts, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
                }
                File f = items.get(position);
                ImageView icon = (ImageView) row.getChildAt(0);
                LinearLayout texts = (LinearLayout) row.getChildAt(1);
                icon.setImageResource(f.isDirectory() ? R.drawable.ic_folder : R.drawable.ic_file);
                icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                        f.isDirectory() ? 0xFFFFC14D : 0xFF8AB4FF));
                ((TextView) texts.getChildAt(0)).setText(f.getName());
                String date = DateFormat.getMediumDateFormat(c).format(f.lastModified());
                String details;
                if (f.isDirectory()) {
                    String[] children = f.list();
                    int count = children == null ? 0 : children.length;
                    details = c.getResources().getQuantityString(R.plurals.files_items, count, count);
                } else {
                    details = Formatter.formatShortFileSize(c, f.length());
                }
                ((TextView) texts.getChildAt(1)).setText(details + "  ·  " + date);
                return row;
            }
        }
    }
}
