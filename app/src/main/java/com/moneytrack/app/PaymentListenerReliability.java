package com.moneytrack.app;

import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.provider.Settings;

public final class PaymentListenerReliability {
    private static final int WATCHDOG_JOB_ID = 47031;
    private static final long FIFTEEN_MINUTES = 15L * 60L * 1000L;

    private PaymentListenerReliability() {}

    public static void ensureConnected(Context context) {
        if (!hasNotificationAccess(context)) return;
        try {
            NotificationListenerService.requestRebind(
                    new ComponentName(context, PaymentNotificationListener.class));
        } catch (RuntimeException ignored) {
            // Some vendor systems reject a rebind while they are already connecting the listener.
        }
        scheduleWatchdog(context);
    }

    public static void scheduleWatchdog(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null || scheduler.getPendingJob(WATCHDOG_JOB_ID) != null) return;
        JobInfo job = new JobInfo.Builder(WATCHDOG_JOB_ID,
                new ComponentName(context, PaymentListenerWatchdogService.class))
                .setPeriodic(FIFTEEN_MINUTES)
                .setPersisted(true)
                .build();
        scheduler.schedule(job);
    }

    public static boolean hasNotificationAccess(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            return manager != null && manager.isNotificationListenerAccessGranted(
                    new ComponentName(context, PaymentNotificationListener.class));
        }
        String enabled = Settings.Secure.getString(context.getContentResolver(),
                "enabled_notification_listeners");
        return enabled != null && enabled.contains(context.getPackageName());
    }
}
