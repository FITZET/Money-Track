package com.moneytrack.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public final class CategoryManagerActivity extends Activity {
    private ExpenseDatabase database;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        root.addView(Ui.text(this, "开销类别", 27, Ui.INK, true));
        TextView hint = Ui.text(this, "新增后会出现在下次记账的下拉框中；移除不会影响历史账单。", 13, Ui.MUTED, false);
        hint.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 16));
        root.addView(hint);
        Button add = button("＋ 新增类别", Color.WHITE, Ui.GREEN);
        add.setOnClickListener(v -> showAddDialog());
        root.addView(add, margins(0, 16));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        Button done = button("完成", Ui.GREEN, Ui.PALE_GREEN);
        done.setOnClickListener(v -> finish());
        root.addView(done, margins(10, 24));
        setContentView(scroll);
        Ui.applySystemBars(this, scroll);
        refresh();
    }

    private void refresh() {
        list.removeAllViews();
        List<String> categories = database.activeCategories();
        for (String category : categories) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Ui.outlined(this, Ui.CARD, 14, Ui.BORDER));
            Ui.pad(row, 15, 8);
            row.addView(Ui.text(this, category, 16, Ui.INK, true),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Button remove = button("移除", Ui.AMBER, Ui.PALE_AMBER);
            remove.setMinHeight(Ui.dp(this, 38));
            remove.setOnClickListener(v -> {
                if (database.activeCategories().size() <= 1) {
                    Toast.makeText(this, "至少保留一个类别", Toast.LENGTH_SHORT).show();
                    return;
                }
                new AlertDialog.Builder(this).setTitle("移除“" + category + "”？")
                        .setMessage("历史账单会继续保留这个类别。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("移除", (dialog, which) -> {
                            database.archiveCategory(category); refresh();
                        }).show();
            });
            row.addView(remove);
            list.addView(row, margins(0, 9));
        }
    }

    private void showAddDialog() {
        EditText input = new EditText(this);
        input.setHint("类别名称");
        input.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle("新增类别").setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("添加", (dialog, which) -> {
                    if (!database.addCategory(input.getText().toString())) {
                        Toast.makeText(this, "请输入新类别名称", Toast.LENGTH_SHORT).show();
                    }
                    refresh();
                }).show();
    }

    private Button button(String text, int textColor, int background) {
        Button button = new Button(this); button.setText(text); button.setTextColor(textColor);
        button.setTextSize(14); button.setAllCaps(false); button.setMinHeight(Ui.dp(this, 48));
        button.setBackground(Ui.background(this, background, 13)); return button;
    }

    private LinearLayout.LayoutParams margins(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Ui.dp(this, top), 0, Ui.dp(this, bottom)); return params;
    }
}
