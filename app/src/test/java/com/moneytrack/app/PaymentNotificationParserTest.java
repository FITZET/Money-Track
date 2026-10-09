package com.moneytrack.app;

import org.junit.Test;

import static org.junit.Assert.*;

public class PaymentNotificationParserTest {
    @Test
    public void parsesWechatPayment() {
        PaymentNotificationParser.Result result = PaymentNotificationParser.parse(
                "com.tencent.mm", "微信支付", "支付成功 ¥128.50", null, null);
        assertNotNull(result);
        assertEquals(12850L, result.amountCents);
        assertEquals("微信支付", result.source);
    }

    @Test
    public void parsesAlipayAmountInYuan() {
        PaymentNotificationParser.Result result = PaymentNotificationParser.parse(
                "com.eg.android.AlipayGphone", "支付宝", "付款成功 36.8元", null, null);
        assertNotNull(result);
        assertEquals(3680L, result.amountCents);
    }

    @Test
    public void ignoresRefundAndIncomingMoney() {
        assertNull(PaymentNotificationParser.parse("com.tencent.mm", "微信支付",
                "退款成功 50元", null, null));
        assertNull(PaymentNotificationParser.parse("com.eg.android.AlipayGphone", "支付宝",
                "收款到账 50元", null, null));
    }

    @Test
    public void ignoresOtherApps() {
        assertNull(PaymentNotificationParser.parse("com.example.bank", "支付成功",
                "付款100元", null, null));
    }

    @Test
    public void parsesBankExpenseButIgnoresIncome() {
        PaymentNotificationParser.Result expense = PaymentNotificationParser.parse(
                "com.example.mobilebank", "示例银行", "账户动账", "尾号1234消费人民币88.20元", null, null);
        assertNotNull(expense);
        assertEquals(8820L, expense.amountCents);
        assertEquals("示例银行", expense.source);
        assertNull(PaymentNotificationParser.parse(
                "com.example.mobilebank", "示例银行", "账户动账", "工资收入人民币5000元", null, null));
    }
}
