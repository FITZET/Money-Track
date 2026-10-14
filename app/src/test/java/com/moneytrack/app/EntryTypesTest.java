package com.moneytrack.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EntryTypesTest {
    @Test public void refundIsAvailableAsAnIncomeCategory() {
        assertTrue(EntryTypes.fixedCategories(EntryTypes.INCOME).contains("退款"));
        assertTrue(EntryTypes.fixedCategories(EntryTypes.REFUND).contains("退款"));
    }

    @Test public void incomeCategoryDeterminesStoredAccountingType() {
        assertEquals(EntryTypes.REFUND, EntryTypes.typeForIncomeCategory("退款"));
        assertEquals(EntryTypes.INCOME, EntryTypes.typeForIncomeCategory("工资"));
    }

    @Test public void refundUsesTheCombinedIncomeInputLabel() {
        assertEquals("收入", EntryTypes.inputLabel(EntryTypes.REFUND));
    }
}
