package com.example.guitartuner.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.example.guitartuner.models.ChordShape;
import com.example.guitartuner.utils.GuitarNoteUtils;

/**
 * Аккордовая диаграмма, как в песенниках: струны вертикально (слева 6-я, толстая),
 * 5 ладов, над порожком — "o" (открытая) и "×" (не звучит), точки с номерами пальцев,
 * баррэ. Внизу — какие ноты звучат на каждой струне.
 *
 * В режиме редактирования (конструктор) нажатие на клетку ставит/убирает палец,
 * нажатие над порожком переключает "открытая ↔ не звучит".
 */
public class ChordDiagramView extends View {

    public interface OnShapeChangeListener {
        void onShapeChanged(int[] frets);
    }

    public static final int VISIBLE_FRETS = 5;
    private static final int STRINGS = 6;
    private static final int NEON = Color.parseColor("#FF8A1F");

    private final float density;
    private final Paint boardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fretPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fingerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint notePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintCellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    // лады от 6-й струны к 1-й; ChordShape.MUTED — не звучит
    private int[] frets = {ChordShape.MUTED, ChordShape.MUTED, ChordShape.MUTED,
            ChordShape.MUTED, ChordShape.MUTED, ChordShape.MUTED};
    private int[] fingers = new int[STRINGS];
    private int baseFret = 1;
    private boolean editable = false;
    // 0..1 — подсветка при верно сыгранном аккорде
    private float highlight = 0f;
    private OnShapeChangeListener listener;

    // геометрия последней отрисовки — для нажатий
    private float gridLeft;
    private float gridTop;
    private float stringGap;
    private float fretGap;

    public ChordDiagramView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;

        boardPaint.setColor(Color.parseColor("#14110F"));
        stringPaint.setColor(Color.parseColor("#9AA3AF"));
        fretPaint.setColor(Color.parseColor("#4A505B"));
        fretPaint.setStrokeWidth(dp(2));
        nutPaint.setColor(Color.parseColor("#E8E2D6"));
        nutPaint.setStrokeWidth(dp(6));
        dotPaint.setColor(NEON);
        fingerTextPaint.setColor(Color.parseColor("#1C0B00"));
        fingerTextPaint.setTextAlign(Paint.Align.CENTER);
        fingerTextPaint.setFakeBoldText(true);
        markerPaint.setColor(Color.parseColor("#FFB15C"));
        markerPaint.setStyle(Paint.Style.STROKE);
        markerPaint.setStrokeWidth(dp(2));
        markerPaint.setStrokeCap(Paint.Cap.ROUND);
        labelPaint.setColor(Color.parseColor("#9AA4B2"));
        labelPaint.setTextSize(dp(13));
        notePaint.setColor(Color.parseColor("#FFCF99"));
        notePaint.setTextAlign(Paint.Align.CENTER);
        notePaint.setTextSize(dp(12));
        notePaint.setFakeBoldText(true);
        hintCellPaint.setColor(Color.argb(28, 255, 138, 31));
    }

    private float dp(float value) {
        return value * density;
    }

    /** Показать аппликатуру; позиция грифа подбирается так, чтобы все прижатые лады влезли. */
    public void setShape(ChordShape shape) {
        frets = shape.frets();
        fingers = shape.fingers();
        int lowest = shape.lowestFret();
        int highest = shape.highestFret();
        baseFret = highest <= VISIBLE_FRETS ? 1 : Math.max(1, lowest);
        invalidate();
    }

    public int[] getFrets() {
        return frets.clone();
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
        if (editable) fingers = new int[STRINGS];
        invalidate();
    }

    /** С какого лада начинается диаграмма (для конструктора: позиция на грифе). */
    public void setBaseFret(int baseFret) {
        this.baseFret = Math.max(1, baseFret);
        invalidate();
    }

    public int getBaseFret() {
        return baseFret;
    }

    public void clear() {
        for (int i = 0; i < STRINGS; i++) {
            frets[i] = ChordShape.MUTED;
            fingers[i] = 0;
        }
        invalidate();
        notifyChanged();
    }

    public void setOnShapeChangeListener(OnShapeChangeListener listener) {
        this.listener = listener;
    }

    /** Подсветить (аккорд сыгран верно): яркость 0..1, гаснет сама. */
    public void flash() {
        highlight = 1f;
        postOnAnimation(new Runnable() {
            @Override
            public void run() {
                highlight = Math.max(0f, highlight - 0.04f);
                invalidate();
                if (highlight > 0) postOnAnimation(this);
            }
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w == 0 || h == 0) return;

        float markerArea = dp(30);
        float notesArea = dp(24);
        float sideLabel = dp(34);
        float gridWidth = w - sideLabel * 2;
        float gridHeight = h - markerArea - notesArea - dp(8);
        stringGap = gridWidth / (STRINGS - 1);
        fretGap = gridHeight / VISIBLE_FRETS;
        gridLeft = sideLabel;
        gridTop = markerArea;
        float gridRight = gridLeft + gridWidth;
        float gridBottom = gridTop + gridHeight;

        // тёмный гриф
        rect.set(gridLeft - dp(10), gridTop, gridRight + dp(10), gridBottom);
        canvas.drawRoundRect(rect, dp(8), dp(8), boardPaint);

        // в конструкторе подсвечиваем клетки, куда можно нажать
        if (editable) {
            for (int f = 0; f < VISIBLE_FRETS; f++) {
                for (int s = 0; s < STRINGS; s++) {
                    float cx = gridLeft + s * stringGap;
                    float cy = gridTop + (f + 0.5f) * fretGap;
                    canvas.drawCircle(cx, cy, Math.min(stringGap, fretGap) * 0.18f, hintCellPaint);
                }
            }
        }

        // лады и порожек
        for (int f = 0; f <= VISIBLE_FRETS; f++) {
            float y = gridTop + f * fretGap;
            canvas.drawLine(gridLeft, y, gridRight, y, fretPaint);
        }
        if (baseFret == 1) {
            canvas.drawLine(gridLeft - dp(2), gridTop, gridRight + dp(2), gridTop, nutPaint);
        } else {
            labelPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(baseFret + " лад", gridLeft - dp(12), gridTop + fretGap * 0.6f, labelPaint);
        }

        // струны: слева толстая
        for (int s = 0; s < STRINGS; s++) {
            float x = gridLeft + s * stringGap;
            stringPaint.setStrokeWidth(dp(2.6f - s * 0.3f));
            canvas.drawLine(x, gridTop, x, gridBottom, stringPaint);
        }

        float dotRadius = Math.min(stringGap, fretGap) * 0.34f;
        fingerTextPaint.setTextSize(dotRadius * 1.15f);

        drawBarre(canvas, dotRadius);

        for (int s = 0; s < STRINGS; s++) {
            float x = gridLeft + s * stringGap;
            int fret = frets[s];
            float markerY = gridTop - dp(14);
            float m = dp(6);

            if (fret == ChordShape.MUTED) {
                canvas.drawLine(x - m, markerY - m, x + m, markerY + m, markerPaint);
                canvas.drawLine(x - m, markerY + m, x + m, markerY - m, markerPaint);
            } else if (fret == 0) {
                canvas.drawCircle(x, markerY, m, markerPaint);
            } else {
                int row = fret - baseFret;
                if (row >= 0 && row < VISIBLE_FRETS) {
                    float y = gridTop + (row + 0.5f) * fretGap;
                    drawDot(canvas, x, y, dotRadius, fingers[s]);
                }
            }

            // звучащая нота струны
            String note = fret == ChordShape.MUTED ? "—"
                    : GuitarNoteUtils.pitchClassName(GuitarNoteUtils.getMidi(6 - s, fret));
            notePaint.setAlpha(fret == ChordShape.MUTED ? 90 : 255);
            canvas.drawText(note, x, gridBottom + dp(20), notePaint);
        }
    }

    // баррэ: один палец (обычно указательный) прижимает несколько струн на одном ладу
    private void drawBarre(Canvas canvas, float dotRadius) {
        for (int finger = 1; finger <= 4; finger++) {
            int first = -1;
            int last = -1;
            int fret = -1;
            for (int s = 0; s < STRINGS; s++) {
                if (fingers[s] == finger && frets[s] > 0) {
                    if (first < 0) {
                        first = s;
                        fret = frets[s];
                    }
                    if (frets[s] == fret) last = s;
                }
            }
            if (first < 0 || last <= first) continue;
            int row = fret - baseFret;
            if (row < 0 || row >= VISIBLE_FRETS) continue;
            float y = gridTop + (row + 0.5f) * fretGap;
            rect.set(gridLeft + first * stringGap - dotRadius, y - dotRadius * 0.75f,
                    gridLeft + last * stringGap + dotRadius, y + dotRadius * 0.75f);
            dotPaint.setAlpha(200);
            canvas.drawRoundRect(rect, dotRadius, dotRadius, dotPaint);
            dotPaint.setAlpha(255);
        }
    }

    private void drawDot(Canvas canvas, float x, float y, float radius, int finger) {
        float glow = 0.35f + 0.65f * highlight;
        glowPaint.setShader(new RadialGradient(x, y, radius * 2.2f,
                new int[]{Color.argb((int) (150 * glow), 255, 138, 31), Color.argb(0, 255, 138, 31)},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, radius * 2.2f, glowPaint);
        dotPaint.setColor(highlight > 0.05f ? blend(NEON, Color.parseColor("#3DFF8E"), highlight) : NEON);
        canvas.drawCircle(x, y, radius, dotPaint);
        dotPaint.setColor(NEON);
        if (finger > 0) {
            canvas.drawText(String.valueOf(finger), x, y + fingerTextPaint.getTextSize() / 3f, fingerTextPaint);
        }
    }

    private static int blend(int a, int b, float t) {
        return Color.rgb(
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!editable) return super.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (event.getAction() != MotionEvent.ACTION_UP) return true;

        int s = Math.round((event.getX() - gridLeft) / stringGap);
        if (s < 0 || s >= STRINGS) return true;

        if (event.getY() < gridTop) {
            // над порожком: открытая ↔ не звучит
            frets[s] = frets[s] == 0 ? ChordShape.MUTED : 0;
        } else {
            int row = (int) ((event.getY() - gridTop) / fretGap);
            if (row < 0 || row >= VISIBLE_FRETS) return true;
            int fret = baseFret + row;
            // повторное нажатие на тот же лад — палец убирается, струна открытая
            frets[s] = frets[s] == fret ? 0 : fret;
        }
        fingers[s] = 0;
        invalidate();
        performClick();
        notifyChanged();
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void notifyChanged() {
        if (listener != null) listener.onShapeChanged(frets.clone());
    }
}
