package com.moneytrack.app;

final class ExpenseDisplay {
    private ExpenseDisplay() {}

    static String primaryText(Expense expense) {
        if (hasText(expense.merchant)) return expense.merchant.trim();
        if (hasText(expense.note)) return expense.note.trim();
        if (hasText(expense.source)) return expense.source.trim();
        return expense.category == null ? "记录" : expense.category;
    }

    static String sourceAndDate(Expense expense) {
        StringBuilder detail = new StringBuilder(Ui.date(expense.expenseTime));
        if (hasText(expense.source)) detail.append(" · 来源：").append(expense.source.trim());
        return detail.toString();
    }

    static boolean showNoteBelowPrimary(Expense expense) {
        return hasText(expense.note) && hasText(expense.merchant);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
