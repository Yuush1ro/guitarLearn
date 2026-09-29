package com.example.guitartuner.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.example.guitartuner.models.TabNote;
import com.example.guitartuner.tuner.NoteUtils;
import com.example.guitartuner.utils.GuitarNoteUtils;

/**
 * Гриф гитары (открытые струны + 12 ладов) с точками нот.
 *
 * Два способа подсветки:
 * - по названию ноты (тренировка): все места, где можно сыграть целевую ноту
 *   (крупные оранжевые точки со свечением), и остальные ноты гаммы (мелкие точки);
 * - по конкретной позиции (уроки): текущая нота урока со свечением и контур следующей.
 *
 * В режиме участка нажатия на гриф задают видимый диапазон ладов:
 * первое — начало, второе — конец.
 */
public class FretboardView extends View {

    public interface OnRegionChangeListener {
        /** pending = true, если выбрано только начало участка и ждём второе нажатие. */
        void onRegionChanged(int startFret, int endFret, boolean pending);
    }

    public static final int FRET_COUNT = 12;
    private static final int STRING_COUNT = 6;
    private static final int[] INLAY_FRETS = {3, 5, 7, 9};

    private final float density;

    private final Paint boardPaint = new Paint();
    private final Paint fretPaint = new Paint();
    private final Paint nutPaint = new Paint();
    private final Paint stringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint();
    private final Paint regionBorderPaint = new Paint();
    private final Paint pendingPaint = new Paint();
    private final Paint targetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scaleNotePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint targetTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scaleTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fretNumberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nextMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nextMarkerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final long PULSE_PERIOD_MS = 1200;

    // позиции для уроков: текущая и следующая нота; null — нет
    private TabNote lessonCurrent;
    private TabNote lessonNext;

    private boolean pulseScheduled = false;
    private final Runnable pulseFrame = new Runnable() {
        @Override
        public void run() {
            pulseScheduled = false;
            invalidate();
            schedulePulse();
        }
    };

    private int targetPitchClass = -1;
    private final boolean[] scalePitchClasses = new boolean[12];
    private boolean showNotes = true;

    private boolean regionMode = false;
    private int regionStart = 0;
    private int regionEnd = 4;
    private int pendingStart = -1;

    private OnRegionChangeListener regionListener;

    public FretboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;

        boardPaint.setColor(Color.parseColor("#5D4037"));

        fretPaint.setColor(Color.parseColor("#BDBDBD"));
        fretPaint.setStrokeWidth(dp(2));

        nutPaint.setColor(Color.parseColor("#EEEEEE"));
        nutPaint.setStrokeWidth(dp(5));

        stringPaint.setColor(Color.parseColor("#E0E0E0"));

        inlayPaint.setColor(Color.parseColor("#8D6E63"));

        dimPaint.setColor(Color.argb(150, 0, 0, 0));

        regionBorderPaint.setColor(Color.parseColor("#FFC107"));
        regionBorderPaint.setStyle(Paint.Style.STROKE);
        regionBorderPaint.setStrokeWidth(dp(2));

        pendingPaint.setColor(Color.argb(90, 255, 193, 7));

        targetPaint.setColor(Color.parseColor("#FF9800"));

        scaleNotePaint.setColor(Color.argb(170, 144, 202, 249));

        targetTextPaint.setColor(Color.BLACK);
        targetTextPaint.setTextAlign(Paint.Align.CENTER);
        targetTextPaint.setFakeBoldText(true);

        scaleTextPaint.setColor(Color.parseColor("#0D47A1"));
        scaleTextPaint.setTextAlign(Paint.Align.CENTER);

        fretNumberPaint.setColor(Color.GRAY);
        fretNumberPaint.setTextAlign(Paint.Align.CENTER);
        fretNumberPaint.setTextSize(dp(11));

        glowPaint.setColor(Color.parseColor("#FFB74D"));

        nextMarkerPaint.setColor(Color.parseColor("#90CAF9"));
        nextMarkerPaint.setStyle(Paint.Style.STROKE);
        nextMarkerPaint.setStrokeWidth(dp(2));

        nextMarkerTextPaint.setColor(Color.parseColor("#E3F2FD"));
        nextMarkerTextPaint.setTextAlign(Paint.Align.CENTER);
        nextMarkerTextPaint.setFakeBoldText(true);
    }

    /** Позиции для уроков: current светится, next показан контуром. null — не показывать. */
    public void setLessonMarkers(TabNote current, TabNote next) {
        lessonCurrent = current;
        lessonNext = next;
        invalidate();
        schedulePulse();
    }

    private boolean hasGlow() {
        return targetPitchClass >= 0 || lessonCurrent != null;
    }

    private void schedulePulse() {
        if (pulseScheduled || !hasGlow() || !isAttachedToWindow()) return;
        pulseScheduled = true;
        postOnAnimationDelayed(pulseFrame, 32);
    }

    // 0..1..0 с периодом PULSE_PERIOD_MS
    private float pulse() {
        float phase = (SystemClock.uptimeMillis() % PULSE_PERIOD_MS) / (float) PULSE_PERIOD_MS;
        return phase < 0.5f ? phase * 2f : (1f - phase) * 2f;
    }

    private void drawGlow(Canvas canvas, float x, float y, float radius, float pulse) {
        glowPaint.setAlpha((int) (120 * (1f - 0.6f * pulse)));
        canvas.drawCircle(x, y, radius * (1.3f + 0.25f * pulse), glowPaint);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        schedulePulse();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(pulseFrame);
        pulseScheduled = false;
    }

    private float dp(float value) {
        return value * density;
    }

    /** Целевая нота 0..11 (0 = C) или -1, чтобы ничего не выделять. */
    public void setTargetPitchClass(int pitchClass) {
        targetPitchClass = pitchClass;
        invalidate();
        schedulePulse();
    }

    public void setScalePitchClasses(int[] pitchClasses) {
        java.util.Arrays.fill(scalePitchClasses, false);
        for (int pc : pitchClasses) scalePitchClasses[pc] = true;
        invalidate();
    }

    public void setShowNotes(boolean show) {
        showNotes = show;
        invalidate();
    }

    public void setRegionMode(boolean enabled) {
        regionMode = enabled;
        pendingStart = -1;
        invalidate();
    }

    public void setRegion(int startFret, int endFret) {
        regionStart = clampFret(Math.min(startFret, endFret));
        regionEnd = clampFret(Math.max(startFret, endFret));
        pendingStart = -1;
        invalidate();
    }

    public int getRegionStart() {
        return regionStart;
    }

    public int getRegionEnd() {
        return regionEnd;
    }

    public void setOnRegionChangeListener(OnRegionChangeListener listener) {
        regionListener = listener;
    }

    private static int clampFret(int fret) {
        return Math.max(0, Math.min(FRET_COUNT, fret));
    }

    private boolean isFretVisible(int fret) {
        return !regionMode || (fret >= regionStart && fret <= regionEnd);
    }

    // колонка 0 — открытые струны (слева от порожка), колонки 1..12 — лады
    private float cellWidth() {
        return getWidth() / (float) (FRET_COUNT + 1);
    }

    private float fretCenterX(int fret) {
        return (fret + 0.5f) * cellWidth();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        if (width == 0 || height == 0) return;

        float cellW = cellWidth();
        float numbersHeight = dp(18);
        float top = dp(14);
        float bottom = height - numbersHeight - dp(14);
        float stringSpacing = (bottom - top) / (STRING_COUNT - 1);
        float boardTop = top - dp(8);
        float boardBottom = bottom + dp(8);

        // дерево грифа
        canvas.drawRect(cellW, boardTop, width, boardBottom, boardPaint);

        // метки ладов
        float inlayRadius = Math.min(cellW, stringSpacing) * 0.18f;
        float midY = (top + bottom) / 2f;
        for (int fret : INLAY_FRETS) {
            canvas.drawCircle(fretCenterX(fret), midY, inlayRadius, inlayPaint);
        }
        canvas.drawCircle(fretCenterX(12), top + stringSpacing * 1.5f, inlayRadius, inlayPaint);
        canvas.drawCircle(fretCenterX(12), top + stringSpacing * 3.5f, inlayRadius, inlayPaint);

        // лады и порожек
        for (int fret = 1; fret <= FRET_COUNT; fret++) {
            float x = (fret + 1) * cellW;
            canvas.drawLine(x, boardTop, x, boardBottom, fretPaint);
        }
        canvas.drawLine(cellW, boardTop, cellW, boardBottom, nutPaint);

        // струны: 1-я (тонкая, высокая E) сверху — как в табулатуре
        for (int s = 0; s < STRING_COUNT; s++) {
            float y = top + s * stringSpacing;
            stringPaint.setStrokeWidth(dp(1f + s * 0.4f));
            canvas.drawLine(0, y, width, y, stringPaint);
        }

        // затемнение ладов вне выбранного участка
        if (regionMode) {
            for (int fret = 0; fret <= FRET_COUNT; fret++) {
                if (!isFretVisible(fret)) {
                    canvas.drawRect(fret * cellW, boardTop, (fret + 1) * cellW, boardBottom, dimPaint);
                }
            }
            if (pendingStart >= 0) {
                canvas.drawRect(pendingStart * cellW, boardTop,
                        (pendingStart + 1) * cellW, boardBottom, pendingPaint);
            }
            canvas.drawRect(regionStart * cellW, boardTop,
                    (regionEnd + 1) * cellW, boardBottom, regionBorderPaint);
        }

        float targetRadius = Math.min(cellW, stringSpacing) * 0.42f;
        float pulse = pulse();

        // точки нот
        if (showNotes) {
            float scaleRadius = Math.min(cellW, stringSpacing) * 0.30f;
            targetTextPaint.setTextSize(targetRadius * 1.1f);
            scaleTextPaint.setTextSize(scaleRadius * 1.0f);

            for (int s = 0; s < STRING_COUNT; s++) {
                float y = top + s * stringSpacing;
                for (int fret = 0; fret <= FRET_COUNT; fret++) {
                    if (!isFretVisible(fret)) continue;

                    int pc = GuitarNoteUtils.getMidi(s + 1, fret) % 12;
                    float x = fretCenterX(fret);
                    String name = NoteUtils.NOTE_NAMES[pc];

                    if (pc == targetPitchClass) {
                        drawGlow(canvas, x, y, targetRadius, pulse);
                        canvas.drawCircle(x, y, targetRadius, targetPaint);
                        canvas.drawText(name, x, y + targetTextPaint.getTextSize() / 3f, targetTextPaint);
                    } else if (scalePitchClasses[pc]) {
                        canvas.drawCircle(x, y, scaleRadius, scaleNotePaint);
                        canvas.drawText(name, x, y + scaleTextPaint.getTextSize() / 3f, scaleTextPaint);
                    }
                }
            }
        }

        // позиции урока: следующая нота контуром, текущая — со свечением
        if (lessonNext != null) {
            float x = fretCenterX(lessonNext.getFret());
            float y = top + (lessonNext.getStringNumber() - 1) * stringSpacing;
            float r = targetRadius * 0.85f;
            canvas.drawCircle(x, y, r, nextMarkerPaint);
            nextMarkerTextPaint.setTextSize(r * 1.0f);
            canvas.drawText(noteName(lessonNext), x, y + nextMarkerTextPaint.getTextSize() / 3f,
                    nextMarkerTextPaint);
        }
        if (lessonCurrent != null) {
            float x = fretCenterX(lessonCurrent.getFret());
            float y = top + (lessonCurrent.getStringNumber() - 1) * stringSpacing;
            drawGlow(canvas, x, y, targetRadius, pulse);
            canvas.drawCircle(x, y, targetRadius, targetPaint);
            targetTextPaint.setTextSize(targetRadius * 1.1f);
            canvas.drawText(noteName(lessonCurrent), x, y + targetTextPaint.getTextSize() / 3f,
                    targetTextPaint);
        }

        // номера ладов
        float numbersY = height - dp(4);
        for (int fret = 0; fret <= FRET_COUNT; fret++) {
            canvas.drawText(String.valueOf(fret), fretCenterX(fret), numbersY, fretNumberPaint);
        }
    }

    private static String noteName(TabNote note) {
        int pc = GuitarNoteUtils.getMidi(note.getStringNumber(), note.getFret()) % 12;
        return NoteUtils.NOTE_NAMES[pc];
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!regionMode) return super.onTouchEvent(event);

        if (event.getAction() == MotionEvent.ACTION_UP) {
            int fret = clampFret((int) (event.getX() / cellWidth()));
            onFretTapped(fret);
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void onFretTapped(int fret) {
        if (pendingStart < 0) {
            // первое нажатие: начало участка
            pendingStart = fret;
            regionStart = fret;
            regionEnd = fret;
        } else {
            // второе нажатие: конец участка
            regionStart = Math.min(pendingStart, fret);
            regionEnd = Math.max(pendingStart, fret);
            pendingStart = -1;
        }
        invalidate();
        if (regionListener != null) {
            regionListener.onRegionChanged(regionStart, regionEnd, pendingStart >= 0);
        }
    }
}
