package com.example.guitartuner.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.example.guitartuner.audio.AudioEngine;

/**
 * Неоновая визуализация метронома: маятник качается в такт, на каждой доле
 * вспыхивает ореол, снизу — ряд долей такта (акцент, обычная, без звука).
 * Нажатие на долю меняет её тип.
 */
public class MetronomeView extends View {

    public interface OnBeatTapListener {
        void onBeatTapped(int beat);
    }

    private static final int NEON = Color.parseColor("#FF8A1F");
    private static final int NEON_SOFT = Color.parseColor("#FFB15C");
    private static final float MAX_ANGLE_DEG = 28f;

    private final float density;
    private final Paint armPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint armGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint weightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pivotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mutedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int beats = 4;
    private int[] beatTypes = {0, 1, 1, 1};
    private int subdivision = 1;

    private boolean running = false;
    private int currentBeat = -1;
    private int currentSub = 0;
    private double beatPhase = 0;
    // сколько долей прошло с запуска — чтобы маятник качался туда-обратно без рывков
    private long beatCounter = 0;

    private OnBeatTapListener tapListener;

    public MetronomeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;

        armPaint.setColor(NEON);
        armPaint.setStrokeWidth(dp(4));
        armPaint.setStrokeCap(Paint.Cap.ROUND);

        armGlowPaint.setColor(NEON);
        armGlowPaint.setAlpha(60);
        armGlowPaint.setStrokeWidth(dp(14));
        armGlowPaint.setStrokeCap(Paint.Cap.ROUND);

        weightPaint.setColor(NEON_SOFT);
        pivotPaint.setColor(Color.parseColor("#2A2F3A"));

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(dp(2));
        arcPaint.setColor(Color.parseColor("#2A2F3A"));

        dotPaint.setColor(NEON);
        mutedPaint.setStyle(Paint.Style.STROKE);
        mutedPaint.setStrokeWidth(dp(2));
        mutedPaint.setColor(Color.parseColor("#5C6370"));
        subPaint.setColor(NEON_SOFT);
    }

    private float dp(float value) {
        return value * density;
    }

    public void setBeats(int beats, int[] beatTypes, int subdivision) {
        this.beats = beats;
        this.beatTypes = beatTypes;
        this.subdivision = subdivision;
        invalidate();
    }

    public void setOnBeatTapListener(OnBeatTapListener listener) {
        tapListener = listener;
    }

    /** Обновить по текущему звуку; state == null — метроном стоит. */
    public void setState(AudioEngine.MetronomeState state) {
        if (state == null) {
            running = false;
            currentBeat = -1;
            beatCounter = 0;
        } else {
            // новая доля: сменился номер или фаза началась заново (в размере 1/4 номер не меняется)
            if (state.beat != currentBeat || state.beatPhase < beatPhase - 0.5) beatCounter++;
            running = true;
            currentBeat = state.beat;
            currentSub = state.subdivision;
            beatPhase = state.beatPhase;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w == 0 || h == 0) return;

        float dotsArea = dp(64);
        float pivotX = w / 2f;
        float pivotY = h - dotsArea - dp(12);
        float armLength = Math.min(pivotY - dp(16), w * 0.42f);

        // вспышка на долю: ярче всего в момент щелчка, затем гаснет
        if (running) {
            float flash = (float) Math.pow(1 - beatPhase, 2);
            boolean accent = currentBeat >= 0 && currentBeat < beatTypes.length && beatTypes[currentBeat] == 0;
            float radius = armLength * (accent ? 1.05f : 0.85f);
            int alpha = (int) ((accent ? 150 : 95) * flash);
            if (alpha > 2) {
                haloPaint.setShader(new RadialGradient(pivotX, pivotY - armLength * 0.55f, radius,
                        new int[]{Color.argb(alpha, 255, 138, 31), Color.argb(0, 255, 138, 31)},
                        null, Shader.TileMode.CLAMP));
                canvas.drawCircle(pivotX, pivotY - armLength * 0.55f, radius, haloPaint);
            }
        }

        // шкала маятника
        float arcR = armLength;
        canvas.drawArc(pivotX - arcR, pivotY - arcR, pivotX + arcR, pivotY + arcR,
                -90 - MAX_ANGLE_DEG - 4, (MAX_ANGLE_DEG + 4) * 2, false, arcPaint);

        // маятник: в момент щелчка — в крайней точке, между щелчками — плавно через центр
        double angle = 0;
        if (running) {
            double side = (beatCounter % 2 == 0) ? 1 : -1;
            angle = side * MAX_ANGLE_DEG * Math.cos(Math.PI * beatPhase);
        }
        double rad = Math.toRadians(angle - 90);
        float tipX = (float) (pivotX + armLength * Math.cos(rad));
        float tipY = (float) (pivotY + armLength * Math.sin(rad));
        canvas.drawLine(pivotX, pivotY, tipX, tipY, armGlowPaint);
        canvas.drawLine(pivotX, pivotY, tipX, tipY, armPaint);

        float weightX = (float) (pivotX + armLength * 0.62f * Math.cos(rad));
        float weightY = (float) (pivotY + armLength * 0.62f * Math.sin(rad));
        canvas.drawRoundRect(weightX - dp(11), weightY - dp(8), weightX + dp(11), weightY + dp(8),
                dp(4), dp(4), weightPaint);
        canvas.drawCircle(pivotX, pivotY, dp(9), pivotPaint);
        canvas.drawCircle(pivotX, pivotY, dp(4), armPaint);

        drawBeatDots(canvas, w, h - dotsArea / 2f - dp(4));
    }

    private void drawBeatDots(Canvas canvas, float w, float cy) {
        float slot = Math.min(dp(44), (w - dp(16)) / beats);
        float startX = (w - slot * beats) / 2f + slot / 2f;
        float baseRadius = Math.min(slot * 0.3f, dp(13));

        for (int b = 0; b < beats; b++) {
            float cx = startX + b * slot;
            int type = b < beatTypes.length ? beatTypes[b] : 1;
            boolean active = running && b == currentBeat;
            float r = type == 0 ? baseRadius : baseRadius * 0.72f;

            if (active) {
                float glow = (float) (1 - beatPhase);
                dotGlowPaint.setShader(new RadialGradient(cx, cy, r * 2.6f,
                        new int[]{Color.argb((int) (170 * glow), 255, 138, 31), Color.argb(0, 255, 138, 31)},
                        null, Shader.TileMode.CLAMP));
                canvas.drawCircle(cx, cy, r * 2.6f, dotGlowPaint);
                r *= 1 + 0.25f * glow;
            }

            if (type == 2) {
                canvas.drawCircle(cx, cy, r, mutedPaint);
            } else {
                dotPaint.setAlpha(active ? 255 : type == 0 ? 200 : 120);
                canvas.drawCircle(cx, cy, r, dotPaint);
            }

            // дробление доли — маленькие точки под текущей долей
            if (active && subdivision > 1) {
                float subSpacing = dp(7);
                float subStart = cx - subSpacing * (subdivision - 1) / 2f;
                for (int s = 0; s < subdivision; s++) {
                    subPaint.setAlpha(s <= currentSub ? 255 : 70);
                    canvas.drawCircle(subStart + s * subSpacing, cy + baseRadius + dp(10), dp(2.5f), subPaint);
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (event.getAction() != MotionEvent.ACTION_UP) return super.onTouchEvent(event);

        float h = getHeight();
        float dotsTop = h - dp(64) - dp(8);
        if (event.getY() < dotsTop) return true;

        float w = getWidth();
        float slot = Math.min(dp(44), (w - dp(16)) / beats);
        float startX = (w - slot * beats) / 2f;
        int beat = (int) ((event.getX() - startX) / slot);
        if (beat >= 0 && beat < beats && tapListener != null) {
            tapListener.onBeatTapped(beat);
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
