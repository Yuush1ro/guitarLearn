package com.example.guitartuner.models;

/**
 * Время нот и щелчков метронома при исходном темпе (скорость 100%). Все времена в мс.
 * Для песен берётся из файла Guitar Pro, для уроков — каждая нота длится одну долю.
 */
public final class PlayTiming {

    /** Темп по умолчанию для уроков, в которых нет своего темпа. */
    public static final double LESSON_BPM = 60;

    public final double baseBpm;
    public final double[] stepStart;
    public final double[] stepDuration;
    public final double[] clickTimes;
    public final boolean[] clickAccents;

    public PlayTiming(double baseBpm, double[] stepStart, double[] stepDuration,
                      double[] clickTimes, boolean[] clickAccents) {
        this.baseBpm = baseBpm;
        this.stepStart = stepStart;
        this.stepDuration = stepDuration;
        this.clickTimes = clickTimes;
        this.clickAccents = clickAccents;
    }

    /** Каждый шаг — одна доля при темпе bpm, метроном на каждую долю, акцент раз в 4 доли. */
    public static PlayTiming evenBeats(int steps, double bpm) {
        double beatMs = 60000.0 / bpm;
        double[] start = new double[steps];
        double[] duration = new double[steps];
        double[] clicks = new double[steps];
        boolean[] accents = new boolean[steps];
        for (int i = 0; i < steps; i++) {
            start[i] = i * beatMs;
            duration[i] = beatMs;
            clicks[i] = i * beatMs;
            accents[i] = i % 4 == 0;
        }
        return new PlayTiming(bpm, start, duration, clicks, accents);
    }

    public int size() {
        return stepStart.length;
    }

    /** Конец последней ноты. */
    public double endMs() {
        int last = stepStart.length - 1;
        return last < 0 ? 0 : stepStart[last] + stepDuration[last];
    }

    /** Длительность одной доли в начале песни. */
    public double beatMs() {
        return 60000.0 / baseBpm;
    }

    /**
     * Какой шаг сейчас играть: последний, который начинается не позже songMs + earlyMs
     * (нота "включается" чуть заранее, чтобы успеть её сыграть). -1 — до первого шага.
     */
    public int indexAt(double songMs, double earlyMs) {
        double t = songMs + earlyMs;
        int lo = 0;
        int hi = stepStart.length - 1;
        int result = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (stepStart[mid] <= t) {
                result = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return result;
    }

    /**
     * Дробная позиция для плавной ленты: 3.5 — посередине между шагами 3 и 4.
     */
    public float positionAt(double songMs) {
        int i = indexAt(songMs, 0);
        if (i < 0) {
            // отсчёт перед первой нотой: подъезжаем к ней
            double first = stepStart.length > 0 ? stepStart[0] : 0;
            return (float) Math.max(-1, (songMs - first) / beatMs());
        }
        if (i + 1 >= stepStart.length) return i;
        double span = stepStart[i + 1] - stepStart[i];
        return span <= 0 ? i : (float) (i + (songMs - stepStart[i]) / span);
    }
}
