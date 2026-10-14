package com.moneytrack.app;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public final class UpdateDownloadReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
        long completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
        long expectedId = context.getSharedPreferences(UpdateChecker.PREFS, Context.MODE_PRIVATE)
                .getLong(UpdateChecker.KEY_DOWNLOAD_ID, -2L);
        if (completedId != expectedId) return;
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        Uri apk = manager.getUriForDownloadedFile(completedId);
        if (apk == null) return;
        context.getSharedPreferences(UpdateChecker.PREFS, Context.MODE_PRIVATE).edit()
                .remove(UpdateChecker.KEY_DOWNLOAD_ID).apply();
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(apk, "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(install);
    }
}
