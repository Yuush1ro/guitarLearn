package com.example.guitartuner.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Карта прохождения: вся песня тонкой полосой. Зелёный — сыграно верно, красный —
 * пропущено, серый — ещё не играли; оранжевая метка — текущее место.
 * Нажатие или перетаскивание по полосе перематывает туда.
 */
public class ResultMapView extends View {

    public interface OnSeekListener {
        void onSeek(int index);
    }

    private static final byte HIT = 1;
    private static final byte MISS = 2;

    private final float density;
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hitPaint = new Paint();
    private final Paint missPaint = new Paint();
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private byte[] results = new byte[0];
    private int currentIndex = 0;
    private OnSeekListener seekListener;

    public ResultMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        trackPaint.setColor(Color.parseColor("#222833"));
        hitPaint.setColor(Color.parseColor("#3DFF8E"));
        missPaint.setColor(Color.parseColor("#FF3D71"));
        markerPaint.setColor(Color.parseColor("#FF8A1F"));
        markerGlowPaint.setColor(Color.argb(90, 255, 138, 31));
    }

    public void setResults(byte[] results, int currentIndex) {
        this.results = results;
        this.currentIndex = currentIndex;
        invalidate();
    }

    public void setOnSeekListener(OnSeekListener listener) {
        seekListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w == 0 || h == 0) return;

        float pad = 4 * density;
        float barTop = pad;
        float barBottom = h - pad;
        float radius = (barBottom - barTop) / 2f;
        rect.set(0, barTop, w, barBottom);
        canvas.drawRoundRect(rect, radius, radius, trackPaint);

        int n = results.length;
        if (n == 0) return;

        // по пикселю: если на этот кусок пришлась хоть одна ошибка — красный, иначе
        // если что-то сыграно — зелёный (ошибки важнее, их нужно увидеть)
        int columns = (int) Math.max(1, w);
        for (int x = 0; x < columns; x++) {
            int from = (int) ((long) x * n / columns);
            int to = Math.max(from + 1, (int) ((long) (x + 1) * n / columns));
            boolean miss = false;
            boolean hit = false;
            for (int i = from; i < to && i < n; i++) {
                if (results[i] == MISS) miss = true;
                else if (results[i] == HIT) hit = true;
            }
            if (miss) canvas.drawRect(x, barTop, x + 1, barBottom, missPaint);
            else if (hit) canvas.drawRect(x, barTop, x + 1, barBottom, hitPaint);
        }

        float markerX = Math.min(w - 1, (Math.min(currentIndex, n - 1) + 0.5f) * w / n);
        canvas.drawRect(markerX - 3 * density, 0, markerX + 3 * density, h, markerGlowPaint);
        canvas.drawRect(markerX - density, 0, markerX + density, h, markerPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int n = results.length;
        if (n == 0 || seekListener == null) return super.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE
                || action == MotionEvent.ACTION_UP) {
            int index = (int) (event.getX() / Math.max(1, getWidth()) * n);
            index = Math.max(0, Math.min(n - 1, index));
            if (index != currentIndex || action == MotionEvent.ACTION_UP) {
                currentIndex = index;
                seekListener.onSeek(index);
            }
            if (action == MotionEvent.ACTION_UP) performClick();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
