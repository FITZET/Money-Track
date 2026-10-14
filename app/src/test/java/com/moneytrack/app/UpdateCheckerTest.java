package com.moneytrack.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UpdateCheckerTest {
    @Test
    public void comparesSemanticVersions() {
        assertTrue(UpdateChecker.compareVersions("1.2.0", "1.1.9") > 0);
        assertTrue(UpdateChecker.compareVersions("2.0", "1.9.9") > 0);
        assertEquals(0, UpdateChecker.compareVersions("1.1.6", "1.1.6"));
    }
}
