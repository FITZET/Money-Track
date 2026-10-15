package com.moneytrack.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public final class ReimbursementEventActivity extends Activity {
    public static final String EXTRA_EVENT_ID = "event_id";
    private ExpenseDatabase database;
    private long eventId;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = new ExpenseDatabase(this);
        eventId = getIntent().getLongExtra(EXTRA_EVENT_ID, 0L);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.PAPER);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(root, 20, 18);
        scroll.addView(root);
        setContentView(scroll);
        Ui.applySystemBars(this, scroll);
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        root.removeAllViews();
        ReimbursementEvent event = database.getEvent(eventId);
        if (event == null) { finish(); return; }
        root.addView(Ui.text(this, event.title, 27, Ui.INK, true));
        TextView summary = Ui.text(this,
                "待报销 " + Ui.money(event.pendingCents) + " / 总金额 " + Ui.money(event.totalCents),
                14, event.pendingCents > 0 ? Ui.AMBER : Ui.GREEN, true);
        summary.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 18));
        root.addView(summary);

        Button add = new Button(this);
        add.setText("＋ 添加一笔到这个事项");
        add.setTextColor(android.graphics.Color.WHITE);
        add.setTextSize(14); add.setAllCaps(false);
        add.setBackground(Ui.background(this, Ui.GREEN, 13));
        add.setOnClickListener(v -> {
            Intent intent = new Intent(this, ExpenseEditorActivity.class);
            intent.putExtra(ExpenseEditorActivity.EXTRA_REIMBURSEMENT_EVENT_ID, eventId);
            startActivity(intent);
        });
        root.addView(add, margins(0, 10));

        Button merge = new Button(this);
        merge.setText("合并并转发报销材料");
        merge.setTextColor(Ui.GREEN);
        merge.setTextSize(14); merge.setAllCaps(false);
        merge.setBackground(Ui.background(this, Ui.PALE_GREEN, 13));
        merge.setOnClickListener(v -> {
            Intent intent = new Intent(this, ExportMaterialsActivity.class);
            intent.putExtra(ExportMaterialsActivity.EXTRA_EVENT_ID, eventId);
            startActivity(intent);
        });
        root.addView(merge, margins(0, 14));

        List<Expense> pending = database.expensesForEvent(eventId, false);
        addSection("未报销明细 · " + pending.size() + " 笔");
        if (pending.isEmpty()) addEmpty("所有明细都已报销");
        else for (Expense expense : pending) addExpense(expense, false);

        List<Expense> completed = database.expensesForEvent(eventId, true);
        addSection("已报销明细 · " + completed.size() + " 笔");
        if (completed.isEmpty()) addEmpty("暂无已报销明细");
        else for (Expense expense : completed) addExpense(expense, true);
    }

    private void addSection(String title) {
        TextView view = Ui.text(this, title, 17, Ui.INK, true);
        view.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 9)); root.addView(view);
    }

    private void addExpense(Expense expense, boolean checked) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.outlined(this, checked ? Ui.PAPER : Ui.CARD, 15, Ui.BORDER));
        Ui.pad(row, 10, 9);
        CheckBox check = new CheckBox(this);
        check.setChecked(checked);
        check.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));
        row.addView(check);
        LinearLayout textArea = new LinearLayout(this);
        textArea.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, ExpenseDisplay.primaryText(expense) + "  " + Ui.money(expense.amountCents),
                15, checked ? Ui.MUTED : Ui.INK, true);
        title.setMaxLines(2);
        if (checked) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        textArea.addView(title);
        textArea.addView(Ui.text(this, ExpenseDisplay.sourceAndDate(expense) + " · 类别：" + expense.category,
                12, Ui.MUTED, false));
        if (ExpenseDisplay.showNoteBelowPrimary(expense)) {
            textArea.addView(Ui.text(this, "用途：" + expense.note, 12, checked ? Ui.MUTED : Ui.INK, false));
        }
        row.addView(textArea, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        textArea.setOnClickListener(v -> {
            Intent intent = new Intent(this, ExpenseEditorActivity.class);
            intent.putExtra(ExpenseEditorActivity.EXTRA_EXPENSE_ID, expense.id); startActivity(intent);
        });
        check.setOnCheckedChangeListener((button, value) -> {
            database.setExpenseReimbursed(expense.id, value); refresh();
        });
        SwipeDeleteLayout swipe = new SwipeDeleteLayout(this, row, () -> confirmDelete(expense));
        root.addView(swipe, margins(0, 8));
    }

    private void confirmDelete(Expense expense) {
        new AlertDialog.Builder(this)
                .setTitle("删除这笔记录？")
                .setMessage(Ui.date(expense.expenseTime) + " · " + expense.category + " · " +
                        Ui.money(expense.amountCents) + "\n相关附件也会一起删除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    if (database.deleteExpense(expense.id)) refresh();
                }).show();
    }

    private void addEmpty(String message) {
        TextView text = Ui.text(this, message, 13, Ui.MUTED, false);
        text.setGravity(Gravity.CENTER); Ui.pad(text, 10, 18); root.addView(text);
    }

    private LinearLayout.LayoutParams margins(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Ui.dp(this, top), 0, Ui.dp(this, bottom)); return params;
    }
}
