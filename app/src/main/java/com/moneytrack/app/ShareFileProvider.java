package com.moneytrack.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public final class ShareFileProvider extends ContentProvider {
    public static Uri uriForFile(android.content.Context context, File file) {
        return new Uri.Builder().scheme("content")
                .authority(context.getPackageName() + ".fileprovider")
                .appendPath("shared").appendPath(file.getName()).build();
    }

    public static Uri uriForAttachment(android.content.Context context, File file) {
        return new Uri.Builder().scheme("content")
                .authority(context.getPackageName() + ".fileprovider")
                .appendPath("attachments").appendPath(file.getName()).build();
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name != null) {
            String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(name);
            String type = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (type != null) return type;
        }
        return "application/octet-stream";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        File file = resolve(uri);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        File file;
        try { file = resolve(uri); }
        catch (FileNotFoundException exception) { return null; }
        String[] columns = projection == null ?
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) row.add(file.getName());
            else if (OpenableColumns.SIZE.equals(column)) row.add(file.length());
            else row.add(null);
        }
        return cursor;
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        if (getContext() == null || uri.getPathSegments().size() != 2) throw new FileNotFoundException();
        String area = uri.getPathSegments().get(0);
        File directory;
        if ("shared".equals(area)) directory = new File(getContext().getCacheDir(), "shared_pdfs");
        else if ("attachments".equals(area)) directory = AttachmentStore.directory(getContext());
        else throw new FileNotFoundException();
        File file = new File(directory, uri.getPathSegments().get(1));
        try {
            String root = directory.getCanonicalPath() + File.separator;
            if (!file.getCanonicalPath().startsWith(root) || !file.isFile()) throw new FileNotFoundException();
        } catch (IOException exception) { throw new FileNotFoundException(); }
        return file;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
