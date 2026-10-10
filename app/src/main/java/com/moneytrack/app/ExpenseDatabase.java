package com.moneytrack.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public final class ExpenseDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "money_track.db";
    public static final int CURRENT_DATABASE_VERSION = 3;
    private static final String[] DEFAULT_CATEGORIES = {
            "吃饭", "零用", "衣服鞋子", "打车", "住宿", "交通", "其他"
    };

    public ExpenseDatabase(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, CURRENT_DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE expenses (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "amount_cents INTEGER NOT NULL," +
                "category TEXT NOT NULL," +
                "note TEXT NOT NULL DEFAULT ''," +
                "merchant TEXT NOT NULL DEFAULT ''," +
                "source TEXT NOT NULL DEFAULT '手动记录'," +
                "expense_time INTEGER NOT NULL," +
                "reimbursable INTEGER NOT NULL DEFAULT 0," +
                "reimbursed INTEGER NOT NULL DEFAULT 0," +
                "reimbursement_event_id INTEGER NOT NULL DEFAULT 0," +
                "reimbursed_at INTEGER NOT NULL DEFAULT 0," +
                "receipt_uri TEXT NOT NULL DEFAULT ''," +
                "created_at INTEGER NOT NULL)");
        createSupportingTables(db);
        createAttachmentsTable(db);
        db.execSQL("CREATE INDEX expenses_time_idx ON expenses(expense_time DESC)");
        db.execSQL("CREATE INDEX expenses_reimburse_idx ON expenses(reimbursable, reimbursed)");
        db.execSQL("CREATE INDEX expenses_event_idx ON expenses(reimbursement_event_id, reimbursed)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE expenses ADD COLUMN reimbursement_event_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE expenses ADD COLUMN reimbursed_at INTEGER NOT NULL DEFAULT 0");
            createSupportingTables(db);
            db.execSQL("CREATE INDEX IF NOT EXISTS expenses_event_idx ON expenses(reimbursement_event_id, reimbursed)");
        }
        if (oldVersion < 3) {
            createAttachmentsTable(db);
            db.execSQL("INSERT INTO attachments(expense_id,uri,mime_type,display_name,sort_order,created_at) " +
                    "SELECT id,receipt_uri,'image/*','原凭证',0,created_at FROM expenses " +
                    "WHERE receipt_uri IS NOT NULL AND receipt_uri <> ''");
        }
    }

    private void createSupportingTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS categories (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL UNIQUE," +
                "active INTEGER NOT NULL DEFAULT 1," +
                "sort_order INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS reimbursement_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT NOT NULL," +
                "note TEXT NOT NULL DEFAULT ''," +
                "created_at INTEGER NOT NULL," +
                "completed_at INTEGER NOT NULL DEFAULT 0)");
        for (int i = 0; i < DEFAULT_CATEGORIES.length; i++) {
            ContentValues values = new ContentValues();
            values.put("name", DEFAULT_CATEGORIES[i]);
            values.put("active", 1);
            values.put("sort_order", i);
            db.insertWithOnConflict("categories", null, values, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    private void createAttachmentsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS attachments (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "expense_id INTEGER NOT NULL," +
                "uri TEXT NOT NULL," +
                "mime_type TEXT NOT NULL DEFAULT ''," +
                "display_name TEXT NOT NULL DEFAULT ''," +
                "sort_order INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS attachments_expense_idx ON attachments(expense_id,sort_order,id)");
    }

    public long insert(Expense expense) {
        ContentValues values = toValues(expense);
        values.put("created_at", System.currentTimeMillis());
        long id = getWritableDatabase().insertOrThrow("expenses", null, values);
        if (expense.reimbursementEventId > 0) refreshEventCompletion(expense.reimbursementEventId);
        return id;
    }

    public void update(Expense expense) {
        Expense previous = get(expense.id);
        getWritableDatabase().update("expenses", toValues(expense), "id = ?",
                new String[]{String.valueOf(expense.id)});
        if (previous != null && previous.reimbursementEventId > 0) {
            refreshEventCompletion(previous.reimbursementEventId);
        }
        if (expense.reimbursementEventId > 0 &&
                (previous == null || expense.reimbursementEventId != previous.reimbursementEventId)) {
            refreshEventCompletion(expense.reimbursementEventId);
        }
    }

    public Expense get(long id) {
        try (Cursor cursor = getReadableDatabase().query("expenses", null, "id = ?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public List<Expense> recent(int limit) {
        List<Expense> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("expenses", null, null, null,
                null, null, "expense_time DESC", String.valueOf(limit))) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    public List<Expense> pendingReimbursements() {
        List<Expense> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("expenses", null,
                "reimbursable = 1 AND reimbursed = 0", null, null, null,
                "expense_time DESC")) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    public List<Expense> ungroupedPendingReimbursements() {
        List<Expense> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("expenses", null,
                "reimbursable = 1 AND reimbursed = 0 AND reimbursement_event_id = 0",
                null, null, null, "expense_time DESC")) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    public List<Expense> expensesForEvent(long eventId, boolean reimbursed) {
        List<Expense> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("expenses", null,
                "reimbursement_event_id = ? AND reimbursed = ?",
                new String[]{String.valueOf(eventId), reimbursed ? "1" : "0"},
                null, null, "expense_time DESC")) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    public List<Expense> allExpensesForEvent(long eventId) {
        List<Expense> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("expenses", null,
                "reimbursement_event_id = ?", new String[]{String.valueOf(eventId)},
                null, null, "expense_time ASC,id ASC")) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    public List<ExpenseAttachment> attachmentsForExpense(long expenseId) {
        List<ExpenseAttachment> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("attachments", null,
                "expense_id = ?", new String[]{String.valueOf(expenseId)},
                null, null, "sort_order,id")) {
            while (cursor.moveToNext()) {
                ExpenseAttachment item = new ExpenseAttachment();
                item.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                item.expenseId = expenseId;
                item.uri = cursor.getString(cursor.getColumnIndexOrThrow("uri"));
                item.mimeType = cursor.getString(cursor.getColumnIndexOrThrow("mime_type"));
                item.displayName = cursor.getString(cursor.getColumnIndexOrThrow("display_name"));
                item.sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow("sort_order"));
                result.add(item);
            }
        }
        return result;
    }

    public long addAttachment(long expenseId, String uri, String mimeType, String displayName) {
        ContentValues values = new ContentValues();
        values.put("expense_id", expenseId);
        values.put("uri", safe(uri));
        values.put("mime_type", safe(mimeType));
        values.put("display_name", safe(displayName));
        values.put("sort_order", attachmentCount(expenseId));
        values.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insertOrThrow("attachments", null, values);
    }

    public void deleteAttachment(long id) {
        getWritableDatabase().delete("attachments", "id = ?", new String[]{String.valueOf(id)});
    }

    public int attachmentCount(long expenseId) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM attachments WHERE expense_id = ?",
                new String[]{String.valueOf(expenseId)})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public List<ExpenseAttachment> allAttachments() {
        List<ExpenseAttachment> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("attachments", null,
                null, null, null, null, "expense_id,sort_order,id")) {
            while (cursor.moveToNext()) {
                ExpenseAttachment item = new ExpenseAttachment();
                item.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                item.expenseId = cursor.getLong(cursor.getColumnIndexOrThrow("expense_id"));
                item.uri = cursor.getString(cursor.getColumnIndexOrThrow("uri"));
                item.mimeType = cursor.getString(cursor.getColumnIndexOrThrow("mime_type"));
                item.displayName = cursor.getString(cursor.getColumnIndexOrThrow("display_name"));
                item.sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow("sort_order"));
                result.add(item);
            }
        }
        return result;
    }

    public void updateAttachmentUri(long id, String uri) {
        ContentValues values = new ContentValues();
        values.put("uri", safe(uri));
        getWritableDatabase().update("attachments", values, "id = ?", new String[]{String.valueOf(id)});
    }

    public void markReimbursed(long id) {
        setExpenseReimbursed(id, true);
    }

    public void setExpenseReimbursed(long id, boolean reimbursed) {
        Expense expense = get(id);
        if (expense == null) return;
        ContentValues values = new ContentValues();
        values.put("reimbursed", reimbursed ? 1 : 0);
        values.put("reimbursed_at", reimbursed ? System.currentTimeMillis() : 0L);
        getWritableDatabase().update("expenses", values, "id = ?",
                new String[]{String.valueOf(id)});
        if (expense.reimbursementEventId > 0) refreshEventCompletion(expense.reimbursementEventId);
    }

    public List<String> activeCategories() {
        List<String> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("categories", new String[]{"name"},
                "active = 1", null, null, null, "sort_order, id")) {
            while (cursor.moveToNext()) result.add(cursor.getString(0));
        }
        return result;
    }

    public boolean addCategory(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) return false;
        ContentValues values = new ContentValues();
        values.put("name", clean);
        values.put("active", 1);
        values.put("sort_order", System.currentTimeMillis());
        long id = getWritableDatabase().insertWithOnConflict("categories", null, values,
                SQLiteDatabase.CONFLICT_IGNORE);
        if (id >= 0) return true;
        values.clear();
        values.put("active", 1);
        return getWritableDatabase().update("categories", values, "name = ?",
                new String[]{clean}) > 0;
    }

    public void archiveCategory(String name) {
        ContentValues values = new ContentValues();
        values.put("active", 0);
        getWritableDatabase().update("categories", values, "name = ?", new String[]{name});
    }

    public long createEvent(String title) {
        String clean = title == null ? "" : title.trim();
        if (clean.isEmpty()) return 0L;
        ContentValues values = new ContentValues();
        values.put("title", clean);
        values.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insertOrThrow("reimbursement_events", null, values);
    }

    public ReimbursementEvent getEvent(long id) {
        List<ReimbursementEvent> events = queryEvents("e.id = ?", new String[]{String.valueOf(id)}, null);
        return events.isEmpty() ? null : events.get(0);
    }

    public List<ReimbursementEvent> pendingEvents() {
        return queryEvents("(e.completed_at = 0 OR pending_count > 0)", null,
                "e.created_at DESC");
    }

    public List<ReimbursementEvent> completedEvents() {
        return queryEvents("e.completed_at > 0 AND pending_count = 0 AND total_count > 0", null,
                "e.completed_at DESC");
    }

    public List<ReimbursementEvent> selectableEvents() {
        return queryEvents("e.completed_at = 0 OR pending_count > 0", null, "e.created_at DESC");
    }

    private List<ReimbursementEvent> queryEvents(String where, String[] args, String orderBy) {
        List<ReimbursementEvent> result = new ArrayList<>();
        String sql = "SELECT * FROM (SELECT e.id,e.title,e.note,e.created_at,e.completed_at," +
                "COALESCE(SUM(x.amount_cents),0) total_cents," +
                "COALESCE(SUM(CASE WHEN x.reimbursed=0 THEN x.amount_cents ELSE 0 END),0) pending_cents," +
                "COUNT(x.id) total_count," +
                "COALESCE(SUM(CASE WHEN x.reimbursed=0 THEN 1 ELSE 0 END),0) pending_count " +
                "FROM reimbursement_events e LEFT JOIN expenses x ON x.reimbursement_event_id=e.id " +
                "GROUP BY e.id) e" + (where == null ? "" : " WHERE " + where) +
                (orderBy == null ? "" : " ORDER BY " + orderBy);
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                ReimbursementEvent event = new ReimbursementEvent();
                event.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                event.title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
                event.note = cursor.getString(cursor.getColumnIndexOrThrow("note"));
                event.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
                event.completedAt = cursor.getLong(cursor.getColumnIndexOrThrow("completed_at"));
                event.totalCents = cursor.getLong(cursor.getColumnIndexOrThrow("total_cents"));
                event.pendingCents = cursor.getLong(cursor.getColumnIndexOrThrow("pending_cents"));
                event.totalCount = cursor.getInt(cursor.getColumnIndexOrThrow("total_count"));
                event.pendingCount = cursor.getInt(cursor.getColumnIndexOrThrow("pending_count"));
                result.add(event);
            }
        }
        return result;
    }

    public void setEventReimbursed(long eventId, boolean reimbursed) {
        ReimbursementEvent current = getEvent(eventId);
        if (current == null || current.totalCount == 0) return;
        ContentValues expenses = new ContentValues();
        expenses.put("reimbursed", reimbursed ? 1 : 0);
        expenses.put("reimbursed_at", reimbursed ? System.currentTimeMillis() : 0L);
        getWritableDatabase().update("expenses", expenses, "reimbursement_event_id = ?",
                new String[]{String.valueOf(eventId)});
        ContentValues event = new ContentValues();
        event.put("completed_at", reimbursed ? System.currentTimeMillis() : 0L);
        getWritableDatabase().update("reimbursement_events", event, "id = ?",
                new String[]{String.valueOf(eventId)});
    }

    private void refreshEventCompletion(long eventId) {
        ReimbursementEvent event = getEvent(eventId);
        if (event == null) return;
        boolean complete = event.totalCount > 0 && event.pendingCount == 0;
        ContentValues values = new ContentValues();
        values.put("completed_at", complete ? System.currentTimeMillis() : 0L);
        getWritableDatabase().update("reimbursement_events", values, "id = ?",
                new String[]{String.valueOf(eventId)});
    }

    public List<CategoryTotal> categoryTotals(long fromInclusive, long toExclusive) {
        List<CategoryTotal> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT category,SUM(amount_cents) total FROM expenses " +
                        "WHERE expense_time>=? AND expense_time<? GROUP BY category ORDER BY total DESC",
                new String[]{String.valueOf(fromInclusive), String.valueOf(toExclusive)})) {
            while (cursor.moveToNext()) result.add(new CategoryTotal(cursor.getString(0), cursor.getLong(1)));
        }
        return result;
    }

    public List<Expense> expensesByCategory(String category, long fromInclusive, long toExclusive) {
        List<Expense> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("expenses", null,
                "category = ? AND expense_time >= ? AND expense_time < ?",
                new String[]{category, String.valueOf(fromInclusive), String.valueOf(toExclusive)},
                null, null, "expense_time DESC,id DESC")) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    public long totalThisMonth() {
        return sum("expense_time >= ?", new String[]{String.valueOf(monthStart())});
    }

    public long highlightedThisMonth() {
        return sum("expense_time >= ? AND amount_cents > 10000",
                new String[]{String.valueOf(monthStart())});
    }

    public long pendingTotal() {
        return sum("reimbursable = 1 AND reimbursed = 0", null);
    }

    private long sum(String selection, String[] args) {
        try (Cursor cursor = getReadableDatabase().query("expenses",
                new String[]{"COALESCE(SUM(amount_cents), 0)"}, selection, args,
                null, null, null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }

    private long monthStart() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private ContentValues toValues(Expense expense) {
        ContentValues values = new ContentValues();
        values.put("amount_cents", expense.amountCents);
        values.put("category", expense.category);
        values.put("note", safe(expense.note));
        values.put("merchant", safe(expense.merchant));
        values.put("source", safe(expense.source));
        values.put("expense_time", expense.expenseTime);
        values.put("reimbursable", expense.reimbursable ? 1 : 0);
        values.put("reimbursed", expense.reimbursed ? 1 : 0);
        values.put("reimbursement_event_id", expense.reimbursementEventId);
        values.put("reimbursed_at", expense.reimbursedAt);
        values.put("receipt_uri", safe(expense.receiptUri));
        return values;
    }

    private Expense fromCursor(Cursor cursor) {
        Expense expense = new Expense();
        expense.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        expense.amountCents = cursor.getLong(cursor.getColumnIndexOrThrow("amount_cents"));
        expense.category = cursor.getString(cursor.getColumnIndexOrThrow("category"));
        expense.note = cursor.getString(cursor.getColumnIndexOrThrow("note"));
        expense.merchant = cursor.getString(cursor.getColumnIndexOrThrow("merchant"));
        expense.source = cursor.getString(cursor.getColumnIndexOrThrow("source"));
        expense.expenseTime = cursor.getLong(cursor.getColumnIndexOrThrow("expense_time"));
        expense.reimbursable = cursor.getInt(cursor.getColumnIndexOrThrow("reimbursable")) == 1;
        expense.reimbursed = cursor.getInt(cursor.getColumnIndexOrThrow("reimbursed")) == 1;
        expense.reimbursementEventId = cursor.getLong(cursor.getColumnIndexOrThrow("reimbursement_event_id"));
        expense.reimbursedAt = cursor.getLong(cursor.getColumnIndexOrThrow("reimbursed_at"));
        expense.receiptUri = cursor.getString(cursor.getColumnIndexOrThrow("receipt_uri"));
        return expense;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
