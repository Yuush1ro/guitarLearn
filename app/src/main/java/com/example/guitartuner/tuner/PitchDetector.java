package com.example.guitartuner.tuner;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

/**
 * Определение высоты звука с микрофона алгоритмом YIN.
 *
 * YIN ищет период сигнала, а не самый громкий пик спектра, поэтому не путает
 * основной тон с гармониками (низкая E2 больше не определяется как E3/B3).
 *
 * Колбэки слушателя приходят в главном потоке и только пока детектор запущен —
 * после stop() ни один колбэк уже не будет вызван.
 */
public class PitchDetector {

    public interface PitchListener {
        void onPitchDetected(double frequencyHz);

        /** Сигнал слишком тихий или нет чёткого тона. */
        default void onSilence() {
        }
    }

    private static final int SAMPLE_RATE = 44100;

    // окно анализа в сэмплах (~93 мс) — хватает на несколько периодов низкой E2 (82 Гц)
    private static final int WINDOW_SIZE = 4096;
    // шаг между окнами в сэмплах (~46 мс) — окна перекрываются наполовину
    private static final int HOP_SIZE = 2048;

    // диапазон поиска: чуть ниже E2 (82 Гц) .. выше 20-го лада первой струны (~1319 Гц)
    private static final double MIN_FREQ = 60.0;
    private static final double MAX_FREQ = 1400.0;

    // порог YIN: чем меньше, тем строже требование к "периодичности" сигнала
    private static final double YIN_THRESHOLD = 0.15;
    // порог тишины по RMS (сэмплы нормированы в -1..1)
    private static final double SILENCE_RMS = 0.01;

    private final PitchListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // текущий поток записи; null — детектор остановлен.
    // Поток работает, пока это поле указывает на него, поэтому быстрый stop()+start()
    // не оживит старый поток.
    private volatile Thread thread;

    public PitchDetector(PitchListener listener) {
        this.listener = listener;
    }

    public boolean isRunning() {
        return thread != null;
    }

    public void start() {
        if (thread != null) return;
        Thread t = new Thread(this::recordLoop, "PitchDetector");
        thread = t;
        t.start();
    }

    /**
     * Останавливает запись. AudioRecord освобождается в самом потоке записи,
     * поэтому нет гонки между read() и release().
     */
    public void stop() {
        thread = null;
    }

    private boolean isActive(Thread t) {
        return thread == t;
    }

    private void recordLoop() {
        Thread self = Thread.currentThread();

        int minBufferBytes = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        // getMinBufferSize возвращает БАЙТЫ; 1 сэмпл PCM16 = 2 байта
        int bufferBytes = Math.max(minBufferBytes, WINDOW_SIZE * 2 * 2);

        AudioRecord audioRecord;
        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes
            );
        } catch (SecurityException | IllegalArgumentException e) {
            stopFromWorker(self);
            return;
        }

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release();
            stopFromWorker(self);
            return;
        }

        try {
            audioRecord.startRecording();

            short[] hop = new short[HOP_SIZE];
            float[] window = new float[WINDOW_SIZE];
            Yin yin = new Yin(SAMPLE_RATE, WINDOW_SIZE, MIN_FREQ, MAX_FREQ);
            int filled = 0;

            while (isActive(self)) {
                int read = readFully(audioRecord, hop, self);
                if (read < 0) break;
                if (read == 0) continue;

                // сдвигаем окно влево и дописываем новые сэмплы в конец
                System.arraycopy(window, read, window, 0, WINDOW_SIZE - read);
                for (int i = 0; i < read; i++) {
                    window[WINDOW_SIZE - read + i] = hop[i] / 32768f;
                }
                filled = Math.min(WINDOW_SIZE, filled + read);
                if (filled < WINDOW_SIZE) continue;

                double freq = rms(window) < SILENCE_RMS ? -1 : yin.detect(window);
                deliver(self, freq);
            }
        } catch (IllegalStateException ignored) {
        } finally {
            try {
                audioRecord.stop();
            } catch (IllegalStateException ignored) {
            }
            audioRecord.release();
            stopFromWorker(self);
        }
    }

    /** Читает до buffer.length сэмплов; -1 при ошибке AudioRecord. */
    private int readFully(AudioRecord audioRecord, short[] buffer, Thread self) {
        int total = 0;
        while (total < buffer.length && isActive(self)) {
            int read = audioRecord.read(buffer, total, buffer.length - total);
            if (read < 0) return -1;
            total += read;
        }
        return total;
    }

    private void deliver(Thread self, double freq) {
        if (listener == null) return;
        mainHandler.post(() -> {
            // stop() мог быть вызван, пока колбэк стоял в очереди
            if (!isActive(self)) return;
            if (freq > 0) listener.onPitchDetected(freq);
            else listener.onSilence();
        });
    }

    private void stopFromWorker(Thread self) {
        if (thread == self) thread = null;
    }

    private static double rms(float[] data) {
        double sum = 0;
        for (float v : data) sum += v * v;
        return Math.sqrt(sum / data.length);
    }

    /** Реализация YIN (de Cheveigné & Kawahara, 2002) с переиспользуемым буфером. */
    static final class Yin {

        private final int sampleRate;
        private final int tauMin;
        private final int tauMax;
        private final int integrationWindow;
        private final double[] diff;

        Yin(int sampleRate, int windowSize, double minFreq, double maxFreq) {
            this.sampleRate = sampleRate;
            this.tauMin = Math.max(2, (int) Math.floor(sampleRate / maxFreq));
            this.tauMax = Math.min(windowSize / 2, (int) Math.ceil(sampleRate / minFreq));
            this.integrationWindow = windowSize - tauMax;
            this.diff = new double[tauMax + 1];
        }

        /** Частота основного тона в Гц или -1, если чёткого тона нет. */
        double detect(float[] x) {
            // 1. разностная функция d(tau)
            for (int tau = 1; tau <= tauMax; tau++) {
                double sum = 0;
                for (int j = 0; j < integrationWindow; j++) {
                    double delta = x[j] - x[j + tau];
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
                if (diff[t] < YIN_THRESHOLD) {
                    while (t + 1 <= tauMax && diff[t + 1] < diff[t]) t++;
                    tau = t;
                    break;
                }
            }
            if (tau < 0) return -1;

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
    }
}
