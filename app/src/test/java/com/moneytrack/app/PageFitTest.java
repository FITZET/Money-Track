package com.moneytrack.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PageFitTest {
    @Test public void landscapeImageFitsWidthAndCentersVertically() {
        float[] rect = PageFit.calculate(1600, 900, 595, 842, 24);
        assertEquals(24f, rect[0], 0.01f);
        assertEquals(571f, rect[2], 0.01f);
        assertEquals((842f - 547f * 900f / 1600f) / 2f, rect[1], 0.01f);
    }

    @Test public void portraitImageFitsHeightAndCentersHorizontally() {
        float[] rect = PageFit.calculate(900, 1600, 595, 842, 24);
        assertEquals(24f, rect[1], 0.01f);
        assertEquals(818f, rect[3], 0.01f);
        assertEquals((595f - 794f * 900f / 1600f) / 2f, rect[0], 0.01f);
    }
}
