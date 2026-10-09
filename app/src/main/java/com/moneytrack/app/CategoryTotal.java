package com.moneytrack.app;

public final class CategoryTotal {
    public final String category;
    public final long amountCents;

    public CategoryTotal(String category, long amountCents) {
        this.category = category;
        this.amountCents = amountCents;
    }
}
