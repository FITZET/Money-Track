package com.moneytrack.app;

public final class PageFit {
    private PageFit() {}

    public static float[] calculate(float sourceWidth, float sourceHeight,
                                    float pageWidth, float pageHeight, float margin) {
        if (sourceWidth <= 0 || sourceHeight <= 0) throw new IllegalArgumentException("Invalid source size");
        float availableWidth = pageWidth - margin * 2;
        float availableHeight = pageHeight - margin * 2;
        float scale = Math.min(availableWidth / sourceWidth, availableHeight / sourceHeight);
        float width = sourceWidth * scale;
        float height = sourceHeight * scale;
        float left = (pageWidth - width) / 2f;
        float top = (pageHeight - height) / 2f;
        return new float[]{left, top, left + width, top + height};
    }
}
