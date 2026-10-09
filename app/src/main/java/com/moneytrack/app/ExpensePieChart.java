package com.moneytrack.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public final class ExpensePieChart extends View {
    public static final int[] COLORS = {
            0xFF246B4A, 0xFFD08A32, 0xFF5679A6, 0xFF9B6B9E,
            0xFFB65F55, 0xFF69978A, 0xFF8A8065, 0xFF727A87
    };
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private List<CategoryTotal> values = new ArrayList<>();

    public ExpensePieChart(Context context) {
        super(context);
        centerPaint.setColor(Ui.CARD);
    }

    public void setValues(List<CategoryTotal> values) {
        this.values = values == null ? new ArrayList<>() : values;
        invalidate();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(width, Ui.dp(getContext(), 220));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long total = 0L;
        for (CategoryTotal value : values) total += value.amountCents;
        float size = Math.min(getWidth(), getHeight()) - Ui.dp(getContext(), 28);
        float left = (getWidth() - size) / 2f;
        float top = (getHeight() - size) / 2f;
        oval.set(left, top, left + size, top + size);
        if (total <= 0) {
            paint.setColor(Ui.BORDER); canvas.drawArc(oval, 0, 360, true, paint); return;
        }
        float start = -90f;
        for (int i = 0; i < values.size(); i++) {
            float sweep = 360f * values.get(i).amountCents / total;
            paint.setColor(COLORS[i % COLORS.length]);
            canvas.drawArc(oval, start, sweep, true, paint);
            start += sweep;
        }
        float hole = size * 0.53f;
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, hole / 2f, centerPaint);
    }
}
