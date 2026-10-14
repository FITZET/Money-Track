package com.moneytrack.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MoneyInputTest {
    @Test public void acceptsExplicitZero() {
        assertEquals(Long.valueOf(0L), MoneyInput.parseNonNegativeCents("0"));
        assertEquals(Long.valueOf(0L), MoneyInput.parseNonNegativeCents("0.00"));
    }

    @Test public void acceptsAndRoundsPositiveAmounts() {
        assertEquals(Long.valueOf(12345L), MoneyInput.parseNonNegativeCents("123.45"));
        assertEquals(Long.valueOf(12346L), MoneyInput.parseNonNegativeCents("123.456"));
    }

    @Test public void rejectsMissingInvalidAndNegativeAmounts() {
        assertNull(MoneyInput.parseNonNegativeCents(null));
        assertNull(MoneyInput.parseNonNegativeCents("  "));
        assertNull(MoneyInput.parseNonNegativeCents("abc"));
        assertNull(MoneyInput.parseNonNegativeCents("-0.01"));
    }
}
