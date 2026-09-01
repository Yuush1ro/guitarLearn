package com.example.guitartuner.views;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

import com.example.guitartuner.models.FallingNote;

import java.util.ArrayList;
import java.util.List;

public class NoteFallView extends View {

    // условные "струны" сверху вниз по толщине: 6-я .. 1-я
    public static final String[] LANE_NOTES = {"E", "A", "D", "G", "B", "E"};

    private final List<FallingNote> notes = new ArrayList<>();

    private final Paint lanePaint;
    private final Paint hitLinePaint;
    private final Paint notePendingPaint;
    private final Paint noteHitPaint;
    private final Paint noteMissPaint;
    private final Paint textPaint;
    private final Paint laneLabelPaint;

    public NoteFallView(Context context, AttributeSet attrs) {
        super(context, attrs);

        lanePaint = new Paint();
        lanePaint.setColor(Color.LTGRAY);
        lanePaint.setStrokeWidth(2);

        hitLinePaint = new Paint();
        hitLinePaint.setColor(Color.DKGRAY);
        hitLinePaint.setStrokeWidth(6);

        notePendingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        notePendingPaint.setColor(Color.parseColor("#4287f5"));

        noteHitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        noteHitPaint.setColor(Color.parseColor("#4CAF50"));

        noteMissPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        noteMissPaint.setColor(Color.parseColor("#F44336"));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(28f);
        textPaint.setFakeBoldText(true);

        laneLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        laneLabelPaint.setColor(Color.DKGRAY);
        laneLabelPaint.setTextAlign(Paint.Align.CENTER);
        laneLabelPaint.setTextSize(24f);
    }

    public void setNotes(List<FallingNote> currentNotes) {
        synchronized (notes) {
            notes.clear();
            notes.addAll(currentNotes);
        }
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();

        int laneCount = LANE_NOTES.length;
        float laneWidth = width / laneCount;
        float hitLineY = height * 0.85f;

        for (int i = 0; i <= laneCount; i++) {
            float x = i * laneWidth;
            canvas.drawLine(x, 0, x, height, lanePaint);
        }
        for (int i = 0; i < laneCount; i++) {
            float cx = i * laneWidth + laneWidth / 2f;
            canvas.drawText(LANE_NOTES[i], cx, height - 12, laneLabelPaint);
        }

        canvas.drawLine(0, hitLineY, width, hitLineY, hitLinePaint);

        float radius = Math.min(laneWidth, 120f) * 0.32f;

        synchronized (notes) {
            for (FallingNote note : notes) {

                int laneIndex = laneIndexForNote(note.noteName);
                float cx = laneIndex * laneWidth + laneWidth / 2f;
                float cy = note.progress * hitLineY;

                Paint paint = notePendingPaint;
                if (note.hit) paint = noteHitPaint;
                else if (note.missed) paint = noteMissPaint;

                canvas.drawCircle(cx, cy, radius, paint);
                canvas.drawText(note.noteName, cx, cy + 10, textPaint);
            }
        }
    }

    public static int laneIndexForNote(String noteName) {
        for (int i = 0; i < LANE_NOTES.length; i++) {
            if (LANE_NOTES[i].equals(noteName)) return i;
        }
        return Math.abs(noteName.hashCode()) % LANE_NOTES.length;
    }
}