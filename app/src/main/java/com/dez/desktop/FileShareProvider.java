package com.dez.desktop;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Locale;

/**
 * Minimal read-only provider so "My Files" can hand files to other apps (Android forbids file:// URIs).
 * Not exported: other apps can only read URIs DeZ explicitly granted them.
 */
public class FileShareProvider extends ContentProvider {
    public static final String AUTHORITY = "com.dez.desktop.files";

    public static Uri uriFor(File file) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY)
                .encodedPath(Uri.encode(file.getAbsolutePath(), "/")).build();
    }

    public static String mimeTypeOf(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String type = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase(Locale.ROOT));
            if (type != null) return type;
        }
        return "application/octet-stream";
    }

    private static File fileOf(Uri uri) throws FileNotFoundException {
        String path = uri.getPath();
        if (path == null) throw new FileNotFoundException(String.valueOf(uri));
        File f = new File(path);
        try {
            f = f.getCanonicalFile();
        } catch (java.io.IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
        if (!f.isFile()) throw new FileNotFoundException(path);
        return f;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new SecurityException("read-only provider");
        return ParcelFileDescriptor.open(fileOf(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        String path = uri.getPath();
        return path == null ? null : mimeTypeOf(new File(path));
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        File f;
        try {
            f = fileOf(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        if (projection == null) {
            projection = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        }
        MatrixCursor c = new MatrixCursor(projection, 1);
        Object[] row = new Object[projection.length];
        for (int i = 0; i < projection.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(projection[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(projection[i])) row[i] = f.length();
        }
        c.addRow(row);
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
