package com.example.guitartuner.tuner;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

/**
 * Запись с микрофона и анализ звука (см. FrameAnalyzer): тон, громкость, атака, хромаграмма.
 * Новый кадр — каждые ~23 мс.
 *
 * Колбэки слушателя приходят в главном потоке и только пока детектор запущен —
 * после stop() ни один колбэк уже не будет вызван.
 */
public class PitchDetector {

    public interface PitchListener {
        void onPitchDetected(double frequencyHz);

        /**
         * То же, плюс громкость (RMS, 0..1) последних сэмплов — по её скачку
         * можно заметить новый щипок струны. По умолчанию громкость не нужна.
         */
        default void onPitchDetected(double frequencyHz, double level) {
            onPitchDetected(frequencyHz);
        }

        /** Сигнал слишком тихий. */
        default void onSilence() {
        }

        /** Звук есть, но одной чёткой ноты нет (например, звучит аккорд). */
        default void onNoPitch() {
            onSilence();
        }

        /**
         * Хромаграмма каждого не тихого кадра: энергия 12 нот (0 = C), максимум = 1.
         * Приходит, только если детектор создан с computeChroma = true.
         */
        default void onChroma(double[] chroma, double level) {
        }

        /** Весь кадр целиком. По умолчанию раскладывается на колбэки выше. */
        default void onFrame(AudioFrame frame) {
            if (frame.silent) {
                onSilence();
                return;
            }
            if (frame.chroma != null) onChroma(frame.chroma, frame.level);
            if (frame.frequency > 0) onPitchDetected(frame.frequency, frame.level);
            else onNoPitch();
        }
    }

    // источники по порядку предпочтения: VOICE_RECOGNITION на большинстве телефонов
    // идёт без автоусиления и шумоподавления, которые сглаживают атаку струны
    private static final int[] AUDIO_SOURCES = {
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
    };

    private final PitchListener listener;
    private final boolean computeChroma;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // текущий поток записи; null — детектор остановлен.
    // Поток работает, пока это поле указывает на него, поэтому быстрый stop()+start()
    // не оживит старый поток.
    private volatile Thread thread;

    public PitchDetector(PitchListener listener) {
        this(listener, false);
    }

    /** computeChroma = true — дополнительно считать хромаграмму для распознавания аккордов. */
    public PitchDetector(PitchListener listener, boolean computeChroma) {
        this.listener = listener;
        this.computeChroma = computeChroma;
    }

    public boolean isRunning() {
        return thread != null;
    }

    public void start() {
        if (thread != null) return;
        Thread t = new Thread(this::recordLoop, "PitchDetector");
        t.setPriority(Thread.MAX_PRIORITY);
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
        int sampleRate = FrameAnalyzer.SAMPLE_RATE;
        int hopSize = FrameAnalyzer.HOP_SIZE;

        int minBufferBytes = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        // getMinBufferSize возвращает БАЙТЫ; 1 сэмпл PCM16 = 2 байта
        int bufferBytes = Math.max(minBufferBytes, hopSize * 2 * 4);

        AudioRecord audioRecord = openRecord(sampleRate, bufferBytes);
        if (audioRecord == null) {
            stopFromWorker(self);
            return;
        }

        try {
            audioRecord.startRecording();

            short[] raw = new short[hopSize];
            float[] hop = new float[hopSize];
            FrameAnalyzer analyzer = new FrameAnalyzer(computeChroma);

            while (isActive(self)) {
                int read = readFully(audioRecord, raw, self);
                if (read < 0) break;
                if (read == 0) continue;

                for (int i = 0; i < read; i++) hop[i] = raw[i] / 32768f;
                AudioFrame frame = analyzer.process(hop, read);
                if (frame != null) deliver(self, frame);
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

    private static AudioRecord openRecord(int sampleRate, int bufferBytes) {
        for (int source : AUDIO_SOURCES) {
            try {
                AudioRecord record = new AudioRecord(source, sampleRate,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes);
                if (record.getState() == AudioRecord.STATE_INITIALIZED) return record;
                record.release();
            } catch (SecurityException | IllegalArgumentException ignored) {
            }
        }
        return null;
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

    private void deliver(Thread self, AudioFrame frame) {
        if (listener == null) return;
        mainHandler.post(() -> {
            // stop() мог быть вызван, пока колбэк стоял в очереди
            if (isActive(self)) listener.onFrame(frame);
        });
    }

    private void stopFromWorker(Thread self) {
        if (thread == self) thread = null;
    }
}
