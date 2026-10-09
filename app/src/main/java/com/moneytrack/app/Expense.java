package com.moneytrack.app;

public final class Expense {
    public long id;
    public long amountCents;
    public String category;
    public String note;
    public String merchant;
    public String source;
    public long expenseTime;
    public boolean reimbursable;
    public boolean reimbursed;
    public long reimbursementEventId;
    public long reimbursedAt;
    public String receiptUri;

    public Expense() {
        category = "吃饭";
        note = "";
        merchant = "";
        source = "手动记录";
        receiptUri = "";
        expenseTime = System.currentTimeMillis();
    }
}
