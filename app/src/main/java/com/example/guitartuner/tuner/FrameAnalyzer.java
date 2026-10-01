package com.example.guitartuner.tuner;

import org.jtransforms.fft.DoubleFFT_1D;

/**
 * Превращает поток сэмплов с микрофона в кадры AudioFrame: тон (YIN), громкость,
 * атака и, при необходимости, хромаграмма для аккордов.
 *
 * Атака определяется по спектральному потоку (spectral flux) — насколько выросли
 * амплитуды частот по сравнению с прошлым кадром. Так новый удар заметен, даже если
 * струны ещё звенят и общая громкость почти не выросла. Заодно видно, какие ноты
 * только что зазвучали (onsetChroma) — по ним новая нота опознаётся поверх аккорда.
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

    // атака по громкости: выросла во столько раз относительно минимума последних кадров
    private static final double ONSET_RATIO = 1.3;
    private static final int ONSET_HISTORY = 3;

    // атака по спектру: поток выше среднего за последние кадры во столько раз и не ниже порога
    private static final double FLUX_RATIO = 1.8;
    private static final double FLUX_FLOOR = 0.08;
    private static final int FLUX_HISTORY = 8;
    // спектр для потока: 86 Гц … 4 кГц; ноты атаки — от 150 Гц (ниже бины слишком грубые)
    private static final double FLUX_MIN_FREQ = 86;
    private static final double FLUX_MAX_FREQ = 4000;
    private static final double ONSET_CHROMA_MIN_FREQ = 150;
    private static final double LOG_GAIN = 10;

    private final boolean computeChroma;
    private final int historySize;
    private final float[] history;
    private final Yin yin = new Yin(SAMPLE_RATE, YIN_WINDOW, MIN_FREQ, MAX_FREQ);
    private final ChromaAnalyzer chromaAnalyzer;

    private final double[] recentLevels = new double[ONSET_HISTORY];
    private int levelsFilled = 0;

    private final DoubleFFT_1D fluxFft = new DoubleFFT_1D(YIN_WINDOW);
    private final double[] fluxBuffer = new double[YIN_WINDOW];
    private final double[] hann = new double[YIN_WINDOW];
    private final int fluxMinBin;
    private final int fluxMaxBin;
    private final int[] binPitchClass;
    private final double[] previousLogMag;
    private final double[] previousMag;
    private boolean hasPreviousSpectrum = false;
    private final double[] recentFlux = new double[FLUX_HISTORY];
    private int fluxFilled = 0;

    private int filled = 0;
    private long totalSamples = 0;

    public FrameAnalyzer(boolean computeChroma) {
        this.computeChroma = computeChroma;
        this.historySize = computeChroma ? CHROMA_WINDOW : YIN_WINDOW;
        this.history = new float[historySize];
        this.chromaAnalyzer = computeChroma ? new ChromaAnalyzer(SAMPLE_RATE, CHROMA_WINDOW) : null;

        for (int i = 0; i < YIN_WINDOW; i++) {
            hann[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (YIN_WINDOW - 1));
        }
        fluxMinBin = (int) Math.ceil(FLUX_MIN_FREQ * YIN_WINDOW / SAMPLE_RATE);
        fluxMaxBin = Math.min(YIN_WINDOW / 2 - 1, (int) (FLUX_MAX_FREQ * YIN_WINDOW / SAMPLE_RATE));
        previousLogMag = new double[fluxMaxBin + 1];
        previousMag = new double[fluxMaxBin + 1];
        binPitchClass = new int[fluxMaxBin + 1];
        for (int k = 0; k <= fluxMaxBin; k++) {
            double freq = k * (double) SAMPLE_RATE / YIN_WINDOW;
            binPitchClass[k] = freq < ONSET_CHROMA_MIN_FREQ ? -1
                    : (int) (((Math.round(69 + 12 * Math.log(freq / 440.0) / Math.log(2)) % 12) + 12) % 12);
        }
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
        boolean loudOnset = detectLoudnessOnset(level);

        // спектральный поток считаем всегда — иначе после тишины не с чем сравнивать
        double[] onsetChroma = new double[12];
        double flux = spectralFlux(yinOffset, onsetChroma);
        boolean fluxOnset = detectFluxOnset(flux);

        if (windowLevel < SILENCE_RMS) {
            return new AudioFrame(true, -1, 0, level, false, null, null, streamMs);
        }

        boolean onset = loudOnset || fluxOnset;
        double freq = yin.detect(history, yinOffset);
        double confidence = freq > 0 ? yin.lastConfidence() : 0;
        double[] chroma = computeChroma && filled == CHROMA_WINDOW
                ? chromaAnalyzer.analyze(history) : null;

        return new AudioFrame(false, freq, confidence, level, onset, chroma,
                onset ? normalize(onsetChroma) : null, streamMs);
    }

    /** Поток: сумма роста лог-амплитуд по частотам; в onsetChroma — рост по нотам. */
    private double spectralFlux(int offset, double[] onsetChroma) {
        for (int i = 0; i < YIN_WINDOW; i++) fluxBuffer[i] = history[offset + i] * hann[i];
        fluxFft.realForward(fluxBuffer);

        double flux = 0;
        for (int k = fluxMinBin; k <= fluxMaxBin; k++) {
            double re = fluxBuffer[2 * k];
            double im = fluxBuffer[2 * k + 1];
            double mag = Math.sqrt(re * re + im * im);
            double logMag = Math.log1p(LOG_GAIN * mag);
            if (hasPreviousSpectrum) {
                // атака — по логарифму: заметен и тихий новый удар
                double rise = logMag - previousLogMag[k];
                if (rise > 0) flux += rise;
                // какие ноты зазвучали — по линейной амплитуде: там главный основной тон,
                // а не обертоны, которые до удара были почти беззвучны
                double linearRise = mag - previousMag[k];
                if (linearRise > 0 && binPitchClass[k] >= 0) onsetChroma[binPitchClass[k]] += linearRise;
            }
            previousLogMag[k] = logMag;
            previousMag[k] = mag;
        }
        hasPreviousSpectrum = true;
        return flux / (fluxMaxBin - fluxMinBin + 1);
    }

    private boolean detectFluxOnset(double flux) {
        boolean onset = false;
        if (fluxFilled > 0) {
            double mean = 0;
            for (int i = 0; i < fluxFilled; i++) mean += recentFlux[i];
            mean /= fluxFilled;
            onset = flux > FLUX_FLOOR && flux > mean * FLUX_RATIO;
        }
        System.arraycopy(recentFlux, 0, recentFlux, 1, FLUX_HISTORY - 1);
        recentFlux[0] = flux;
        fluxFilled = Math.min(FLUX_HISTORY, fluxFilled + 1);
        return onset;
    }

    private boolean detectLoudnessOnset(double level) {
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

    private static double[] normalize(double[] values) {
        double max = 0;
        for (double v : values) max = Math.max(max, v);
        if (max <= 0) return null;
        for (int i = 0; i < values.length; i++) values[i] /= max;
        return values;
    }

    private static double rms(float[] data, int from, int to) {
        double sum = 0;
        for (int i = from; i < to; i++) sum += data[i] * data[i];
        return Math.sqrt(sum / Math.max(1, to - from));
    }
}
