package com.moneytrack.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

final class UpdateChecker {
    static final String PREFS = "app_updates";
    static final String KEY_DOWNLOAD_ID = "download_id";
    private static final String KEY_PENDING_URL = "pending_url";
    private static final String KEY_PENDING_VERSION = "pending_version";
    private static final String LATEST_API =
            "https://api.github.com/repos/FITZET/Money-Track/releases/latest";
    private static final String DOWNLOAD_PREFIX =
            "https://github.com/FITZET/Money-Track/releases/download/";
    private static final AtomicBoolean CHECKED = new AtomicBoolean(false);

    private UpdateChecker() {}

    static void check(Activity activity) {
        if (!CHECKED.compareAndSet(false, true)) return;
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(LATEST_API).openConnection();
                connection.setConnectTimeout(6_000);
                connection.setReadTimeout(6_000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "MoneyTrack-Android");
                if (connection.getResponseCode() != 200) return;
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }
                JSONObject release = new JSONObject(body.toString());
                String version = release.optString("tag_name", "").replaceFirst("^[vV]", "");
                String apkUrl = findApk(release.optJSONArray("assets"));
                if (apkUrl.isEmpty() || !apkUrl.startsWith(DOWNLOAD_PREFIX) ||
                        compareVersions(version, currentVersion(activity)) <= 0) return;
                activity.runOnUiThread(() -> showUpdate(activity, version, apkUrl));
            } catch (Exception ignored) {
                // Update checks must never interrupt bookkeeping.
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "moneytrack-update-check").start();
    }

    static void resumePending(Activity activity) {
        if (Build.VERSION.SDK_INT < 26 || !activity.getPackageManager().canRequestPackageInstalls()) return;
        SharedPreferences preferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String url = preferences.getString(KEY_PENDING_URL, "");
        String version = preferences.getString(KEY_PENDING_VERSION, "");
        if (url.startsWith(DOWNLOAD_PREFIX) && !version.isEmpty()) {
            preferences.edit().remove(KEY_PENDING_URL).remove(KEY_PENDING_VERSION).apply();
            startDownload(activity, version, url);
        }
    }

    private static void showUpdate(Activity activity, String version, String apkUrl) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        new AlertDialog.Builder(activity)
                .setTitle("发现新版本 " + version)
                .setMessage("点击更新后会下载 GitHub 正式安装包，并打开 Android 系统安装确认页。现有账目不会丢失。")
                .setNegativeButton("稍后", null)
                .setPositiveButton("更新", (dialog, which) -> prepareDownload(activity, version, apkUrl))
                .show();
    }

    private static void prepareDownload(Activity activity, String version, String apkUrl) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_PENDING_URL, apkUrl)
                    .putString(KEY_PENDING_VERSION, version)
                    .apply();
            new AlertDialog.Builder(activity)
                    .setTitle("允许安装更新")
                    .setMessage("请在系统页面允许钱迹安装未知应用，返回后会自动开始下载。")
                    .setPositiveButton("去设置", (dialog, which) -> activity.startActivity(new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()))))
                    .setNegativeButton("取消", (dialog, which) -> activity.getSharedPreferences(
                            PREFS, Context.MODE_PRIVATE).edit()
                            .remove(KEY_PENDING_URL).remove(KEY_PENDING_VERSION).apply())
                    .show();
            return;
        }
        startDownload(activity, version, apkUrl);
    }

    private static void startDownload(Context context, String version, String apkUrl) {
        java.io.File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (directory != null) new java.io.File(directory, "MoneyTrack-" + version + ".apk").delete();
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
        request.setTitle("Money Track " + version);
        request.setDescription("正在下载更新");
        request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setMimeType("application/vnd.android.package-archive");
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS,
                "MoneyTrack-" + version + ".apk");
        long id = ((DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(request);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_DOWNLOAD_ID, id).apply();
    }

    private static String findApk(JSONArray assets) {
        if (assets == null) return "";
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset != null && asset.optString("name", "").endsWith(".apk")) {
                return asset.optString("browser_download_url", "");
            }
        }
        return "";
    }

    private static String currentVersion(Context context) throws Exception {
        return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
    }

    static int compareVersions(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int x = i < a.length ? number(a[i]) : 0;
            int y = i < b.length ? number(b[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int number(String value) {
        try { return Integer.parseInt(value.replaceAll("[^0-9].*$", "")); }
        catch (NumberFormatException ignored) { return 0; }
    }
}
