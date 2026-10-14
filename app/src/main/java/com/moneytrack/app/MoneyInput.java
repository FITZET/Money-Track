package com.moneytrack.app;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class MoneyInput {
    private MoneyInput() {}

    static Long parseNonNegativeCents(String input) {
        if (input == null) return null;
        String normalized = input.trim().replace(",", "");
        if (normalized.isEmpty()) return null;
        try {
            long cents = new BigDecimal(normalized)
                    .setScale(2, RoundingMode.HALF_UP)
                    .movePointRight(2)
                    .longValueExact();
            return cents >= 0 ? cents : null;
        } catch (ArithmeticException | NumberFormatException ignored) {
            return null;
        }
    }
}
