package com.moneytrack.app;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PaymentNotificationParser {
    private static final Pattern[] AMOUNT_PATTERNS = new Pattern[]{
            Pattern.compile("(?:付款|支付|消费|扣款|支出|金额|人民币)[^0-9]{0,10}(?:¥|￥)?\\s*([0-9]+(?:\\.[0-9]{1,2})?)"),
            Pattern.compile("(?:¥|￥)\\s*([0-9]+(?:\\.[0-9]{1,2})?)"),
            Pattern.compile("([0-9]+(?:\\.[0-9]{1,2})?)\\s*元")
    };
    private static final String[] SUCCESS_WORDS = {
            "支付成功", "付款成功", "扣款成功", "消费成功", "已支付", "成功付款", "收款方"
    };
    private static final String[] REJECT_WORDS = {
            "退款", "收款到账", "收到一笔", "转入", "收入", "入账", "余额变动", "验证码", "失败", "取消"
    };
    private static final String[] BANK_OUTGOING_WORDS = {
            "支出", "消费", "扣款", "转出", "支付", "交易成功", "付款"
    };

    private PaymentNotificationParser() {}

    public static Result parse(String packageName, CharSequence title, CharSequence text,
                               CharSequence bigText, CharSequence subText) {
        return parse(packageName, null, title, text, bigText, subText);
    }

    public static Result parse(String packageName, String appLabel, CharSequence title, CharSequence text,
                               CharSequence bigText, CharSequence subText) {
        String source = sourceFor(packageName, appLabel);
        if (source == null) return null;

        String combined = join(title, text, bigText, subText);
        boolean paymentApp = "微信支付".equals(source) || "支付宝".equals(source);
        boolean expenseSignal = paymentApp ? containsAny(combined, SUCCESS_WORDS) :
                containsAny(combined, BANK_OUTGOING_WORDS);
        if (!expenseSignal || containsAny(combined, REJECT_WORDS)) return null;

        for (Pattern pattern : AMOUNT_PATTERNS) {
            Matcher matcher = pattern.matcher(combined);
            if (matcher.find()) {
                try {
                    long cents = new BigDecimal(matcher.group(1))
                            .setScale(2, RoundingMode.HALF_UP)
                            .movePointRight(2)
                            .longValueExact();
                    if (cents <= 0 || cents > 100_000_000_00L) return null;
                    return new Result(cents, source, merchantFrom(title, text), combined);
                } catch (ArithmeticException | NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    static String sourceFor(String packageName, String appLabel) {
        if (packageName == null) return null;
        String normalized = packageName.toLowerCase(Locale.ROOT);
        if (normalized.equals("com.tencent.mm")) return "微信支付";
        if (normalized.equals("com.eg.android.alipaygphone")) return "支付宝";
        String label = appLabel == null ? "" : appLabel.trim();
        String lowerLabel = label.toLowerCase(Locale.ROOT);
        if (label.contains("银行") || label.contains("信用卡") || lowerLabel.contains("bank")) {
            return label.isEmpty() ? "银行 App" : label;
        }
        return null;
    }

    private static boolean containsAny(String text, String[] words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }

    private static String join(CharSequence... values) {
        StringBuilder result = new StringBuilder();
        for (CharSequence value : values) {
            if (value != null && value.length() > 0) result.append(value).append(' ');
        }
        return result.toString().trim();
    }

    private static String merchantFrom(CharSequence title, CharSequence text) {
        String candidate = text == null ? "" : text.toString().trim();
        if (candidate.length() > 0 && candidate.length() <= 40 && !candidate.matches(".*[¥￥].*")) {
            return candidate;
        }
        candidate = title == null ? "" : title.toString().trim();
        if (candidate.equals("微信支付") || candidate.equals("支付宝") || candidate.length() > 40) return "";
        return candidate;
    }

    public static final class Result {
        public final long amountCents;
        public final String source;
        public final String merchant;
        public final String rawText;

        Result(long amountCents, String source, String merchant, String rawText) {
            this.amountCents = amountCents;
            this.source = source;
            this.merchant = merchant;
            this.rawText = rawText;
        }
    }
}
