package com.moneytrack.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class CategoryExpensesActivity extends Activity {
    public static final String EXTRA_CATEGORY = "category";
    public static final String EXTRA_FROM = "from";
    public static final String EXTRA_TO = "to";

    private ExpenseDatabase database;
    private LinearLayout root;
    private String category;
    private long fromInclusive;
    private long toExclusive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = new ExpenseDatabase(this);
        category = getIntent().getStringExtra(EXTRA_CATEGORY);
        fromInclusive = getIntent().getLongExtra(EXTRA_FROM, 0L);
        toExclusive = getIntent().getLongExtra(EXTRA_TO, 0L);
        if (category == null || category.trim().isEmpty() || fromInclusive <= 0L || toExclusive <= fromInclusive) {
            finish();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.PAPER);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(root, 20, 18);
        scroll.addView(root);
        setContentView(scroll);
        Ui.applySystemBars(this, scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (root != null) refresh();
    }

    private void refresh() {
        root.removeAllViews();
        List<Expense> expenses = database.expensesByCategory(category, fromInclusive, toExclusive);
        long total = 0L;
        for (Expense expense : expenses) total += expense.amountCents;

        Button back = new Button(this);
        back.setText("‹ 返回统计");
        back.setTextSize(14);
        back.setTextColor(Ui.GREEN);
        back.setAllCaps(false);
        back.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        back.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        back.setOnClickListener(v -> finish());
        root.addView(back, margins(0, 4));

        root.addView(Ui.text(this, category + "明细", 27, Ui.INK, true));
        String month = new SimpleDateFormat("yyyy年M月", Locale.CHINA).format(new Date(fromInclusive));
        TextView summary = Ui.text(this,
                month + " · " + expenses.size() + " 笔 · 共 " + Ui.money(total),
                14, Ui.MUTED, false);
        summary.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 16));
        root.addView(summary);

        if (expenses.isEmpty()) {
            TextView empty = Ui.text(this, "这个分类在所选月份没有支出", 14, Ui.MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setBackground(Ui.background(this, Ui.CARD, 15));
            Ui.pad(empty, 16, 28);
            root.addView(empty, margins(0, 8));
            return;
        }
        for (Expense expense : expenses) addExpense(expense);
    }

    private void addExpense(Expense expense) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.outlined(this, Ui.CARD, 15, Ui.BORDER));
        Ui.pad(card, 14, 12);

        LinearLayout amountRow = new LinearLayout(this);
        amountRow.setGravity(Gravity.CENTER_VERTICAL);
        String title = expense.merchant.isEmpty() ? expense.source : expense.merchant;
        if (title.isEmpty()) title = "支出";
        amountRow.addView(Ui.text(this, title, 15, Ui.INK, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        amountRow.addView(Ui.text(this, Ui.money(expense.amountCents), 18,
                expense.amountCents > 10000 ? Ui.AMBER : Ui.INK, true));
        card.addView(amountRow);

        TextView date = Ui.text(this, Ui.date(expense.expenseTime), 12, Ui.MUTED, false);
        date.setPadding(0, Ui.dp(this, 5), 0, 0);
        card.addView(date);

        if (!expense.note.isEmpty()) {
            TextView note = Ui.text(this, expense.note, 13, Ui.INK, false);
            note.setPadding(0, Ui.dp(this, 7), 0, 0);
            card.addView(note);
        }

        StringBuilder status = new StringBuilder();
        if (expense.reimbursable) status.append(expense.reimbursed ? "已报销" : "待报销");
        if (expense.reimbursementEventId > 0) {
            ReimbursementEvent event = database.getEvent(expense.reimbursementEventId);
            if (event != null) {
                if (status.length() > 0) status.append(" · ");
                status.append(event.title);
            }
        }
        int attachmentCount = database.attachmentCount(expense.id);
        if (attachmentCount > 0) {
            if (status.length() > 0) status.append(" · ");
            status.append(attachmentCount).append(" 个附件");
        }
        if (status.length() > 0) {
            TextView statusView = Ui.text(this, status.toString(), 12,
                    expense.reimbursable && !expense.reimbursed ? Ui.AMBER : Ui.GREEN, true);
            statusView.setPadding(0, Ui.dp(this, 7), 0, 0);
            card.addView(statusView);
        }

        TextView edit = Ui.text(this, "点击查看或编辑  ›", 12, Ui.GREEN, true);
        edit.setGravity(Gravity.END);
        edit.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(edit);
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, ExpenseEditorActivity.class);
            intent.putExtra(ExpenseEditorActivity.EXTRA_EXPENSE_ID, expense.id);
            startActivity(intent);
        });
        root.addView(card, margins(0, 9));
    }

    private LinearLayout.LayoutParams margins(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Ui.dp(this, top), 0, Ui.dp(this, bottom));
        return params;
    }
}
