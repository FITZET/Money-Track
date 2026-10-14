package com.moneytrack.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PaymentDeduplicatorTest {
    @Test
    public void suppressesMatchingWalletAndBankNotifications() {
        assertTrue(PaymentDeduplicator.isCrossSourceDuplicate(
                3500L, "招商银行", EntryTypes.EXPENSE,
                3500L, "微信支付", EntryTypes.EXPENSE, 8_000L));
        assertTrue(PaymentDeduplicator.isCrossSourceDuplicate(
                3500L, "支付宝", EntryTypes.REFUND,
                3500L, "建设银行", EntryTypes.REFUND, 30_000L));
    }

    @Test
    public void keepsDifferentAmountsSameSourcesAndOlderTransactions() {
        assertFalse(PaymentDeduplicator.isCrossSourceDuplicate(
                3600L, "招商银行", EntryTypes.EXPENSE,
                3500L, "微信支付", EntryTypes.EXPENSE, 8_000L));
        assertFalse(PaymentDeduplicator.isCrossSourceDuplicate(
                3500L, "支付宝", EntryTypes.EXPENSE,
                3500L, "微信支付", EntryTypes.EXPENSE, 8_000L));
        assertFalse(PaymentDeduplicator.isCrossSourceDuplicate(
                3500L, "招商银行", EntryTypes.EXPENSE,
                3500L, "微信支付", EntryTypes.EXPENSE, 120_001L));
        assertFalse(PaymentDeduplicator.isCrossSourceDuplicate(
                3500L, "招商银行", EntryTypes.REFUND,
                3500L, "微信支付", EntryTypes.EXPENSE, 8_000L));
    }
}
