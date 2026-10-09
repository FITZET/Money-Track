package com.moneytrack.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class Ui {
    static final int INK = Color.rgb(23, 33, 27);
    static final int MUTED = Color.rgb(103, 111, 105);
    static final int PAPER = Color.rgb(245, 243, 236);
    static final int CARD = Color.WHITE;
    static final int GREEN = Color.rgb(36, 107, 74);
    static final int PALE_GREEN = Color.rgb(228, 240, 231);
    static final int AMBER = Color.rgb(180, 113, 25);
    static final int PALE_AMBER = Color.rgb(252, 239, 216);
    static final int BORDER = Color.rgb(224, 224, 216);

    private Ui() {}

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable background(Context context, int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static GradientDrawable outlined(Context context, int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = background(context, color, radiusDp);
        drawable.setStroke(dp(context, 1), strokeColor);
        return drawable;
    }

    static TextView text(Context context, String value, float sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    static void pad(View view, int horizontal, int vertical) {
        view.setPadding(dp(view.getContext(), horizontal), dp(view.getContext(), vertical),
                dp(view.getContext(), horizontal), dp(view.getContext(), vertical));
    }

    static String money(long cents) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.CHINA);
        return format.format(cents / 100.0);
    }

    static String date(long millis) {
        return new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(new Date(millis));
    }
}
