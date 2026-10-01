package com.example.guitartuner.tuner;

/**
 * Определение основного тона алгоритмом YIN (de Cheveigné & Kawahara, 2002).
 *
 * YIN ищет период сигнала, а не самый громкий пик спектра, поэтому не путает
 * основной тон с гармониками (низкая E2 не определяется как E3/B3).
 */
public final class Yin {

    // порог: чем меньше, тем строже требование к "периодичности" сигнала
    private static final double THRESHOLD = 0.15;
    // провал на 1/2 или 1/3 периода, достаточный, чтобы считать его настоящим периодом
    private static final double OCTAVE_CHECK_THRESHOLD = 0.25;

    private final int sampleRate;
    private final int tauMin;
    private final int tauMax;
    private final int integrationWindow;
    private final double[] diff;

    // насколько "непериодичен" сигнал в последнем найденном тоне: 0 — идеальная нота
    private double lastAperiodicity = 1;

    public Yin(int sampleRate, int windowSize, double minFreq, double maxFreq) {
        this.sampleRate = sampleRate;
        this.tauMin = Math.max(2, (int) Math.floor(sampleRate / maxFreq));
        this.tauMax = Math.min(windowSize / 2, (int) Math.ceil(sampleRate / minFreq));
        this.integrationWindow = windowSize - tauMax;
        this.diff = new double[tauMax + 1];
    }

    /** Уверенность в последнем найденном тоне: 1 — чистая нота, 0 — шум. */
    public double lastConfidence() {
        return Math.max(0, 1 - lastAperiodicity);
    }

    /** Частота основного тона в Гц или -1, если чёткого тона нет. x — последние windowSize сэмплов. */
    public double detect(float[] x, int offset) {
        // 1. разностная функция d(tau)
        for (int tau = 1; tau <= tauMax; tau++) {
            double sum = 0;
            for (int j = 0; j < integrationWindow; j++) {
                double delta = x[offset + j] - x[offset + j + tau];
                sum += delta * delta;
            }
            diff[tau] = sum;
        }

        // 2. кумулятивная нормализация d'(tau)
        diff[0] = 1;
        double runningSum = 0;
        for (int tau = 1; tau <= tauMax; tau++) {
            runningSum += diff[tau];
            diff[tau] = runningSum == 0 ? 1 : diff[tau] * tau / runningSum;
        }

        // 3. первый провал ниже порога, затем спуск к его локальному минимуму
        int tau = -1;
        for (int t = tauMin; t <= tauMax; t++) {
            if (diff[t] < THRESHOLD) {
                while (t + 1 <= tauMax && diff[t + 1] < diff[t]) t++;
                tau = t;
                break;
            }
        }
        if (tau < 0) {
            lastAperiodicity = 1;
            return -1;
        }
        tau = correctOctave(tau);
        lastAperiodicity = diff[tau];

        // 4. параболическая интерполяция для суб-сэмпловой точности
        double betterTau = tau;
        if (tau < tauMax) {
            double s0 = diff[tau - 1];
            double s1 = diff[tau];
            double s2 = diff[tau + 1];
            double denom = s0 - 2 * s1 + s2;
            if (denom != 0) betterTau = tau + 0.5 * (s0 - s2) / denom;
        }

        return sampleRate / betterTau;
    }

    /**
     * Поверх звенящих струн провал на настоящем периоде бывает выше порога, и YIN берёт
     * кратный (вдвое-втрое больший) — нота "уезжает" на октаву вниз. Если на 1/2 или 1/3
     * найденного периода тоже есть хороший провал, настоящий период — он.
     */
    private int correctOctave(int tau) {
        int best = tau;
        for (int divisor = 3; divisor >= 2; divisor--) {
            int center = Math.round(tau / (float) divisor);
            int from = Math.max(tauMin, center - 2);
            int to = Math.min(tauMax, center + 2);
            int localMin = -1;
            for (int t = from; t <= to; t++) {
                if (localMin < 0 || diff[t] < diff[localMin]) localMin = t;
            }
            if (localMin >= 0 && diff[localMin] < OCTAVE_CHECK_THRESHOLD && localMin < best) {
                best = localMin;
            }
        }
        return best;
    }
}
