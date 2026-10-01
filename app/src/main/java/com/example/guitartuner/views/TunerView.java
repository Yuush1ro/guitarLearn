package com.example.guitartuner.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Неоновая шкала тюнера: стрелка показывает отклонение в центах (−50…+50).
 * В зоне ±5 центов (нота настроена) стрелка и зона светятся зелёным.
 */
public class TunerView extends View {

    private static final int NEON = Color.parseColor("#FF8A1F");
    private static final int IN_TUNE = Color.parseColor("#3DFF8E");
    private static final float MAX_CENTS = 50f;
    private static final float MAX_ANGLE = 60f;
    private static final float IN_TUNE_CENTS = 5f;

    private final float density;
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needleGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pivotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private float cents = 0;

    public TunerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setStrokeWidth(dp(10));
        trackPaint.setColor(Color.parseColor("#1F2530"));

        zonePaint.setStyle(Paint.Style.STROKE);
        zonePaint.setStrokeWidth(dp(10));
        zonePaint.setColor(IN_TUNE);

        tickPaint.setColor(Color.parseColor("#4A5363"));
        tickPaint.setStrokeWidth(dp(2));
        tickPaint.setStrokeCap(Paint.Cap.ROUND);

        needlePaint.setStrokeWidth(dp(4));
        needlePaint.setStrokeCap(Paint.Cap.ROUND);
        needleGlowPaint.setStrokeWidth(dp(14));
        needleGlowPaint.setStrokeCap(Paint.Cap.ROUND);

        pivotPaint.setColor(Color.parseColor("#2A2F3A"));

        labelPaint.setColor(Color.parseColor("#9AA4B2"));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(dp(12));
    }

    private float dp(float value) {
        return value * density;
    }

    public void setCents(float cents) {
        this.cents = cents;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width == 0 || height == 0) return;

        float centerX = width / 2f;
        float centerY = height * 0.78f;
        float radius = Math.min(width * 0.42f, centerY - dp(24));

        float clamped = Math.max(-MAX_CENTS, Math.min(MAX_CENTS, cents));
        boolean inTune = Math.abs(clamped) <= IN_TUNE_CENTS;
        int needleColor = inTune ? IN_TUNE : NEON;

        // ореол, когда нота настроена
        if (inTune) {
            haloPaint.setShader(new RadialGradient(centerX, centerY - radius * 0.5f, radius,
                    new int[]{Color.argb(70, 61, 255, 142), Color.argb(0, 61, 255, 142)},
                    null, Shader.TileMode.CLAMP));
            canvas.drawCircle(centerX, centerY - radius * 0.5f, radius, haloPaint);
        }

        // шкала
        arcRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
        canvas.drawArc(arcRect, -90 - MAX_ANGLE, MAX_ANGLE * 2, false, trackPaint);
        float zoneAngle = IN_TUNE_CENTS / MAX_CENTS * MAX_ANGLE;
        zonePaint.setAlpha(inTune ? 255 : 110);
        canvas.drawArc(arcRect, -90 - zoneAngle, zoneAngle * 2, false, zonePaint);

        // деления каждые 10 центов
        for (int c = -50; c <= 50; c += 10) {
            double rad = Math.toRadians(c / MAX_CENTS * MAX_ANGLE - 90);
            float inner = radius - dp(c == 0 ? 26 : 18);
            float outer = radius - dp(10);
            canvas.drawLine(
                    (float) (centerX + inner * Math.cos(rad)), (float) (centerY + inner * Math.sin(rad)),
                    (float) (centerX + outer * Math.cos(rad)), (float) (centerY + outer * Math.sin(rad)),
                    tickPaint);
        }
        canvas.drawText("♭", centerX - radius * 0.92f, centerY + dp(4), labelPaint);
        canvas.drawText("♯", centerX + radius * 0.92f, centerY + dp(4), labelPaint);

        // стрелка со свечением
        double rad = Math.toRadians(clamped / MAX_CENTS * MAX_ANGLE - 90);
        float tipX = (float) (centerX + (radius - dp(4)) * Math.cos(rad));
        float tipY = (float) (centerY + (radius - dp(4)) * Math.sin(rad));
        needleGlowPaint.setColor(needleColor);
        needleGlowPaint.setAlpha(70);
        needlePaint.setColor(needleColor);
        canvas.drawLine(centerX, centerY, tipX, tipY, needleGlowPaint);
        canvas.drawLine(centerX, centerY, tipX, tipY, needlePaint);

        canvas.drawCircle(centerX, centerY, dp(10), pivotPaint);
        canvas.drawCircle(centerX, centerY, dp(5), needlePaint);
    }
}
