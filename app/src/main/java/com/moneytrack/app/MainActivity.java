package com.moneytrack.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private ExpenseDatabase database;
    private LinearLayout content;
    private LinearLayout nav;
    private int currentTab = 0;
    private int statisticsMonthOffset = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = new ExpenseDatabase(this);
        PaymentListenerReliability.ensureConnected(this);
        buildShell();
        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        PaymentListenerReliability.ensureConnected(this);
        showTab(currentTab);
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.PAPER);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(content, 20, 18);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(Ui.CARD);
        nav.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        addNav("概览", 0);
        addNav("账单", 1);
        addNav("报销", 2);
        addNav("统计", 3);
        root.addView(nav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 64)));
        setContentView(root);
        Ui.applySystemBars(this, root);
    }

    private void addNav(String label, int tab) {
        TextView item = Ui.text(this, label, 14, Ui.MUTED, false);
        item.setGravity(Gravity.CENTER);
        item.setTag(tab);
        item.setOnClickListener(v -> showTab((int) v.getTag()));
        nav.addView(item, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

    private void showTab(int tab) {
        if (content == null) return;
        currentTab = tab;
        for (int i = 0; i < nav.getChildCount(); i++) {
            TextView item = (TextView) nav.getChildAt(i);
            boolean selected = (int) item.getTag() == tab;
            item.setTextColor(selected ? Ui.GREEN : Ui.MUTED);
            item.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        content.removeAllViews();
        if (tab == 0) showOverview();
        else if (tab == 1) showExpenses(database.recent(200), "全部账单", "每一笔都清楚");
        else if (tab == 2) showReimbursements();
        else showStatistics();
    }

    private void showOverview() {
        Calendar calendar = Calendar.getInstance();
        addHeader(calendar.get(Calendar.MONTH) + 1 + "月账本", "把该记住的钱记住");

        LinearLayout totals = new LinearLayout(this);
        totals.setOrientation(LinearLayout.VERTICAL);
        totals.setBackground(Ui.background(this, Ui.GREEN, 22));
        Ui.pad(totals, 20, 18);
        TextView caption = Ui.text(this, "本月已支出", 14, Color.WHITE, false);
        caption.setAlpha(0.82f);
        totals.addView(caption);
        TextView value = Ui.text(this, Ui.money(database.totalThisMonth()), 34, Color.WHITE, true);
        value.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 14));
        totals.addView(value);

        LinearLayout split = new LinearLayout(this);
        split.setOrientation(LinearLayout.HORIZONTAL);
        split.addView(metric("重点开销", database.highlightedThisMonth(), true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        split.addView(metric("待报销", database.pendingTotal(), false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        totals.addView(split);
        content.addView(totals, matchWrap(0, 14));

        Button add = primaryButton("＋ 记一笔");
        add.setOnClickListener(v -> startActivity(new Intent(this, ExpenseEditorActivity.class)));
        content.addView(add, matchWrap(0, 18));

        Button backup = secondaryButton("数据备份与换机");
        backup.setOnClickListener(v -> startActivity(new Intent(this, DataBackupActivity.class)));
        content.addView(backup, matchWrap(0, 18));

        addPermissionPanel();
        sectionLabel("最近记录");
        List<Expense> recent = database.recent(5);
        if (recent.isEmpty()) addEmpty("还没有记录。付款后会提醒你分类，也可以手动记一笔。 ");
        else for (Expense expense : recent) addExpenseCard(expense, false);
    }

    private LinearLayout metric(String label, long cents, boolean first) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        if (!first) box.setPadding(Ui.dp(this, 18), 0, 0, 0);
        TextView labelView = Ui.text(this, label, 12, Color.WHITE, false);
        labelView.setAlpha(0.75f);
        box.addView(labelView);
        box.addView(Ui.text(this, Ui.money(cents), 18, Color.WHITE, true));
        return box;
    }

    private void addPermissionPanel() {
        boolean listener = isNotificationAccessGranted();
        boolean overlay = Settings.canDrawOverlays(this);
        if (listener && overlay) return;

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.outlined(this, Ui.CARD, 18, Ui.BORDER));
        Ui.pad(panel, 16, 14);
        panel.addView(Ui.text(this, "开启付款后提醒", 16, Ui.INK, true));
        TextView description = Ui.text(this,
                "识别微信、支付宝和银行 App 的支出通知；悬浮窗用于付款后显示分类卡片。", 13, Ui.MUTED, false);
        description.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 10));
        panel.addView(description);
        if (!listener) {
            Button button = secondaryButton("1  开启通知使用权");
            button.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
            panel.addView(button, matchWrap(0, 8));
        }
        if (!overlay) {
            Button button = secondaryButton((listener ? "2  " : "2  ") + "允许悬浮窗");
            button.setOnClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            });
            panel.addView(button, matchWrap(0, 0));
        }
        content.addView(panel, matchWrap(0, 20));
    }

    private void showExpenses(List<Expense> expenses, String title, String subtitle) {
        addHeader(title, subtitle);
        Button add = primaryButton("＋ 记一笔");
        add.setOnClickListener(v -> startActivity(new Intent(this, ExpenseEditorActivity.class)));
        content.addView(add, matchWrap(0, 18));
        if (expenses.isEmpty()) addEmpty("暂无账单");
        else for (Expense expense : expenses) addExpenseCard(expense, false);
    }

    private void showReimbursements() {
        addHeader("报销事项", "还有 " + Ui.money(database.pendingTotal()) + " 没收回来");
        Button add = primaryButton("＋ 新建报销事项");
        add.setOnClickListener(v -> showCreateEventDialog());
        content.addView(add, matchWrap(0, 18));

        sectionLabel("待报销事项");
        List<ReimbursementEvent> pending = database.pendingEvents();
        if (pending.isEmpty()) addEmpty("没有待报销事项");
        else for (ReimbursementEvent event : pending) addEventCard(event, false);

        List<Expense> ungrouped = database.ungroupedPendingReimbursements();
        if (!ungrouped.isEmpty()) {
            sectionLabel("未归入事项的旧记录");
            for (Expense expense : ungrouped) addExpenseCard(expense, true);
        }

        sectionLabel("已报销事项");
        List<ReimbursementEvent> completed = database.completedEvents();
        if (completed.isEmpty()) addEmpty("暂无已报销事项");
        else for (ReimbursementEvent event : completed) addEventCard(event, true);
    }

    private void showCreateEventDialog() {
        EditText input = new EditText(this);
        input.setHint("例如：10 月上海出差");
        input.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle("新建报销事项").setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("创建", (dialog, which) -> {
                    long id = database.createEvent(input.getText().toString());
                    if (id == 0) Toast.makeText(this, "请输入事项名称", Toast.LENGTH_SHORT).show();
                    else {
                        Intent intent = new Intent(this, ExpenseEditorActivity.class);
                        intent.putExtra(ExpenseEditorActivity.EXTRA_REIMBURSEMENT_EVENT_ID, id);
                        startActivity(intent);
                    }
                }).show();
    }

    private void addEventCard(ReimbursementEvent event, boolean completed) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.outlined(this, completed ? Ui.PAPER : Ui.CARD, 16, Ui.BORDER));
        Ui.pad(card, 10, 11);
        CheckBox check = new CheckBox(this);
        check.setChecked(completed);
        check.setEnabled(event.totalCount > 0);
        check.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));
        card.addView(check);
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, event.title, 16, completed ? Ui.MUTED : Ui.INK, true);
        details.addView(title);
        String summary = completed ?
                "已报销 " + Ui.money(event.totalCents) + " · " + event.totalCount + " 笔" :
                "待报销 " + Ui.money(event.pendingCents) + " / 总额 " + Ui.money(event.totalCents) +
                        " · 剩 " + event.pendingCount + " 笔";
        details.addView(Ui.text(this, summary, 12, completed ? Ui.MUTED : Ui.AMBER, false));
        details.setPadding(0, 0, Ui.dp(this, 8), 0);
        card.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = Ui.text(this, "›", 25, Ui.MUTED, false);
        card.addView(arrow);
        details.setOnClickListener(v -> openEvent(event.id));
        arrow.setOnClickListener(v -> openEvent(event.id));
        check.setOnCheckedChangeListener((button, checked) -> {
            database.setEventReimbursed(event.id, checked);
            Toast.makeText(this, checked ? "整个事项已报销，可在下方取消" : "事项已恢复为待报销",
                    Toast.LENGTH_SHORT).show();
            showTab(2);
        });
        content.addView(card, matchWrap(0, 9));
    }

    private void openEvent(long eventId) {
        Intent intent = new Intent(this, ReimbursementEventActivity.class);
        intent.putExtra(ReimbursementEventActivity.EXTRA_EVENT_ID, eventId);
        startActivity(intent);
    }

    private void showStatistics() {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 0); start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0); start.set(Calendar.MILLISECOND, 0);
        start.add(Calendar.MONTH, statisticsMonthOffset);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.MONTH, 1);
        List<CategoryTotal> totals = database.categoryTotals(start.getTimeInMillis(), end.getTimeInMillis());
        long total = 0L;
        for (CategoryTotal item : totals) total += item.amountCents;

        LinearLayout monthRow = new LinearLayout(this);
        monthRow.setGravity(Gravity.CENTER_VERTICAL);
        Button previous = secondaryButton("‹");
        previous.setOnClickListener(v -> { statisticsMonthOffset--; showTab(3); });
        monthRow.addView(previous, new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 44)));
        TextView month = Ui.text(this, start.get(Calendar.YEAR) + "年" +
                (start.get(Calendar.MONTH) + 1) + "月", 22, Ui.INK, true);
        month.setGravity(Gravity.CENTER);
        monthRow.addView(month, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button next = secondaryButton("›");
        next.setEnabled(statisticsMonthOffset < 0);
        next.setAlpha(next.isEnabled() ? 1f : 0.35f);
        next.setOnClickListener(v -> { statisticsMonthOffset++; showTab(3); });
        monthRow.addView(next, new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 44)));
        content.addView(monthRow, matchWrap(0, 8));

        TextView totalView = Ui.text(this, "总支出 " + Ui.money(total), 18, Ui.GREEN, true);
        totalView.setGravity(Gravity.CENTER);
        content.addView(totalView, matchWrap(0, 4));
        if (totals.isEmpty()) { addEmpty("这个月还没有支出记录"); return; }
        ExpensePieChart chart = new ExpensePieChart(this);
        chart.setValues(totals);
        content.addView(chart, matchWrap(0, 8));
        for (int i = 0; i < totals.size(); i++) {
            CategoryTotal item = totals.get(i);
            addCategoryLegend(item, total, ExpensePieChart.COLORS[i % ExpensePieChart.COLORS.length],
                    start.getTimeInMillis(), end.getTimeInMillis());
        }
    }

    private void addCategoryLegend(CategoryTotal item, long total, int color,
                                   long fromInclusive, long toExclusive) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.outlined(this, Ui.CARD, 13, Ui.BORDER));
        Ui.pad(row, 13, 11);
        TextView dot = Ui.text(this, "●", 18, color, false);
        row.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 28), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(Ui.text(this, item.category, 15, Ui.INK, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        double percent = total == 0 ? 0 : item.amountCents * 100.0 / total;
        row.addView(Ui.text(this, Ui.money(item.amountCents) + String.format(Locale.CHINA, "  %.1f%%", percent),
                14, Ui.MUTED, false));
        TextView arrow = Ui.text(this, "  ›", 22, Ui.MUTED, false);
        row.addView(arrow);
        row.setOnClickListener(v -> {
            Intent intent = new Intent(this, CategoryExpensesActivity.class);
            intent.putExtra(CategoryExpensesActivity.EXTRA_CATEGORY, item.category);
            intent.putExtra(CategoryExpensesActivity.EXTRA_FROM, fromInclusive);
            intent.putExtra(CategoryExpensesActivity.EXTRA_TO, toExclusive);
            startActivity(intent);
        });
        content.addView(row, matchWrap(0, 8));
    }

    private void addHeader(String title, String subtitle) {
        content.addView(Ui.text(this, title, 28, Ui.INK, true));
        TextView detail = Ui.text(this, subtitle, 14, Ui.MUTED, false);
        detail.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 18));
        content.addView(detail);
    }

    private void sectionLabel(String text) {
        TextView label = Ui.text(this, text, 17, Ui.INK, true);
        label.setPadding(0, 0, 0, Ui.dp(this, 10));
        content.addView(label);
    }

    private void addExpenseCard(Expense expense, boolean showReimburseButton) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.outlined(this, Ui.CARD, 16, Ui.BORDER));
        Ui.pad(card, 15, 13);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView category = Ui.text(this, expense.category, 16, Ui.INK, true);
        row.addView(category, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView amount = Ui.text(this, Ui.money(expense.amountCents), 18,
                expense.amountCents > 10000 ? Ui.AMBER : Ui.INK, true);
        row.addView(amount);
        card.addView(row);

        StringBuilder meta = new StringBuilder(Ui.date(expense.expenseTime));
        if (!expense.source.isEmpty()) meta.append(" · ").append(expense.source);
        if (!expense.merchant.isEmpty()) meta.append(" · ").append(expense.merchant);
        TextView details = Ui.text(this, meta.toString(), 12, Ui.MUTED, false);
        details.setPadding(0, Ui.dp(this, 5), 0, 0);
        card.addView(details);
        if (!expense.note.isEmpty()) {
            TextView note = Ui.text(this, expense.note, 13, Ui.INK, false);
            note.setPadding(0, Ui.dp(this, 7), 0, 0);
            card.addView(note);
        }
        int attachmentCount = database.attachmentCount(expense.id);
        if (attachmentCount > 0) {
            TextView proof = Ui.text(this, "已添加 " + attachmentCount + " 个附件", 12, Ui.GREEN, true);
            proof.setPadding(0, Ui.dp(this, 7), 0, 0);
            card.addView(proof);
        }

        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, ExpenseEditorActivity.class);
            intent.putExtra(ExpenseEditorActivity.EXTRA_EXPENSE_ID, expense.id);
            startActivity(intent);
        });
        if (showReimburseButton) {
            Button done = secondaryButton("标记为已报销");
            done.setOnClickListener(v -> {
                database.markReimbursed(expense.id);
                Toast.makeText(this, "已收回 " + Ui.money(expense.amountCents), Toast.LENGTH_SHORT).show();
                showTab(2);
            });
            card.addView(done, matchWrap(10, 0));
        }
        content.addView(card, matchWrap(0, 10));
    }

    private void addEmpty(String text) {
        TextView empty = Ui.text(this, text, 14, Ui.MUTED, false);
        empty.setGravity(Gravity.CENTER);
        empty.setBackground(Ui.background(this, Ui.CARD, 16));
        Ui.pad(empty, 20, 30);
        content.addView(empty, matchWrap(0, 10));
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(15);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackground(Ui.background(this, Ui.GREEN, 15));
        button.setMinHeight(Ui.dp(this, 52));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(Ui.GREEN);
        button.setAllCaps(false);
        button.setBackground(Ui.background(this, Ui.PALE_GREEN, 12));
        button.setMinHeight(Ui.dp(this, 44));
        return button;
    }

    private LinearLayout.LayoutParams matchWrap(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Ui.dp(this, top), 0, Ui.dp(this, bottom));
        return params;
    }

    private boolean isNotificationAccessGranted() {
        return PaymentListenerReliability.hasNotificationAccess(this);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
    }
}
