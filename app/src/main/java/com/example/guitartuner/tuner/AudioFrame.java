package com.example.guitartuner.tuner;

/** Результат анализа одного кадра звука с микрофона. */
public final class AudioFrame {

    /** Сигнал слишком тихий — струны не звучат. */
    public final boolean silent;
    /** Основной тон в Гц или -1, если одной чёткой ноты нет. */
    public final double frequency;
    /** Уверенность YIN в найденном тоне: 1 — чистая нота, 0 — нет ноты. */
    public final double confidence;
    /** Громкость (RMS, 0..1) свежих сэмплов кадра. */
    public final double level;
    /** В этом кадре новый щипок или удар по струнам (по громкости или по спектру). */
    public final boolean onset;
    /** Хромаграмма (12 нот, максимум = 1) или null, если не считалась. */
    public final double[] chroma;
    /** Какие ноты только что зазвучали (рост энергии, максимум = 1); null — атаки нет. */
    public final double[] onsetChroma;
    /** Время конца кадра от начала записи, мс. */
    public final long streamMs;
    /** Когда кадр записан (SystemClock.uptimeMillis); 0 — неизвестно. Ставит PitchDetector. */
    public long captureUptimeMs;

    public AudioFrame(boolean silent, double frequency, double confidence, double level,
                      boolean onset, double[] chroma, double[] onsetChroma, long streamMs) {
        this.silent = silent;
        this.frequency = frequency;
        this.confidence = confidence;
        this.level = level;
        this.onset = onset;
        this.chroma = chroma;
        this.onsetChroma = onsetChroma;
        this.streamMs = streamMs;
    }

    /** MIDI-номер найденной ноты или -1. */
    public int midi() {
        if (frequency <= 0) return -1;
        return (int) Math.round(69 + 12 * Math.log(frequency / 440.0) / Math.log(2));
    }
}
