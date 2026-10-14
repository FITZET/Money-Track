package com.moneytrack.app;

final class PaymentDeduplicator {
    static final long CROSS_SOURCE_WINDOW_MS = 120_000L;

    private PaymentDeduplicator() {}

    static boolean isWallet(String source) {
        return "微信支付".equals(source) || "支付宝".equals(source);
    }

    static boolean isCrossSourceDuplicate(long currentAmount, String currentSource, String currentType,
                                          long previousAmount, String previousSource, String previousType,
                                          long elapsedMillis) {
        if (currentAmount <= 0L || currentAmount != previousAmount) return false;
        if (!currentType.equals(previousType)) return false;
        if (elapsedMillis < 0L || elapsedMillis > CROSS_SOURCE_WINDOW_MS) return false;
        return isWallet(currentSource) != isWallet(previousSource);
    }
}
