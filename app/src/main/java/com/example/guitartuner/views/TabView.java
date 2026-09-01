package com.example.guitartuner.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import com.example.guitartuner.models.TabNote;
import com.example.guitartuner.utils.GuitarNoteUtils;

import java.util.ArrayList;
import java.util.List;

public class TabView extends View {

    public interface OnNotePlayedListener {
        void onNotePlayed(TabNote note, String noteName);
    }

    private static final int STRING_COUNT = 6;
    private static final float NOTE_SPACING_DP = 90f;
    private static final float SCROLL_SPEED_DP_PER_SEC = 60f;

    private List<TabNote> notes = new ArrayList<>();
    private float noteSpacingPx;
    private float scrollSpeedPxPerSec;
    private final float playheadFractionX = 0.25f;

    private Paint stringPaint;
    private Paint notePaint;
    private Paint noteTextPaint;
    private Paint playheadPaint;

    private boolean isPlaying = false;
    private long startTimeMs = 0;
    private float pausedOffsetPx = 0;
    private int lastTriggeredIndex = -1;

    private OnNotePlayedListener listener;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPlaying) {
                invalidate();
                handler.postDelayed(this, 16);
            }
        }
    };

    public TabView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        noteSpacingPx = NOTE_SPACING_DP * density;
        scrollSpeedPxPerSec = SCROLL_SPEED_DP_PER_SEC * density;

        stringPaint = new Paint();
        stringPaint.setColor(Color.LTGRAY);
        stringPaint.setStrokeWidth(3f);

        notePaint = new Paint();
        notePaint.setStyle(Paint.Style.FILL);
        notePaint.setAntiAlias(true);

        noteTextPaint = new Paint();
        noteTextPaint.setColor(Color.WHITE);
        noteTextPaint.setTextSize(28f);
        noteTextPaint.setTextAlign(Paint.Align.CENTER);
        noteTextPaint.setAntiAlias(true);
        noteTextPaint.setFakeBoldText(true);

        playheadPaint = new Paint();
        playheadPaint.setColor(Color.RED);
        playheadPaint.setStrokeWidth(5f);
    }

    public void setNotes(List<TabNote> notes) {
        this.notes = notes;
        lastTriggeredIndex = -1;
        pausedOffsetPx = 0;
        invalidate();
    }

    public void setOnNotePlayedListener(OnNotePlayedListener listener) {
        this.listener = listener;
    }

    public void play() {
        if (isPlaying) return;
        isPlaying = true;
        startTimeMs = System.currentTimeMillis();
        handler.post(tickRunnable);
    }

    public void pause() {
        if (!isPlaying) return;
        pausedOffsetPx += elapsedScrollPx();
        isPlaying = false;
        handler.removeCallbacks(tickRunnable);
    }

    public void reset() {
        isPlaying = false;
        handler.removeCallbacks(tickRunnable);
        pausedOffsetPx = 0;
        lastTriggeredIndex = -1;
        invalidate();
    }

    private float elapsedScrollPx() {
        if (!isPlaying) return 0;
        long elapsedMs = System.currentTimeMillis() - startTimeMs;
        return (elapsedMs / 1000f) * scrollSpeedPxPerSec;
    }

    private float getTotalScrollPx() {
        return pausedOffsetPx + elapsedScrollPx();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;

        float topMargin = height * 0.12f;
        float bottomMargin = height * 0.12f;
        float stringSpacing = (height - topMargin - bottomMargin) / (STRING_COUNT - 1);

        for (int s = 0; s < STRING_COUNT; s++) {
            float y = topMargin + s * stringSpacing;
            canvas.drawLine(0, y, width, y, stringPaint);
        }

        float playheadX = width * playheadFractionX;
        float scrollPx = getTotalScrollPx();
        float noteRadius = Math.min(stringSpacing * 0.38f, noteSpacingPx * 0.3f);

        int triggerIndex = -1;

        for (int i = 0; i < notes.size(); i++) {
            TabNote note = notes.get(i);
            float noteX = playheadX + (i * noteSpacingPx) - scrollPx;

            if (noteX < -noteRadius * 2 || noteX > width + noteRadius * 2) {
                continue;
            }

            int stringIndex = note.getStringNumber() - 1; // 1..6 -> 0..5, сверху вниз
            float noteY = topMargin + stringIndex * stringSpacing;

            boolean isCurrent = noteX <= playheadX && noteX > playheadX - noteSpacingPx / 2f;
            if (isCurrent) {
                triggerIndex = i;
            }

            notePaint.setColor(isCurrent ? Color.parseColor("#D32F2F") : Color.parseColor("#2E7D32"));
            canvas.drawCircle(noteX, noteY, noteRadius, notePaint);
            canvas.drawText(
                    String.valueOf(note.getFret()),
                    noteX,
                    noteY + noteTextPaint.getTextSize() / 3f,
                    noteTextPaint
            );
        }

        canvas.drawLine(playheadX, 0, playheadX, height, playheadPaint);

        if (isPlaying && triggerIndex != -1 && triggerIndex != lastTriggeredIndex) {
            lastTriggeredIndex = triggerIndex;
            TabNote note = notes.get(triggerIndex);
            if (listener != null) {
                listener.onNotePlayed(note, GuitarNoteUtils.getNoteName(note.getStringNumber(), note.getFret()));
            }
        }

        if (isPlaying && !notes.isEmpty()) {
            float lastNoteX = playheadX + ((notes.size() - 1) * noteSpacingPx) - scrollPx;
            if (lastNoteX < playheadX - noteSpacingPx) {
                pause();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(tickRunnable);
    }
}