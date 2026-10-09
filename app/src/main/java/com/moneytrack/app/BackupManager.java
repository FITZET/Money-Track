package com.moneytrack.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class BackupManager {
    private static final String DATABASE_ENTRY = "database/money_track.db";
    private static final long MAX_ENTRY_BYTES = 500L * 1024L * 1024L;

    private BackupManager() {}

    public static void exportBackup(Context context, ExpenseDatabase database, Uri destination)
            throws IOException {
        AttachmentStore.localizeAll(context, database);
        try (Cursor ignored = database.getWritableDatabase().rawQuery("PRAGMA wal_checkpoint(FULL)", null)) {
            ignored.moveToFirst();
        }
        File databaseFile = context.getDatabasePath("money_track.db");
        OutputStream raw = context.getContentResolver().openOutputStream(destination, "w");
        if (raw == null) throw new IOException("无法创建备份文件");
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(raw))) {
            zip.putNextEntry(new ZipEntry("backup_info.txt"));
            zip.write(("Money Track backup\nformat=1\ndatabaseVersion=" +
                    ExpenseDatabase.CURRENT_DATABASE_VERSION + "\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            addFile(zip, databaseFile, DATABASE_ENTRY);
            File[] attachments = AttachmentStore.directory(context).listFiles();
            if (attachments != null) {
                for (File file : attachments) if (file.isFile()) {
                    addFile(zip, file, "attachments/" + file.getName());
                }
            }
        }
    }

    public static void restoreBackup(Context context, ExpenseDatabase database, Uri source)
            throws IOException {
        File staging = new File(context.getCacheDir(), "restore_staging");
        deleteRecursively(staging);
        if (!staging.mkdirs()) throw new IOException("无法创建恢复目录");
        File stagedDatabase = new File(staging, "money_track.db");
        File stagedAttachments = new File(staging, "attachments");
        stagedAttachments.mkdirs();
        InputStream raw = context.getContentResolver().openInputStream(source);
        if (raw == null) throw new IOException("无法读取备份文件");
        boolean hasDatabase = false;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            byte[] buffer = new byte[32 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                File output = null;
                if (DATABASE_ENTRY.equals(entry.getName())) {
                    output = stagedDatabase;
                    hasDatabase = true;
                } else if (entry.getName().startsWith("attachments/") &&
                        entry.getName().substring("attachments/".length()).matches("[A-Za-z0-9._-]+")) {
                    output = new File(stagedAttachments,
                            entry.getName().substring("attachments/".length()));
                }
                if (output != null) {
                    long total = 0;
                    try (FileOutputStream stream = new FileOutputStream(output)) {
                        int count;
                        while ((count = zip.read(buffer)) != -1) {
                            total += count;
                            if (total > MAX_ENTRY_BYTES) throw new IOException("备份中的单个文件过大");
                            stream.write(buffer, 0, count);
                        }
                    }
                }
                zip.closeEntry();
            }
        } catch (Exception exception) {
            deleteRecursively(staging);
            throw exception instanceof IOException ? (IOException) exception : new IOException(exception);
        }
        if (!hasDatabase) throw new IOException("这不是有效的钱迹备份文件");
        validateDatabase(stagedDatabase);

        File targetDatabase = context.getDatabasePath("money_track.db");
        File databaseBackup = new File(targetDatabase.getParentFile(), "money_track.db.before_restore");
        File targetAttachments = AttachmentStore.directory(context);
        File attachmentBackup = new File(context.getFilesDir(), "attachments_before_restore");
        database.close();
        deleteRecursively(databaseBackup);
        deleteRecursively(attachmentBackup);
        boolean databaseMoved = !targetDatabase.exists() || targetDatabase.renameTo(databaseBackup);
        if (!databaseMoved) throw new IOException("无法暂存现有数据库");
        boolean attachmentsMoved = !targetAttachments.exists() || targetAttachments.renameTo(attachmentBackup);
        if (!attachmentsMoved) {
            if (databaseBackup.exists()) databaseBackup.renameTo(targetDatabase);
            throw new IOException("无法暂存现有附件");
        }
        try {
            copyFile(stagedDatabase, targetDatabase);
            if (!stagedAttachments.renameTo(targetAttachments)) {
                targetAttachments.mkdirs();
                File[] files = stagedAttachments.listFiles();
                if (files != null) for (File file : files) copyFile(file, new File(targetAttachments, file.getName()));
            }
            new File(targetDatabase.getPath() + "-wal").delete();
            new File(targetDatabase.getPath() + "-shm").delete();
            deleteRecursively(databaseBackup);
            deleteRecursively(attachmentBackup);
        } catch (IOException exception) {
            targetDatabase.delete();
            deleteRecursively(targetAttachments);
            if (databaseBackup.exists()) databaseBackup.renameTo(targetDatabase);
            if (attachmentBackup.exists()) attachmentBackup.renameTo(targetAttachments);
            throw exception;
        } finally {
            deleteRecursively(staging);
        }
    }

    private static void validateDatabase(File file) throws IOException {
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            try (Cursor cursor = db.rawQuery("PRAGMA integrity_check", null)) {
                if (!cursor.moveToFirst() || !"ok".equalsIgnoreCase(cursor.getString(0))) {
                    throw new IOException("备份数据库校验失败");
                }
            }
            int version = db.getVersion();
            if (version > ExpenseDatabase.CURRENT_DATABASE_VERSION) {
                throw new IOException("该备份来自更高版本的钱迹，请先更新应用");
            }
            try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM expenses", null)) { cursor.moveToFirst(); }
        } catch (RuntimeException exception) {
            throw new IOException("备份数据库无法读取", exception);
        } finally {
            if (db != null) db.close();
        }
    }

    private static void addFile(ZipOutputStream zip, File file, String entryName) throws IOException {
        if (!file.isFile()) throw new IOException("缺少数据库文件");
        zip.putNextEntry(new ZipEntry(entryName));
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) zip.write(buffer, 0, count);
        }
        zip.closeEntry();
    }

    private static void copyFile(File source, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }
}
