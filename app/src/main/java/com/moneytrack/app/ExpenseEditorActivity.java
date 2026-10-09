package com.moneytrack.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ClipData;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExpenseEditorActivity extends Activity {
    public static final String EXTRA_EXPENSE_ID = "expense_id";
    public static final String EXTRA_AMOUNT_CENTS = "amount_cents";
    public static final String EXTRA_SOURCE = "source";
    public static final String EXTRA_MERCHANT = "merchant";
    public static final String EXTRA_REIMBURSEMENT_EVENT_ID = "reimbursement_event_id";
    private static final int REQUEST_RECEIPT = 21;

    private ExpenseDatabase database;
    private Expense expense;
    private EditText amountInput;
    private Spinner categoryInput;
    private EditText merchantInput;
    private EditText noteInput;
    private CheckBox reimbursableInput;
    private LinearLayout eventArea;
    private Spinner eventInput;
    private TextView highlightHint;
    private LinearLayout attachmentsArea;
    private final List<ExpenseAttachment> existingAttachments = new ArrayList<>();
    private final List<ExpenseAttachment> pendingAttachments = new ArrayList<>();
    private final List<Long> deletedAttachmentIds = new ArrayList<>();
    private List<String> categories = new ArrayList<>();
    private List<ReimbursementEvent> events = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = new ExpenseDatabase(this);
        long id = getIntent().getLongExtra(EXTRA_EXPENSE_ID, 0L);
        expense = id > 0 ? database.get(id) : new Expense();
        if (expense == null) expense = new Expense();
        if (expense.id == 0) {
            expense.amountCents = getIntent().getLongExtra(EXTRA_AMOUNT_CENTS, 0L);
            expense.source = valueOrEmpty(getIntent().getStringExtra(EXTRA_SOURCE));
            if (expense.source.isEmpty()) expense.source = "手动记录";
            expense.merchant = valueOrEmpty(getIntent().getStringExtra(EXTRA_MERCHANT));
            expense.reimbursementEventId = getIntent().getLongExtra(EXTRA_REIMBURSEMENT_EVENT_ID, 0L);
            expense.reimbursable = expense.reimbursementEventId > 0;
        }
        if (expense.id > 0) existingAttachments.addAll(database.attachmentsForExpense(expense.id));
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (categoryInput != null) loadCategories(expense.category);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.PAPER);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(root, 22, 20);
        scroll.addView(root);

        String title = expense.id > 0 ? "编辑记录" :
                (expense.source.equals("手动记录") ? "记一笔" : "确认这笔付款");
        root.addView(Ui.text(this, title, 27, Ui.INK, true));
        TextView subtitle = Ui.text(this, expense.source.equals("手动记录") ?
                "花在哪儿，简单记一下" : expense.source + " · 付款后分类", 14, Ui.MUTED, false);
        subtitle.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 20));
        root.addView(subtitle);

        root.addView(label("金额"));
        amountInput = edit("0.00", true);
        amountInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountInput.setText(expense.amountCents > 0 ? centsAsInput(expense.amountCents) : "");
        amountInput.setTextSize(28);
        root.addView(amountInput, fieldParams());

        highlightHint = Ui.text(this, "超过 100 元 · 重点开销", 12, Ui.AMBER, true);
        highlightHint.setPadding(Ui.dp(this, 2), 0, 0, Ui.dp(this, 15));
        root.addView(highlightHint);
        updateHighlight();
        amountInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateHighlight(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        LinearLayout categoryHeader = new LinearLayout(this);
        categoryHeader.setGravity(Gravity.CENTER_VERTICAL);
        categoryHeader.addView(label("开销类别"), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView manage = Ui.text(this, "管理类别", 13, Ui.GREEN, true);
        manage.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), 0, Ui.dp(this, 7));
        manage.setOnClickListener(v -> startActivity(new Intent(this, CategoryManagerActivity.class)));
        categoryHeader.addView(manage);
        root.addView(categoryHeader);
        categoryInput = new Spinner(this);
        categoryInput.setBackground(Ui.outlined(this, Ui.CARD, 13, Ui.BORDER));
        categoryInput.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        root.addView(categoryInput, fieldParams());
        loadCategories(expense.category);

        root.addView(label("商户或用途（可选）"));
        merchantInput = edit("例如：高铁票、某某餐厅", true);
        merchantInput.setText(expense.merchant);
        root.addView(merchantInput, fieldParams());

        root.addView(label("备注（可选）"));
        noteInput = edit("补充这笔钱为什么花", false);
        noteInput.setMinLines(2);
        noteInput.setGravity(Gravity.TOP);
        noteInput.setText(expense.note);
        root.addView(noteInput, fieldParams());

        reimbursableInput = new CheckBox(this);
        reimbursableInput.setText("这笔钱需要报销");
        reimbursableInput.setTextSize(15);
        reimbursableInput.setTextColor(Ui.INK);
        reimbursableInput.setChecked(expense.reimbursable);
        reimbursableInput.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));
        root.addView(reimbursableInput, matchWrap(0, 6));

        eventArea = new LinearLayout(this);
        eventArea.setOrientation(LinearLayout.VERTICAL);
        eventArea.addView(label("关联报销事项"));
        eventInput = new Spinner(this);
        eventInput.setBackground(Ui.outlined(this, Ui.CARD, 13, Ui.BORDER));
        eventInput.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        eventArea.addView(eventInput, fieldParams());
        Button newEvent = secondaryButton("＋ 新建报销事项");
        newEvent.setOnClickListener(v -> showCreateEventDialog());
        eventArea.addView(newEvent, matchWrap(0, 12));
        root.addView(eventArea);
        loadEvents(expense.reimbursementEventId);
        eventArea.setVisibility(reimbursableInput.isChecked() ? View.VISIBLE : View.GONE);
        reimbursableInput.setOnCheckedChangeListener((button, checked) ->
                eventArea.setVisibility(checked ? View.VISIBLE : View.GONE));

        root.addView(label("附件材料（图片或 PDF，可多选）"));
        attachmentsArea = new LinearLayout(this);
        attachmentsArea.setOrientation(LinearLayout.VERTICAL);
        root.addView(attachmentsArea);
        renderAttachments();
        Button attachmentButton = secondaryButton("＋ 添加图片或 PDF");
        attachmentButton.setOnClickListener(v -> chooseAttachments());
        root.addView(attachmentButton, matchWrap(4, 18));

        Button save = primaryButton(expense.id > 0 ? "保存修改" : "保存这笔记录");
        save.setOnClickListener(v -> save());
        root.addView(save, matchWrap(0, 10));
        Button cancel = secondaryButton("取消");
        cancel.setOnClickListener(v -> finish());
        root.addView(cancel, matchWrap(0, 24));
        setContentView(scroll);

        if (expense.amountCents == 0) {
            amountInput.requestFocus();
            amountInput.postDelayed(() -> ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                    .showSoftInput(amountInput, InputMethodManager.SHOW_IMPLICIT), 250);
        }
    }

    private void loadCategories(String selected) {
        categories = database.activeCategories();
        if (selected != null && !selected.isEmpty() && !categories.contains(selected)) categories.add(selected);
        categoryInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, categories));
        int position = categories.indexOf(selected);
        categoryInput.setSelection(position >= 0 ? position : 0);
    }

    private void loadEvents(long selectedId) {
        events = database.selectableEvents();
        boolean containsSelected = false;
        for (ReimbursementEvent event : events) if (event.id == selectedId) containsSelected = true;
        if (selectedId > 0 && !containsSelected) {
            ReimbursementEvent selected = database.getEvent(selectedId);
            if (selected != null) events.add(selected);
        }
        List<String> names = new ArrayList<>();
        for (ReimbursementEvent event : events) names.add(event.title);
        eventInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, names));
        for (int i = 0; i < events.size(); i++) if (events.get(i).id == selectedId) eventInput.setSelection(i);
    }

    private void showCreateEventDialog() {
        EditText input = edit("例如：10 月上海出差", true);
        new AlertDialog.Builder(this).setTitle("新建报销事项").setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("创建", (dialog, which) -> {
                    long id = database.createEvent(input.getText().toString());
                    if (id == 0) Toast.makeText(this, "请输入事项名称", Toast.LENGTH_SHORT).show();
                    else loadEvents(id);
                }).show();
    }

    private void chooseAttachments() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "application/pdf"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_RECEIPT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_RECEIPT && resultCode == RESULT_OK && data != null) {
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) addPendingAttachment(clip.getItemAt(i).getUri());
            } else if (data.getData() != null) {
                addPendingAttachment(data.getData());
            }
            renderAttachments();
        }
    }

    private void addPendingAttachment(Uri uri) {
        if (uri == null) return;
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (SecurityException ignored) {}
        String mime = valueOrEmpty(getContentResolver().getType(uri));
        if (!mime.startsWith("image/") && !mime.equals("application/pdf")) {
            Toast.makeText(this, "只支持图片和 PDF", Toast.LENGTH_SHORT).show();
            return;
        }
        String uriText = uri.toString();
        for (ExpenseAttachment item : existingAttachments) if (item.uri.equals(uriText)) return;
        for (ExpenseAttachment item : pendingAttachments) if (item.uri.equals(uriText)) return;
        ExpenseAttachment item = new ExpenseAttachment();
        item.uri = uriText;
        item.mimeType = mime;
        item.displayName = displayName(uri);
        pendingAttachments.add(item);
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (RuntimeException ignored) {}
        String last = uri.getLastPathSegment();
        return last == null ? "附件" : last;
    }

    private void renderAttachments() {
        if (attachmentsArea == null) return;
        attachmentsArea.removeAllViews();
        List<ExpenseAttachment> shown = new ArrayList<>();
        shown.addAll(existingAttachments);
        shown.addAll(pendingAttachments);
        if (shown.isEmpty()) {
            TextView empty = Ui.text(this, "还没有附件", 13, Ui.MUTED, false);
            empty.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), 0, Ui.dp(this, 7));
            attachmentsArea.addView(empty);
            return;
        }
        for (ExpenseAttachment item : shown) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Ui.outlined(this, Ui.CARD, 11, Ui.BORDER));
            Ui.pad(row, 10, 7);
            String kind = item.mimeType.equals("application/pdf") ? "PDF" : "图片";
            TextView name = Ui.text(this, kind + " · " + item.displayName, 13, Ui.INK, false);
            name.setSingleLine(true);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView remove = Ui.text(this, "移除", 13, Ui.AMBER, true);
            remove.setPadding(Ui.dp(this, 12), Ui.dp(this, 5), 0, Ui.dp(this, 5));
            remove.setOnClickListener(v -> {
                if (item.id > 0) { existingAttachments.remove(item); deletedAttachmentIds.add(item.id); }
                else pendingAttachments.remove(item);
                renderAttachments();
            });
            row.addView(remove);
            name.setOnClickListener(v -> openAttachment(item));
            attachmentsArea.addView(row, matchWrap(0, 6));
        }
    }

    private void openAttachment(ExpenseAttachment item) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.uri));
            intent.setDataAndType(Uri.parse(item.uri), item.mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (RuntimeException exception) {
            Toast.makeText(this, "原文件已不可用，请重新选择附件", Toast.LENGTH_LONG).show();
        }
    }

    private void save() {
        long cents = parseCents(amountInput.getText().toString());
        if (cents <= 0) { amountInput.setError("请输入正确金额"); amountInput.requestFocus(); return; }
        if (categories.isEmpty()) { Toast.makeText(this, "请先添加一个类别", Toast.LENGTH_SHORT).show(); return; }
        if (reimbursableInput.isChecked() && events.isEmpty()) {
            Toast.makeText(this, "需要报销的支出必须关联一个事项", Toast.LENGTH_LONG).show();
            showCreateEventDialog();
            return;
        }
        List<ExpenseAttachment> localizedPending = new ArrayList<>();
        try {
            for (ExpenseAttachment item : pendingAttachments) {
                Uri uri = Uri.parse(item.uri);
                localizedPending.add(AttachmentStore.isInternal(this, uri) ? item :
                        AttachmentStore.importUri(this, uri, item.mimeType, item.displayName));
            }
        } catch (IOException | SecurityException exception) {
            Toast.makeText(this, "附件复制失败，请重新选择：" + exception.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        expense.amountCents = cents;
        expense.category = categoryInput.getSelectedItem().toString();
        expense.merchant = merchantInput.getText().toString().trim();
        expense.note = noteInput.getText().toString().trim();
        expense.reimbursable = reimbursableInput.isChecked() || expense.reimbursed;
        if (reimbursableInput.isChecked()) {
            expense.reimbursementEventId = events.get(eventInput.getSelectedItemPosition()).id;
        } else if (!expense.reimbursed) {
            expense.reimbursementEventId = 0;
        }
        if (!expense.reimbursable) { expense.reimbursed = false; expense.reimbursedAt = 0; }
        if (expense.id == 0) expense.id = database.insert(expense); else database.update(expense);
        for (long attachmentId : deletedAttachmentIds) database.deleteAttachment(attachmentId);
        for (ExpenseAttachment item : localizedPending) {
            database.addAttachment(expense.id, item.uri, item.mimeType, item.displayName);
        }
        Toast.makeText(this, "已记录 " + Ui.money(cents), Toast.LENGTH_SHORT).show();
        finish();
    }

    private void updateHighlight() {
        if (highlightHint != null) highlightHint.setVisibility(
                parseCents(amountInput.getText().toString()) > 10000 ? View.VISIBLE : View.GONE);
    }

    private TextView label(String value) {
        TextView label = Ui.text(this, value, 13, Ui.MUTED, true);
        label.setPadding(Ui.dp(this, 2), 0, 0, Ui.dp(this, 7)); return label;
    }
    private EditText edit(String hint, boolean singleLine) {
        EditText field = new EditText(this);
        field.setHint(hint); field.setTextColor(Ui.INK); field.setHintTextColor(Color.rgb(154, 159, 154));
        field.setTextSize(16); field.setSingleLine(singleLine);
        field.setBackground(Ui.outlined(this, Ui.CARD, 13, Ui.BORDER));
        field.setPadding(Ui.dp(this, 14), Ui.dp(this, 11), Ui.dp(this, 14), Ui.dp(this, 11)); return field;
    }
    private Button primaryButton(String text) { return styledButton(text, Color.WHITE, Ui.GREEN, 54); }
    private Button secondaryButton(String text) { return styledButton(text, Ui.GREEN, Ui.PALE_GREEN, 48); }
    private Button styledButton(String text, int textColor, int background, int height) {
        Button button = new Button(this); button.setText(text); button.setTextSize(14);
        button.setTextColor(textColor); button.setAllCaps(false);
        button.setBackground(Ui.background(this, background, 13)); button.setMinHeight(Ui.dp(this, height)); return button;
    }
    private LinearLayout.LayoutParams fieldParams() { return matchWrap(0, 14); }
    private LinearLayout.LayoutParams matchWrap(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Ui.dp(this, top), 0, Ui.dp(this, bottom)); return params;
    }
    private static long parseCents(String input) {
        try { return new BigDecimal(input.trim().replace(",", "")).setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2).longValueExact(); } catch (Exception ignored) { return 0L; }
    }
    private static String centsAsInput(long cents) { return BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString(); }
    private static String valueOrEmpty(String value) { return value == null ? "" : value; }
}
