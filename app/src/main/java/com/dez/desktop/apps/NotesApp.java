package com.dez.desktop.apps;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.dez.desktop.DesktopActivity;
import com.dez.desktop.R;
import com.dez.desktop.Ui;
import com.dez.desktop.wm.DesktopWindow;
import com.dez.desktop.wm.WindowContent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Plain-text editor. Opens files passed from "My Files"; untitled notes live in DeZ's storage. */
final class NotesApp extends WindowApp {
    static final String ARG_PATH = "path";

    NotesApp() {
        super(BuiltInApps.NOTES, R.string.app_notes, R.drawable.ic_app_notes);
    }

    @Override
    public View createView(DesktopActivity host, Bundle args) {
        String path = args == null ? null : args.getString(ARG_PATH);
        return new NotesView(host, path == null ? null : new File(path));
    }

    static File notesDir(Context c) {
        File dir = new File(c.getFilesDir(), "notes");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    static final class NotesView extends LinearLayout implements WindowContent {
        private final EditText editor;
        private final TextView fileLabel;
        private File file;
        private boolean dirty;
        private boolean loading;

        NotesView(Context c, File file) {
            super(c);
            setOrientation(VERTICAL);
            setBackgroundColor(Ui.WINDOW_BG);

            LinearLayout toolbar = new LinearLayout(c);
            toolbar.setGravity(Gravity.CENTER_VERTICAL);
            toolbar.setBackgroundColor(Ui.SURFACE);
            toolbar.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 8), 0);
            toolbar.addView(Ui.iconButton(c, R.drawable.ic_add, 40, 10,
                    c.getString(R.string.notes_new), v -> newNote()));
            toolbar.addView(Ui.iconButton(c, R.drawable.ic_save, 40, 10,
                    c.getString(R.string.notes_save), v -> save(true)));
            fileLabel = Ui.text(c, "", 12, Ui.TEXT_DIM);
            fileLabel.setSingleLine(true);
            fileLabel.setPadding(Ui.dp(c, 8), 0, 0, 0);
            toolbar.addView(fileLabel, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            addView(toolbar, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 44)));

            editor = new EditText(c);
            editor.setGravity(Gravity.TOP | Gravity.START);
            editor.setTextColor(Ui.TEXT);
            editor.setHintTextColor(Ui.TEXT_DIM);
            editor.setHint(R.string.notes_hint);
            editor.setTextSize(15);
            editor.setBackground(null);
            int p = Ui.dp(c, 14);
            editor.setPadding(p, p, p, p);
            editor.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (!loading && !dirty) {
                        dirty = true;
                        updateTitle();
                    }
                }
            });
            addView(editor, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

            open(file != null ? file : new File(notesDir(c), c.getString(R.string.notes_default_name)));
        }

        private void open(File f) {
            file = f;
            loading = true;
            String text = "";
            if (f.isFile()) {
                try {
                    if (f.length() > 2 * 1024 * 1024) throw new IOException("too large");
                    text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                } catch (IOException | SecurityException e) {
                    Toast.makeText(getContext(), R.string.files_failed, Toast.LENGTH_SHORT).show();
                }
            }
            editor.setText(text);
            loading = false;
            dirty = false;
            updateTitle();
        }

        private void newNote() {
            if (dirty) save(false);
            Context c = getContext();
            EditText name = new EditText(c);
            name.setSingleLine(true);
            name.setText(R.string.notes_untitled);
            new AlertDialog.Builder(c, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(R.string.notes_new)
                    .setView(name)
                    .setPositiveButton(R.string.dialog_ok, (d, w) -> {
                        String n = name.getText().toString().trim().replace('/', '_');
                        if (n.isEmpty()) return;
                        if (!n.contains(".")) n += ".txt";
                        open(new File(notesDir(c), n));
                    })
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show();
        }

        private void save(boolean announce) {
            try {
                File parent = file.getParentFile();
                if (parent != null) //noinspection ResultOfMethodCallIgnored
                    parent.mkdirs();
                Files.write(file.toPath(), editor.getText().toString().getBytes(StandardCharsets.UTF_8));
                dirty = false;
                updateTitle();
                if (announce) Toast.makeText(getContext(), R.string.notes_saved, Toast.LENGTH_SHORT).show();
            } catch (IOException | SecurityException e) {
                Toast.makeText(getContext(), R.string.files_failed, Toast.LENGTH_SHORT).show();
            }
        }

        private void updateTitle() {
            fileLabel.setText(file.getAbsolutePath());
            DesktopWindow w = DesktopWindow.of(this);
            if (w != null) {
                w.setTitle((dirty ? "• " : "") + file.getName() + " — "
                        + getContext().getString(R.string.app_notes));
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            updateTitle();
        }

        @Override
        public void onWindowClosed() {
            // Notes autosave like a phone notes app; never write an unchanged file.
            if (dirty) save(false);
        }
    }
}
