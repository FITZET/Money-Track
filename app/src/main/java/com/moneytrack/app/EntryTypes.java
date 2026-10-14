package com.moneytrack.app;

import java.util.Arrays;
import java.util.List;

final class EntryTypes {
    static final String EXPENSE = "expense";
    static final String INCOME = "income";
    static final String REFUND = "refund";

    private EntryTypes() {}

    static String label(String type) {
        if (INCOME.equals(type)) return "收入";
        if (REFUND.equals(type)) return "退款";
        return "支出";
    }

    static List<String> fixedCategories(String type) {
        return Arrays.asList("退款", "工资", "奖金", "兼职", "理财", "红包", "其他收入");
    }

    static String typeForIncomeCategory(String category) {
        return "退款".equals(category) ? REFUND : INCOME;
    }

    static String inputLabel(String type) {
        return EXPENSE.equals(type) ? "支出" : "收入";
    }
}
