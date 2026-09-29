package com.example.guitartuner.tuner;

import org.jtransforms.fft.DoubleFFT_1D;

/**
 * Хромаграмма: сколько энергии звука приходится на каждую из 12 нот (0 = C … 11 = B)
 * без учёта октавы. Нужна для аккордов — YIN слышит только одну ноту.
 *
 * Берём пики спектра, уточняем их частоту параболической интерполяцией
 * и складываем амплитуды в ноту, к которой пик ближе всего.
 */
public class ChromaAnalyzer {

    private static final double MIN_FREQ = 70.0;    // чуть ниже E2 (82 Гц)
    private static final double MAX_FREQ = 2500.0;
    // пики слабее этой доли от самого сильного — шум
    private static final double PEAK_THRESHOLD = 0.03;
    // пик дальше этого (в полутонах) от ближайшей ноты не считаем нотой
    private static final double MAX_DETUNE_SEMITONES = 0.4;

    private final int sampleRate;
    private final int size;
    private final DoubleFFT_1D fft;
    private final double[] hann;
    private final double[] buffer;
    private final double[] magnitudes;

    public ChromaAnalyzer(int sampleRate, int size) {
        this.sampleRate = sampleRate;
        this.size = size;
        this.fft = new DoubleFFT_1D(size);
        this.hann = new double[size];
        for (int i = 0; i < size; i++) {
            hann[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (size - 1));
        }
        this.buffer = new double[size];
        this.magnitudes = new double[size / 2];
    }

    /** samples.length == size. Возвращает 12 значений, нормированных так, что максимум = 1. */
    public double[] analyze(float[] samples) {
        for (int i = 0; i < size; i++) buffer[i] = samples[i] * hann[i];
        fft.realForward(buffer);

        int kMin = Math.max(2, (int) Math.ceil(MIN_FREQ * size / sampleRate));
        int kMax = Math.min(size / 2 - 2, (int) (MAX_FREQ * size / sampleRate));

        double maxMag = 0;
        for (int k = kMin - 1; k <= kMax + 1; k++) {
            double re = buffer[2 * k];
            double im = buffer[2 * k + 1];
            magnitudes[k] = Math.sqrt(re * re + im * im);
            if (k >= kMin && k <= kMax) maxMag = Math.max(maxMag, magnitudes[k]);
        }

        double[] chroma = new double[12];
        if (maxMag == 0) return chroma;
        double threshold = maxMag * PEAK_THRESHOLD;

        for (int k = kMin; k <= kMax; k++) {
            double m = magnitudes[k];
            if (m < threshold || m <= magnitudes[k - 1] || m < magnitudes[k + 1]) continue;

            // параболическая интерполяция по логарифму амплитуды — точная частота пика
            double a = Math.log(magnitudes[k - 1] + 1e-12);
            double b = Math.log(m);
            double c = Math.log(magnitudes[k + 1] + 1e-12);
            double denom = a - 2 * b + c;
            double offset = denom == 0 ? 0 : 0.5 * (a - c) / denom;
            double freq = (k + offset) * sampleRate / size;

            double midi = 69 + 12 * Math.log(freq / 440.0) / Math.log(2);
            long nearest = Math.round(midi);
            if (Math.abs(midi - nearest) > MAX_DETUNE_SEMITONES) continue;

            chroma[(int) (((nearest % 12) + 12) % 12)] += m;
        }

        double max = 0;
        for (double v : chroma) max = Math.max(max, v);
        if (max > 0) {
            for (int i = 0; i < 12; i++) chroma[i] /= max;
        }
        return chroma;
    }
}
