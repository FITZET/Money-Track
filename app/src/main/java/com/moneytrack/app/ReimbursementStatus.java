package com.moneytrack.app;

final class ReimbursementStatus {
    static final int PENDING = 0;
    static final int SUBMITTED = 1;
    static final int REIMBURSED = 2;

    private ReimbursementStatus() {}

    static String label(int status) {
        if (status == SUBMITTED) return "已提交材料";
        if (status == REIMBURSED) return "已报销";
        return "待报销";
    }
}
