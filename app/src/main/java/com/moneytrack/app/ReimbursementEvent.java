package com.moneytrack.app;

public final class ReimbursementEvent {
    public long id;
    public String title = "";
    public String note = "";
    public long createdAt;
    public long completedAt;
    public long totalCents;
    public long pendingCents;
    public int totalCount;
    public int pendingCount;

    public boolean isCompleted() {
        return totalCount > 0 && pendingCount == 0;
    }
}
