package com.moneytrack.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DataBackupActivity extends Activity {
    private static final int REQUEST_EXPORT = 31;
    private static final int REQUEST_IMPORT = 32;
    private ExpenseDatabase database;
    private Button exportButton;
    private Button importButton;
    private ProgressBar progress;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        database = new ExpenseDatabase(this);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.PAPER);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(root, 22, 20);
        scroll.addView(root);
        setContentView(scroll);

        root.addView(Ui.text(this, "数据备份与换机", 27, Ui.INK, true));
        TextView version = Ui.text(this, "钱迹 " + versionName(), 13, Ui.MUTED, false);
        version.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 20));
        root.addView(version);

        root.addView(card("导出完整备份",
                "把账单、报销事项、自定义类别和附件打包成一个 .mtbackup 文件。建议保存到网盘或电脑。"));
        exportButton = primaryButton("导出备份文件");
        exportButton.setOnClickListener(v -> chooseExportLocation());
        root.addView(exportButton, margins(10, 24));

        root.addView(card("在新手机恢复",
                "先在旧手机导出备份，再把文件传到新手机。恢复会用备份内容替换当前手机里的钱迹数据。"));
        importButton = secondaryButton("选择备份并恢复");
        importButton.setOnClickListener(v -> chooseImportFile());
        root.addView(importButton, margins(10, 18));

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = margins(4, 12);
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(progress, progressParams);

        TextView note = Ui.text(this,
                "正常安装新版会保留原数据。不要先卸载旧版；卸载前请先导出备份。发布签名文件也必须长期保留，否则以后无法覆盖更新。",
                13, Ui.AMBER, false);
        root.addView(note, margins(4, 20));
    }

    private LinearLayout card(String title, String description) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.outlined(this, Ui.CARD, 16, Ui.BORDER));
        Ui.pad(card, 16, 14);
        card.addView(Ui.text(this, title, 17, Ui.INK, true));
        TextView detail = Ui.text(this, description, 13, Ui.MUTED, false);
        detail.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(detail);
        return card;
    }

    private void chooseExportLocation() {
        String date = new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, "钱迹备份_" + date + ".mtbackup");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    private void chooseImportFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT) runExport(uri);
        else if (requestCode == REQUEST_IMPORT) {
            new AlertDialog.Builder(this).setTitle("恢复这份备份？")
                    .setMessage("当前手机里的账单和附件会被备份文件替换。建议先导出一份当前数据。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("确认恢复", (dialog, which) -> runImport(uri)).show();
        }
    }

    private void runExport(Uri uri) {
        setWorking(true);
        executor.execute(() -> {
            try {
                BackupManager.exportBackup(this, database, uri);
                runOnUiThread(() -> {
                    setWorking(false);
                    Toast.makeText(this, "完整备份已保存", Toast.LENGTH_LONG).show();
                });
            } catch (Exception exception) { showFailure("备份失败", exception); }
        });
    }

    private void runImport(Uri uri) {
        setWorking(true);
        executor.execute(() -> {
            try {
                BackupManager.restoreBackup(this, database, uri);
                runOnUiThread(() -> {
                    Toast.makeText(this, "数据已恢复", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                });
            } catch (Exception exception) { showFailure("恢复失败", exception); }
        });
    }

    private void showFailure(String title, Exception exception) {
        runOnUiThread(() -> {
            setWorking(false);
            new AlertDialog.Builder(this).setTitle(title).setMessage(exception.getMessage())
                    .setPositiveButton("知道了", null).show();
        });
    }

    private void setWorking(boolean working) {
        exportButton.setEnabled(!working);
        importButton.setEnabled(!working);
        progress.setVisibility(working ? View.VISIBLE : View.GONE);
    }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception ignored) { return ""; }
    }

    private Button primaryButton(String text) { return button(text, Color.WHITE, Ui.GREEN); }
    private Button secondaryButton(String text) { return button(text, Ui.GREEN, Ui.PALE_GREEN); }
    private Button button(String text, int color, int background) {
        Button button = new Button(this); button.setText(text); button.setTextColor(color);
        button.setTextSize(14); button.setAllCaps(false); button.setMinHeight(Ui.dp(this, 50));
        button.setBackground(Ui.background(this, background, 13)); return button;
    }
    private LinearLayout.LayoutParams margins(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Ui.dp(this, top), 0, Ui.dp(this, bottom)); return params;
    }

    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
