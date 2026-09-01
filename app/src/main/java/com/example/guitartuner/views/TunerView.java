package com.example.guitartuner.views;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

public class TunerView extends View {

    private Paint linePaint;
    private Paint needlePaint;
    private Paint centerPaint;

    private float cents = 0;

    public TunerView(Context context, AttributeSet attrs) {
        super(context, attrs);

        linePaint = new Paint();
        linePaint.setColor(Color.GRAY);
        linePaint.setStrokeWidth(5);
        linePaint.setStyle(Paint.Style.STROKE);

        needlePaint = new Paint();
        needlePaint.setColor(Color.RED);
        needlePaint.setStrokeWidth(8);
        needlePaint.setStrokeCap(Paint.Cap.ROUND);

        centerPaint = new Paint();
        centerPaint.setColor(Color.GREEN);
        centerPaint.setStrokeWidth(6);
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

        float centerX = width / 2f;
        float centerY = height * 0.8f; // 🔥 смещаем вниз (как спидометр)

        float radius = width / 3f;

        // 🔥 дуга (только верх)
        RectF rect = new RectF(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius
        );

        canvas.drawArc(rect, 180, 180, false, linePaint);

        // 🔥 центральная линия (идеальный строй)
        canvas.drawLine(centerX, centerY, centerX, centerY - radius, centerPaint);

        // 🔥 clamp
        float clamped = Math.max(-50f, Math.min(50f, cents));

        // 🔥 ВАЖНО: диапазон -45° .. +45°
        float angle = (clamped / 50f) * 45f;

        // 🔥 перевод в радианы (центр = вверх)
        double rad = Math.toRadians(angle - 90);

        float needleX = (float)(centerX + radius * Math.cos(rad));
        float needleY = (float)(centerY + radius * Math.sin(rad));

        // 🔥 стрелка
        canvas.drawLine(centerX, centerY, needleX, needleY, needlePaint);
    }
}