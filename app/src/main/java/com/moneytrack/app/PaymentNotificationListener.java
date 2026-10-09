package com.moneytrack.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public final class PaymentNotificationListener extends NotificationListenerService {
    private static final String CHANNEL_ID = "payment_capture";
    private static final String PREFS = "payment_detection";
    private View overlayView;
    private WindowManager windowManager;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        PaymentListenerReliability.scheduleWatchdog(this);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("listener_connected", true)
                .putLong("listener_connected_at", System.currentTimeMillis()).apply();
        PaymentListenerReliability.scheduleWatchdog(this);
    }

    @Override
    public void onListenerDisconnected() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("listener_connected", false).apply();
        super.onListenerDisconnected();
        PaymentListenerReliability.ensureConnected(this);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification statusBarNotification) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong("last_notification_at", System.currentTimeMillis()).apply();
        Notification notification = statusBarNotification.getNotification();
        if (notification == null) return;
        Bundle extras = notification.extras;
        String appLabel = appLabel(statusBarNotification.getPackageName());
        PaymentNotificationParser.Result result = PaymentNotificationParser.parse(
                statusBarNotification.getPackageName(), appLabel,
                extras.getCharSequence(Notification.EXTRA_TITLE),
                extras.getCharSequence(Notification.EXTRA_TEXT),
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
                extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        if (result == null || isDuplicate(result)) return;

        if (Settings.canDrawOverlays(this)) showOverlay(result);
        else showCaptureNotification(result);
    }

    @Override
    public void onDestroy() {
        removeOverlay();
        super.onDestroy();
    }

    private boolean isDuplicate(PaymentNotificationParser.Result result) {
        long now = System.currentTimeMillis();
        long previousAmount = getSharedPreferences(PREFS, MODE_PRIVATE).getLong("amount", -1L);
        long previousTime = getSharedPreferences(PREFS, MODE_PRIVATE).getLong("time", 0L);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong("amount", result.amountCents).putLong("time", now).apply();
        return result.amountCents == previousAmount && now - previousTime < 45_000L;
    }

    private String appLabel(String packageName) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            return getPackageManager().getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException exception) {
            return "";
        }
    }

    private void showOverlay(PaymentNotificationParser.Result result) {
        removeOverlay();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.outlined(this, Ui.CARD, 22, Ui.BORDER));
        Ui.pad(card, 18, 16);
        card.setElevation(Ui.dp(this, 12));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(this, result.source + "付款", 15, Ui.MUTED, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = Ui.text(this, "稍后", 13, Ui.MUTED, false);
        close.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), 0, Ui.dp(this, 6));
        close.setOnClickListener(v -> {
            removeOverlay();
            showCaptureNotification(result);
        });
        titleRow.addView(close);
        card.addView(titleRow);

        TextView amount = Ui.text(this, Ui.money(result.amountCents), 30,
                result.amountCents > 10000 ? Ui.AMBER : Ui.INK, true);
        amount.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 4));
        card.addView(amount);
        if (result.amountCents > 10000) {
            TextView badge = Ui.text(this, "超过 100 元 · 重点开销", 12, Ui.AMBER, true);
            badge.setPadding(0, 0, 0, Ui.dp(this, 8));
            card.addView(badge);
        } else {
            TextView prompt = Ui.text(this, "这笔钱花在哪里？", 13, Ui.MUTED, false);
            prompt.setPadding(0, 0, 0, Ui.dp(this, 8));
            card.addView(prompt);
        }

        List<String> categories = new ExpenseDatabase(this).activeCategories();
        int shown = Math.min(6, categories.size());
        int rowCount = (shown + 2) / 3;
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int column = 0; column < 3; column++) {
                int index = rowIndex * 3 + column;
                if (index >= shown) break;
                String category = categories.get(index);
                Button button = categoryButton(category);
                button.setOnClickListener(v -> save(result, category));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0, Ui.dp(this, 44), 1f);
                params.setMargins(column == 0 ? 0 : Ui.dp(this, 4), Ui.dp(this, 4),
                        column == 2 ? 0 : Ui.dp(this, 4), Ui.dp(this, 4));
                row.addView(button, params);
            }
            card.addView(row);
        }
        Button details = categoryButton("需要报销 / 更多类别");
        details.setOnClickListener(v -> {
            removeOverlay();
            openCaptureEditor(result);
        });
        card.addView(details, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.x = Ui.dp(this, 12);
        params.y = Ui.dp(this, 48);
        params.width = getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 24);
        try {
            windowManager.addView(card, params);
            overlayView = card;
        } catch (RuntimeException exception) {
            overlayView = null;
            showCaptureNotification(result);
        }
    }

    private Button categoryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        button.setTextColor(Ui.GREEN);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setBackground(Ui.background(this, Ui.PALE_GREEN, 12));
        return button;
    }

    private void save(PaymentNotificationParser.Result result, String category) {
        Expense expense = new Expense();
        expense.amountCents = result.amountCents;
        expense.category = category;
        expense.source = result.source;
        expense.merchant = result.merchant;
        new ExpenseDatabase(this).insert(expense);
        removeOverlay();
        Toast.makeText(this, "已记为“" + category + "”", Toast.LENGTH_SHORT).show();
    }

    private void openCaptureEditor(PaymentNotificationParser.Result result) {
        Intent intent = new Intent(this, PaymentCaptureActivity.class);
        intent.putExtra(ExpenseEditorActivity.EXTRA_AMOUNT_CENTS, result.amountCents);
        intent.putExtra(ExpenseEditorActivity.EXTRA_SOURCE, result.source);
        intent.putExtra(ExpenseEditorActivity.EXTRA_MERCHANT, result.merchant);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void removeOverlay() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (RuntimeException ignored) {
                // The system may already have removed the overlay during shutdown.
            }
        }
        overlayView = null;
    }

    private void showCaptureNotification(PaymentNotificationParser.Result result) {
        Intent intent = new Intent(this, PaymentCaptureActivity.class);
        intent.putExtra(ExpenseEditorActivity.EXTRA_AMOUNT_CENTS, result.amountCents);
        intent.putExtra(ExpenseEditorActivity.EXTRA_SOURCE, result.source);
        intent.putExtra(ExpenseEditorActivity.EXTRA_MERCHANT, result.merchant);
        int requestCode = (int) (System.currentTimeMillis() & 0x7fffffff);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID);
        builder.setSmallIcon(com.moneytrack.app.R.drawable.ic_money_track)
                .setContentTitle(result.source + " " + Ui.money(result.amountCents))
                .setContentText("点一下选择开销类别")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setPriority(Notification.PRIORITY_HIGH);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(requestCode, builder.build());
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(com.moneytrack.app.R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(getString(com.moneytrack.app.R.string.notification_channel_description));
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                .createNotificationChannel(channel);
    }
}
