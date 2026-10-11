package com.moneytrack.app;

import android.content.Context;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

/** A record row that reveals its delete action when swiped to the left. */
public final class SwipeDeleteLayout extends HorizontalScrollView {
    private final LinearLayout strip;
    private final View content;
    private final int actionWidth;
    private final int revealWidth;

    public SwipeDeleteLayout(Context context, View content, Runnable onDelete) {
        super(context);
        this.content = content;
        actionWidth = Ui.dp(context, 82);
        revealWidth = actionWidth + Ui.dp(context, 7);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setFillViewport(false);

        strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.addView(content, new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button delete = new Button(context);
        delete.setText("删除");
        delete.setTextColor(Color.WHITE);
        delete.setTextSize(14);
        delete.setAllCaps(false);
        delete.setBackground(Ui.background(context, Color.rgb(210, 63, 63), 14));
        delete.setOnClickListener(v -> onDelete.run());
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                actionWidth, ViewGroup.LayoutParams.MATCH_PARENT);
        deleteParams.setMargins(Ui.dp(context, 7), 0, 0, 0);
        strip.addView(delete, deleteParams);
        addView(strip, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int viewportWidth = MeasureSpec.getSize(widthMeasureSpec);
        ViewGroup.LayoutParams params = content.getLayoutParams();
        if (viewportWidth > 0 && params.width != viewportWidth) {
            params.width = viewportWidth;
            content.setLayoutParams(params);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = super.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            post(() -> smoothScrollTo(getScrollX() > revealWidth / 3 ? revealWidth : 0, 0));
        }
        return handled;
    }
}
