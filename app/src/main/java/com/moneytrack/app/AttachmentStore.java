package com.moneytrack.app;

import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AttachmentStore {
    private AttachmentStore() {}

    public static File directory(Context context) {
        File directory = new File(context.getFilesDir(), "attachments");
        if (!directory.exists()) directory.mkdirs();
        return directory;
    }

    public static ExpenseAttachment importUri(Context context, Uri source,
                                              String mimeType, String displayName) throws IOException {
        String extension = extension(displayName, mimeType);
        File target = new File(directory(context), UUID.randomUUID() + extension);
        try (InputStream input = context.getContentResolver().openInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) throw new IOException("无法读取附件");
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        } catch (IOException exception) {
            target.delete();
            throw exception;
        }
        ExpenseAttachment attachment = new ExpenseAttachment();
        attachment.uri = ShareFileProvider.uriForAttachment(context, target).toString();
        attachment.mimeType = mimeType;
        attachment.displayName = displayName;
        return attachment;
    }

    public static void localizeAll(Context context, ExpenseDatabase database) throws IOException {
        List<ExpenseAttachment> attachments = database.allAttachments();
        for (ExpenseAttachment item : attachments) {
            Uri uri = Uri.parse(item.uri);
            if (isInternal(context, uri)) continue;
            try {
                ExpenseAttachment local = importUri(context, uri, item.mimeType, item.displayName);
                database.updateAttachmentUri(item.id, local.uri);
            } catch (IOException | SecurityException exception) {
                throw new IOException("附件“" + item.displayName + "”无法读取，请重新添加后再备份", exception);
            }
        }
    }

    public static boolean isInternal(Context context, Uri uri) {
        return "content".equals(uri.getScheme()) &&
                (context.getPackageName() + ".fileprovider").equals(uri.getAuthority()) &&
                uri.getPathSegments().size() == 2 && "attachments".equals(uri.getPathSegments().get(0));
    }

    private static String extension(String displayName, String mimeType) {
        int dot = displayName == null ? -1 : displayName.lastIndexOf('.');
        if (dot >= 0 && dot < displayName.length() - 1) {
            String value = displayName.substring(dot).toLowerCase(Locale.ROOT);
            if (value.length() <= 10 && value.matches("\\.[a-z0-9]+")) return value;
        }
        String value = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        return value == null || value.isEmpty() ? "" : "." + value;
    }
}
