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
import com.example.guitartuner.tuner.NoteUtils;
import com.example.guitartuner.utils.GuitarNoteUtils;

import java.util.ArrayList;
import java.util.List;

public class TabView extends View {

    public interface OnNotePlayedListener {
        void onNotePlayed(TabNote note, String noteName);
    }

    public interface OnLessonCompleteListener {
        void onLessonComplete();
    }

    private static final int STRING_COUNT = 6;
    private static final float NOTE_SPACING_DP = 90f;

    // сколько мс подряд нужно держать верную ноту, чтобы засчитать попадание
    private static final long HOLD_MS = 120;
    // защита от повторного засчитывания той же ноты сразу после попадания
    private static final long ADVANCE_COOLDOWN_MS = 250;

    private List<TabNote> notes = new ArrayList<>();
    private float noteSpacingPx;
    private final float playheadFractionX = 0.25f;

    private Paint stringPaint;
    private Paint notePaintPending;
    private Paint notePaintCurrent;
    private Paint notePaintDone;
    private Paint noteTextPaint;
    private Paint playheadPaint;

    private boolean isPlaying = false;
    private int currentIndex = 0;

    private float animatedScrollPx = 0f;
    private float targetScrollPx = 0f;

    private long correctSince = 0L;
    private long lastAdvanceTime = 0L;

    private OnNotePlayedListener listener;
    private OnLessonCompleteListener completeListener;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPlaying) {
                animatedScrollPx += (targetScrollPx - animatedScrollPx) * 0.25f;
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

        stringPaint = new Paint();
        stringPaint.setColor(Color.LTGRAY);
        stringPaint.setStrokeWidth(3f);

        notePaintPending = new Paint(Paint.ANTI_ALIAS_FLAG);
        notePaintPending.setColor(Color.parseColor("#2E7D32")); // зелёный — впереди

        notePaintCurrent = new Paint(Paint.ANTI_ALIAS_FLAG);
        notePaintCurrent.setColor(Color.parseColor("#F9A825")); // жёлтый — играть сейчас

        notePaintDone = new Paint(Paint.ANTI_ALIAS_FLAG);
        notePaintDone.setColor(Color.parseColor("#616161")); // серый — уже сыграно

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
        currentIndex = 0;
        animatedScrollPx = 0f;
        targetScrollPx = 0f;
        correctSince = 0L;
        invalidate();
    }

    public void setOnNotePlayedListener(OnNotePlayedListener listener) {
        this.listener = listener;
    }

    public void setOnLessonCompleteListener(OnLessonCompleteListener listener) {
        this.completeListener = listener;
    }

    public void play() {
        if (isPlaying) return;
        isPlaying = true;
        handler.post(tickRunnable);
    }

    public void pause() {
        isPlaying = false;
        handler.removeCallbacks(tickRunnable);
    }

    public void reset() {
        pause();
        currentIndex = 0;
        animatedScrollPx = 0f;
        targetScrollPx = 0f;
        correctSince = 0L;
        invalidate();
    }

    /** Дёргается из фрагмента при каждой определённой частоте с микрофона. */
    public void onPitchDetected(double frequencyHz) {
        if (!isPlaying) return;
        if (currentIndex >= notes.size()) return;

        long now = System.currentTimeMillis();
        if (now - lastAdvanceTime < ADVANCE_COOLDOWN_MS) return;

        NoteUtils.NoteInfo detected = NoteUtils.frequencyToNote(frequencyHz);
        if (detected == null) {
            correctSince = 0L;
            return;
        }

        TabNote expected = notes.get(currentIndex);
        String expectedFullName = GuitarNoteUtils.getNoteName(
                expected.getStringNumber(), expected.getFret());

        if (expectedFullName.equals(detected.fullName)) {
            if (correctSince == 0L) {
                correctSince = now;
            } else if (now - correctSince >= HOLD_MS) {
                advanceToNext(now);
            }
        } else {
            correctSince = 0L;
        }
    }

    private void advanceToNext(long now) {
        TabNote note = notes.get(currentIndex);
        String fullName = GuitarNoteUtils.getNoteName(note.getStringNumber(), note.getFret());

        if (listener != null) {
            listener.onNotePlayed(note, fullName);
        }

        currentIndex++;
        correctSince = 0L;
        lastAdvanceTime = now;
        targetScrollPx = currentIndex * noteSpacingPx;

        if (currentIndex >= notes.size()) {
            pause();
            if (completeListener != null) completeListener.onLessonComplete();
        }
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
        float noteRadius = Math.min(stringSpacing * 0.38f, noteSpacingPx * 0.3f);

        for (int i = 0; i < notes.size(); i++) {
            TabNote note = notes.get(i);
            float noteX = playheadX + (i * noteSpacingPx) - animatedScrollPx;

            if (noteX < -noteRadius * 2 || noteX > width + noteRadius * 2) continue;

            int stringIndex = note.getStringNumber() - 1;
            float noteY = topMargin + stringIndex * stringSpacing;

            Paint paint;
            if (i < currentIndex) paint = notePaintDone;
            else if (i == currentIndex) paint = notePaintCurrent;
            else paint = notePaintPending;

            canvas.drawCircle(noteX, noteY, noteRadius, paint);
            canvas.drawText(String.valueOf(note.getFret()), noteX,
                    noteY + noteTextPaint.getTextSize() / 3f, noteTextPaint);
        }

        canvas.drawLine(playheadX, 0, playheadX, height, playheadPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(tickRunnable);
    }
}