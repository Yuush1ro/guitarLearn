package com.example.guitartuner.tuner;

/**
 * Превращает поток сэмплов с микрофона в кадры AudioFrame: тон (YIN), громкость,
 * атака (резкий рост громкости) и, при необходимости, хромаграмма для аккордов.
 *
 * Чистая Java без Android — её можно проверять на ПК синтезированным звуком.
 */
public class FrameAnalyzer {

    public static final int SAMPLE_RATE = 44100;
    /** Новые сэмплы на кадр (~23 мс) — так часто приходит результат. */
    public static final int HOP_SIZE = 1024;
    // окно YIN (~46 мс): несколько периодов низкой E2 (82 Гц) и быстрая реакция на новую ноту
    static final int YIN_WINDOW = 2048;
    // окно хромаграммы (~93 мс): нужно чуть длиннее, чтобы различать низкие ноты аккорда
    static final int CHROMA_WINDOW = 4096;

    // диапазон поиска тона: чуть ниже E2 (82 Гц) .. выше 20-го лада первой струны (~1319 Гц)
    private static final double MIN_FREQ = 60.0;
    private static final double MAX_FREQ = 1400.0;
    // порог тишины по RMS (сэмплы нормированы в -1..1)
    private static final double SILENCE_RMS = 0.01;
    // атака: громкость выросла во столько раз относительно минимума последних кадров.
    // Повторный щипок ещё звучащей струны даёт всего ~×1.4, поэтому порог невысокий
    private static final double ONSET_RATIO = 1.3;
    private static final int ONSET_HISTORY = 3;

    private final boolean computeChroma;
    private final int historySize;
    private final float[] history;
    private final Yin yin = new Yin(SAMPLE_RATE, YIN_WINDOW, MIN_FREQ, MAX_FREQ);
    private final ChromaAnalyzer chromaAnalyzer;
    private final double[] recentLevels = new double[ONSET_HISTORY];
    private int levelsFilled = 0;

    private int filled = 0;
    private long totalSamples = 0;

    public FrameAnalyzer(boolean computeChroma) {
        this.computeChroma = computeChroma;
        this.historySize = computeChroma ? CHROMA_WINDOW : YIN_WINDOW;
        this.history = new float[historySize];
        this.chromaAnalyzer = computeChroma ? new ChromaAnalyzer(SAMPLE_RATE, CHROMA_WINDOW) : null;
    }

    /**
     * Добавляет count новых сэмплов (-1..1). Возвращает кадр или null,
     * пока не накопилось достаточно звука.
     */
    public AudioFrame process(float[] samples, int count) {
        if (count <= 0) return null;
        if (count >= historySize) {
            System.arraycopy(samples, count - historySize, history, 0, historySize);
        } else {
            System.arraycopy(history, count, history, 0, historySize - count);
            System.arraycopy(samples, 0, history, historySize - count, count);
        }
        filled = Math.min(historySize, filled + count);
        totalSamples += count;
        if (filled < YIN_WINDOW) return null;

        long streamMs = totalSamples * 1000 / SAMPLE_RATE;
        int yinOffset = historySize - YIN_WINDOW;

        double windowLevel = rms(history, yinOffset, historySize);
        double level = rms(history, historySize - Math.min(count, historySize), historySize);
        boolean onset = detectOnset(level);

        if (windowLevel < SILENCE_RMS) {
            return new AudioFrame(true, -1, 0, level, false, null, streamMs);
        }

        double freq = yin.detect(history, yinOffset);
        double confidence = freq > 0 ? yin.lastConfidence() : 0;
        double[] chroma = computeChroma && filled == CHROMA_WINDOW
                ? chromaAnalyzer.analyze(history) : null;

        return new AudioFrame(false, freq, confidence, level, onset, chroma, streamMs);
    }

    private boolean detectOnset(double level) {
        boolean onset = false;
        if (levelsFilled > 0) {
            double min = Double.MAX_VALUE;
            for (int i = 0; i < levelsFilled; i++) min = Math.min(min, recentLevels[i]);
            onset = level > SILENCE_RMS * 1.5 && level > min * ONSET_RATIO;
        }
        // сдвигаем историю громкости
        System.arraycopy(recentLevels, 0, recentLevels, 1, ONSET_HISTORY - 1);
        recentLevels[0] = level;
        levelsFilled = Math.min(ONSET_HISTORY, levelsFilled + 1);
        return onset;
    }

    private static double rms(float[] data, int from, int to) {
        double sum = 0;
        for (int i = from; i < to; i++) sum += data[i] * data[i];
        return Math.sqrt(sum / Math.max(1, to - from));
    }
}
