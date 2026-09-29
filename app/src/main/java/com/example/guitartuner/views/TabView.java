package com.example.guitartuner.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.example.guitartuner.models.TabNote;

import java.util.ArrayList;
import java.util.List;

/**
 * Горизонтальная лента бегущих нот в виде табулатуры.
 *
 * Какую ноту играть сейчас, решает фрагмент через setCurrentIndex().
 * Текущая нота стоит на линии воспроизведения и пульсирует свечением.
 * Ленту можно тянуть пальцем — после отпускания она встаёт на ближайшую ноту
 * и сообщает об этом через OnSeekListener.
 */
public class TabView extends View {

    public interface OnSeekListener {
        /** Пользователь отпустил ленту: играть с ноты index. */
        void onSeek(int index);

        /** Лента тянется: у линии воспроизведения сейчас нота index. */
        default void onSeekPreview(int index) {
        }
    }

    private static final int STRING_COUNT = 6;
    private static final String[] STANDARD_LABELS = {"e", "B", "G", "D", "A", "E"};
    private static final float NOTE_SPACING_DP = 56f;
    private static final long PULSE_PERIOD_MS = 1200;

    private final float density;
    private final float noteSpacingPx;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playheadGlowPaint = new Paint();
    private final Paint playheadPaint = new Paint();
    private final Paint donePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint upcomingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint currentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chordBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint doneTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint upcomingTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint currentTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private String[] stringLabels = STANDARD_LABELS;
    private List<TabNote> notes = new ArrayList<>();
    private int currentIndex = 0;
    private boolean playing = false;

    private float animatedScrollPx = 0f;
    private float targetScrollPx = 0f;
    private boolean frameScheduled = false;

    // перетаскивание ленты пальцем
    private final int touchSlop;
    private OnSeekListener seekListener;
    private boolean dragging = false;
    private float dragStartX;
    private float dragStartScrollPx;
    private int previewIndex = -1;

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            frameScheduled = false;
            float diff = targetScrollPx - animatedScrollPx;
            if (Math.abs(diff) < 0.5f) animatedScrollPx = targetScrollPx;
            else animatedScrollPx += diff * 0.2f;
            invalidate();
            if (playing || animatedScrollPx != targetScrollPx) scheduleFrame();
        }
    };

    public TabView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        noteSpacingPx = NOTE_SPACING_DP * density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        backgroundPaint.setColor(Color.parseColor("#263238"));

        stringPaint.setColor(Color.parseColor("#78909C"));

        labelPaint.setColor(Color.parseColor("#B0BEC5"));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(dp(13));
        labelPaint.setFakeBoldText(true);

        playheadGlowPaint.setColor(Color.argb(45, 255, 152, 0));
        playheadPaint.setColor(Color.parseColor("#FF9800"));
        playheadPaint.setStrokeWidth(dp(2));

        donePaint.setColor(Color.parseColor("#455A64"));
        upcomingPaint.setColor(Color.parseColor("#90CAF9"));
        currentPaint.setColor(Color.parseColor("#FF9800"));
        glowPaint.setColor(Color.parseColor("#FF9800"));

        doneTextPaint.setColor(Color.parseColor("#90A4AE"));
        upcomingTextPaint.setColor(Color.parseColor("#0D47A1"));
        currentTextPaint.setColor(Color.BLACK);
        for (Paint p : new Paint[]{doneTextPaint, upcomingTextPaint, currentTextPaint}) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setFakeBoldText(true);
        }
    }

    private float dp(float value) {
        return value * density;
    }

    public void setNotes(List<TabNote> notes) {
        this.notes = notes;
        currentIndex = 0;
        animatedScrollPx = 0f;
        targetScrollPx = 0f;
        invalidate();
    }

    /** Подписи струн от 1-й (тонкой) к 6-й, например для нестандартного строя песни. */
    public void setStringLabels(String[] labels) {
        stringLabels = labels != null && labels.length == STRING_COUNT ? labels : STANDARD_LABELS;
        invalidate();
    }

    /** Индекс ноты, которую нужно играть сейчас; notes.size() — урок пройден. */
    public void setCurrentIndex(int index) {
        currentIndex = index;
        // пока ленту тянут пальцем, её положение задаёт палец
        if (dragging) return;
        targetScrollPx = scrollForIndex(index);
        scheduleFrame();
    }

    public void setOnSeekListener(OnSeekListener listener) {
        seekListener = listener;
    }

    private float scrollForIndex(int index) {
        return Math.min(index, maxIndex()) * noteSpacingPx;
    }

    private int maxIndex() {
        return Math.max(0, notes.size() - 1);
    }

    private int indexAtPlayhead() {
        return Math.max(0, Math.min(maxIndex(), Math.round(animatedScrollPx / noteSpacingPx)));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (notes.isEmpty()) return super.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragStartX = event.getX();
                dragStartScrollPx = animatedScrollPx;
                dragging = false;
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - dragStartX;
                if (!dragging && Math.abs(dx) > touchSlop) {
                    dragging = true;
                    previewIndex = -1;
                    // не даём прокручиваемому родителю перехватить жест
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) {
                    float scroll = dragStartScrollPx - dx;
                    animatedScrollPx = Math.max(0, Math.min(maxIndex() * noteSpacingPx, scroll));
                    targetScrollPx = animatedScrollPx;
                    invalidate();

                    int index = indexAtPlayhead();
                    if (index != previewIndex) {
                        previewIndex = index;
                        if (seekListener != null) seekListener.onSeekPreview(index);
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
                if (dragging) {
                    dragging = false;
                    int index = indexAtPlayhead();
                    targetScrollPx = scrollForIndex(index);
                    scheduleFrame();
                    if (seekListener != null) seekListener.onSeek(index);
                } else {
                    performClick();
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    dragging = false;
                    // жест отменён — возвращаемся к текущей ноте
                    targetScrollPx = scrollForIndex(currentIndex);
                    scheduleFrame();
                }
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    /** Пока идёт урок, текущая нота пульсирует. */
    public void setPlaying(boolean playing) {
        this.playing = playing;
        scheduleFrame();
    }

    private void scheduleFrame() {
        if (frameScheduled) return;
        frameScheduled = true;
        postOnAnimation(frame);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        if (width == 0 || height == 0) return;

        // фон ленты
        rect.set(0, 0, width, height);
        canvas.drawRoundRect(rect, dp(12), dp(12), backgroundPaint);

        float gutter = dp(28);
        float top = dp(16);
        float bottom = height - dp(16);
        float stringSpacing = (bottom - top) / (STRING_COUNT - 1);

        // струны и их подписи; 1-я (e) сверху, как в табулатуре
        for (int s = 0; s < STRING_COUNT; s++) {
            float y = top + s * stringSpacing;
            stringPaint.setStrokeWidth(dp(1f + s * 0.3f));
            canvas.drawLine(gutter, y, width - dp(8), y, stringPaint);
            canvas.drawText(stringLabels[s], gutter / 2f, y + labelPaint.getTextSize() / 3f, labelPaint);
        }

        float playheadX = gutter + (width - gutter) * 0.22f;
        canvas.drawRect(playheadX - dp(8), dp(4), playheadX + dp(8), height - dp(4), playheadGlowPaint);
        canvas.drawLine(playheadX, dp(4), playheadX, height - dp(4), playheadPaint);

        float radius = Math.min(stringSpacing * 0.45f, noteSpacingPx * 0.36f);
        float pulse = pulse();

        canvas.save();
        canvas.clipRect(gutter, 0, width, height);

        for (int i = 0; i < notes.size(); i++) {
            float x = playheadX + i * noteSpacingPx - animatedScrollPx;
            if (x < gutter - radius * 2 || x > width + radius * 2) continue;

            TabNote step = notes.get(i);
            Paint fill;
            Paint textPaint;
            float r = radius;
            if (i == currentIndex) {
                fill = currentPaint;
                textPaint = currentTextPaint;
            } else if (i < currentIndex) {
                fill = donePaint;
                textPaint = doneTextPaint;
                r = radius * 0.8f;
            } else {
                fill = upcomingPaint;
                textPaint = upcomingTextPaint;
            }

            // аккорд: ноты на одной вертикали, соединённые полосой
            if (step.isChord()) {
                float minY = Float.MAX_VALUE;
                float maxY = -Float.MAX_VALUE;
                for (TabNote n : step.getNotes()) {
                    float y = top + (n.getStringNumber() - 1) * stringSpacing;
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
                chordBarPaint.setColor(fill.getColor());
                chordBarPaint.setAlpha(110);
                rect.set(x - r * 0.35f, minY, x + r * 0.35f, maxY);
                canvas.drawRoundRect(rect, r * 0.35f, r * 0.35f, chordBarPaint);
            }

            for (TabNote n : step.getNotes()) {
                float y = top + (n.getStringNumber() - 1) * stringSpacing;
                if (i == currentIndex) {
                    glowPaint.setAlpha((int) (110 * (1f - 0.6f * pulse)));
                    canvas.drawCircle(x, y, r * (1.3f + 0.25f * pulse), glowPaint);
                }
                drawNote(canvas, x, y, r, String.valueOf(n.getFret()), fill, textPaint);
            }
        }

        canvas.restore();
    }

    private void drawNote(Canvas canvas, float x, float y, float radius, String text,
                          Paint fill, Paint textPaint) {
        canvas.drawCircle(x, y, radius, fill);
        textPaint.setTextSize(radius * 1.1f);
        canvas.drawText(text, x, y + textPaint.getTextSize() / 3f, textPaint);
    }

    // 0..1..0 с периодом PULSE_PERIOD_MS; 0, если урок не идёт
    private float pulse() {
        if (!playing) return 0f;
        float phase = (SystemClock.uptimeMillis() % PULSE_PERIOD_MS) / (float) PULSE_PERIOD_MS;
        return phase < 0.5f ? phase * 2f : (1f - phase) * 2f;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(frame);
        frameScheduled = false;
    }
}
