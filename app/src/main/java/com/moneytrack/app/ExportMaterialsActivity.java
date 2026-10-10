package com.moneytrack.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ExportMaterialsActivity extends Activity {
    public static final String EXTRA_EVENT_ID = "event_id";
    private final Map<Expense, CheckBox> choices = new LinkedHashMap<>();
    private ExpenseDatabase database;
    private ReimbursementEvent event;
    private LinearLayout root;
    private Button generateButton;
    private ProgressBar progress;
    private File generatedFile;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        database = new ExpenseDatabase(this);
        event = database.getEvent(getIntent().getLongExtra(EXTRA_EVENT_ID, 0));
        if (event == null) { finish(); return; }
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.PAPER);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(root, 20, 18);
        scroll.addView(root);
        setContentView(scroll);
        Ui.applySystemBars(this, scroll);

        root.addView(Ui.text(this, "合并报销材料", 27, Ui.INK, true));
        TextView subtitle = Ui.text(this, event.title + " · 勾选要导出的支出", 14, Ui.MUTED, false);
        subtitle.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 15));
        root.addView(subtitle);

        LinearLayout controls = new LinearLayout(this);
        Button all = secondaryButton("全选有材料");
        all.setOnClickListener(v -> setAll(true));
        controls.addView(all, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f));
        Button clear = secondaryButton("清空");
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f);
        clearParams.setMargins(Ui.dp(this, 8), 0, 0, 0);
        clear.setOnClickListener(v -> setAll(false));
        controls.addView(clear, clearParams);
        root.addView(controls, margins(0, 14));

        for (Expense expense : database.allExpensesForEvent(event.id)) addExpenseChoice(expense);
        if (choices.isEmpty()) root.addView(Ui.text(this, "这个事项还没有支出记录", 14, Ui.MUTED, false));

        progress = new ProgressBar(this);
        progress.setVisibility(android.view.View.GONE);
        root.addView(progress, centered(8, 8));
        generateButton = primaryButton("生成合并 PDF");
        generateButton.setOnClickListener(v -> generate());
        root.addView(generateButton, margins(12, 10));
        TextView hint = Ui.text(this,
                "图片会各占一张 A4 页面并自动缩放；原 PDF 会逐页合并。生成后可从系统分享面板选择微信。",
                12, Ui.MUTED, false);
        root.addView(hint, margins(0, 18));
    }

    private void addExpenseChoice(Expense expense) {
        int count = database.attachmentCount(expense.id);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.outlined(this, Ui.CARD, 14, Ui.BORDER));
        Ui.pad(row, 10, 9);
        CheckBox check = new CheckBox(this);
        check.setEnabled(count > 0);
        check.setChecked(count > 0);
        check.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));
        row.addView(check);
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(Ui.text(this, expense.category + "  " + Ui.money(expense.amountCents),
                15, count > 0 ? Ui.INK : Ui.MUTED, true));
        String detail = Ui.date(expense.expenseTime) + " · " +
                (count > 0 ? count + " 个附件" : "无附件");
        text.addView(Ui.text(this, detail, 12, count > 0 ? Ui.GREEN : Ui.MUTED, false));
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        choices.put(expense, check);
        root.addView(row, margins(0, 8));
    }

    private void setAll(boolean checked) {
        for (CheckBox box : choices.values()) if (box.isEnabled()) box.setChecked(checked);
    }

    private void generate() {
        List<ExpenseAttachment> attachments = new ArrayList<>();
        for (Map.Entry<Expense, CheckBox> entry : choices.entrySet()) {
            if (entry.getValue().isChecked()) attachments.addAll(database.attachmentsForExpense(entry.getKey().id));
        }
        if (attachments.isEmpty()) {
            Toast.makeText(this, "请至少勾选一笔有附件的支出", Toast.LENGTH_LONG).show();
            return;
        }
        generateButton.setEnabled(false);
        progress.setVisibility(android.view.View.VISIBLE);
        executor.execute(() -> {
            try {
                File file = PdfMaterialMerger.merge(this, event.title, attachments);
                runOnUiThread(() -> showResult(file));
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    generateButton.setEnabled(true);
                    progress.setVisibility(android.view.View.GONE);
                    new android.app.AlertDialog.Builder(this).setTitle("生成失败")
                            .setMessage(exception.getMessage()).setPositiveButton("知道了", null).show();
                });
            }
        });
    }

    private void showResult(File file) {
        generatedFile = file;
        progress.setVisibility(android.view.View.GONE);
        generateButton.setText(R.string.regenerate_pdf);
        generateButton.setEnabled(true);
        TextView success = Ui.text(this, "已生成：" + file.getName(), 13, Ui.GREEN, true);
        root.addView(success, margins(4, 8));
        Button preview = secondaryButton("预览 PDF");
        preview.setOnClickListener(v -> openPdf(false));
        root.addView(preview, margins(0, 8));
        Button share = primaryButton("转发 PDF");
        share.setOnClickListener(v -> openPdf(true));
        root.addView(share, margins(0, 18));
    }

    private void openPdf(boolean share) {
        if (generatedFile == null) return;
        Uri uri = ShareFileProvider.uriForFile(this, generatedFile);
        try {
            Intent intent;
            if (share) {
                intent = new Intent(Intent.ACTION_SEND);
                intent.setType("application/pdf");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.setClipData(ClipData.newRawUri("报销材料", uri));
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(intent, "选择微信或其他应用"));
            } else {
                intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            }
        } catch (RuntimeException exception) {
            Toast.makeText(this, share ? "没有可用的分享应用" : "没有可预览 PDF 的应用",
                    Toast.LENGTH_LONG).show();
        }
    }

    private Button primaryButton(String text) { return button(text, Color.WHITE, Ui.GREEN); }
    private Button secondaryButton(String text) { return button(text, Ui.GREEN, Ui.PALE_GREEN); }
    private Button button(String text, int color, int background) {
        Button button = new Button(this); button.setText(text); button.setTextColor(color);
        button.setTextSize(14); button.setAllCaps(false);
        button.setBackground(Ui.background(this, background, 13)); return button;
    }
    private LinearLayout.LayoutParams margins(int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, Ui.dp(this, top), 0, Ui.dp(this, bottom)); return p;
    }
    private LinearLayout.LayoutParams centered(int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.gravity = Gravity.CENTER_HORIZONTAL; p.setMargins(0, Ui.dp(this, top), 0, Ui.dp(this, bottom)); return p;
    }

    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
