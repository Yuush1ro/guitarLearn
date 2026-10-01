package com.example.guitartuner.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.SystemClock;

import com.example.guitartuner.models.TabNote;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Звук приложения: ноты-подсказки и метроном.
 *
 * Отдельный поток непрерывно пишет в AudioTrack смесь активных звуков. "Транспорт"
 * (воспроизведение по времени песни) тоже живёт в этом потоке: щелчки и ноты
 * запускаются с точностью до сэмпла, а getSongTimeMs() считает время песни по
 * реально проигранным сэмплам — картинка совпадает с тем, что слышно.
 */
public class AudioEngine {

    public static final int SAMPLE_RATE = 44100;
    private static final int CHUNK = 256;
    // задержка между струнами при "ударе" по аккорду
    private static final double STRUM_MS = 18;

    private final GuitarSynth synth = new GuitarSynth(SAMPLE_RATE);
    private final float[] clickAccent = synth.click(true);
    private final float[] clickNormal = synth.click(false);

    private final ConcurrentLinkedQueue<Runnable> commands = new ConcurrentLinkedQueue<>();
    private volatile Thread thread;
    private volatile AudioTrack track;

    // --- состояние, которое меняет только аудиопоток ---
    private final List<Voice> voices = new ArrayList<>();
    private long framesWritten = 0;
    private Schedule schedule;
    private int nextClick;
    private int nextStep;

    // --- метроном (отдельный экран): щелчки генерируются прямо в аудиопотоке ---
    private MetronomeConfig metronome;
    private ClickSound metronomeSound;
    private float[][] metronomeBuffers;
    private double nextClickFrame;
    private long clickCounter;
    // последние щелчки: кадр и доля — чтобы UI знал, что звучит сейчас
    private static final int TICK_HISTORY = 64;
    private final long[] tickFrames = new long[TICK_HISTORY];
    private final int[] tickBeats = new int[TICK_HISTORY];
    private final int[] tickSubs = new int[TICK_HISTORY];
    private int tickCount = 0;
    private final Object tickLock = new Object();
    private volatile MetronomeConfig activeMetronome;

    // снимок транспорта для чтения из UI-потока
    private volatile Transport transport;
    // до какого момента (uptime, мс) звучат подсказки playStep — чтобы не слушать их микрофоном
    private volatile long previewBusyUntil = 0;

    private static final class Voice {
        final float[] samples;
        int position; // < 0 — ещё не началась (задержка в сэмплах)
        final float gain;

        Voice(float[] samples, int delay, float gain) {
            this.samples = samples;
            this.position = -delay;
            this.gain = gain;
        }
    }

    /** Что играть по времени песни. Все времена — в миллисекундах песни (при скорости 100%). */
    public static final class Schedule {
        final double[] clickTimes;
        final boolean[] clickAccents;
        // null — ноты не озвучивать
        final double[] stepTimes;
        final List<TabNote> steps;

        public Schedule(double[] clickTimes, boolean[] clickAccents, double[] stepTimes, List<TabNote> steps) {
            this.clickTimes = clickTimes;
            this.clickAccents = clickAccents;
            this.stepTimes = stepTimes;
            this.steps = steps;
        }
    }

    private static final class Transport {
        final long startFrame;
        final double startSongMs;
        final double speed;

        Transport(long startFrame, double startSongMs, double speed) {
            this.startFrame = startFrame;
            this.startSongMs = startSongMs;
            this.speed = speed;
        }

        double songMsAtFrame(long frame) {
            return startSongMs + (frame - startFrame) * 1000.0 / SAMPLE_RATE * speed;
        }
    }

    /** Настройки метронома. Доли: 0 — акцент, 1 — обычная, 2 — без звука. */
    public static final class MetronomeConfig {
        public static final int ACCENT = 0;
        public static final int NORMAL = 1;
        public static final int MUTE = 2;

        public final double bpm;
        public final int beats;
        public final int subdivision;
        public final int[] beatTypes;
        public final ClickSound sound;
        public final float volume;

        public MetronomeConfig(double bpm, int beats, int subdivision, int[] beatTypes,
                               ClickSound sound, float volume) {
            this.bpm = bpm;
            this.beats = beats;
            this.subdivision = subdivision;
            this.beatTypes = beatTypes;
            this.sound = sound;
            this.volume = volume;
        }
    }

    /** Что звучит сейчас: доля такта, дробление и фаза (0..1) внутри доли. */
    public static final class MetronomeState {
        public final int beat;
        public final int subdivision;
        public final double beatPhase;

        MetronomeState(int beat, int subdivision, double beatPhase) {
            this.beat = beat;
            this.subdivision = subdivision;
            this.beatPhase = beatPhase;
        }
    }

    // ---------- управление (из главного потока) ----------

    /** Запустить метроном с первой доли. */
    public void startMetronome(MetronomeConfig config) {
        start();
        activeMetronome = config;
        commands.add(() -> {
            metronome = config;
            prepareMetronomeSound(config.sound);
            // первый щелчок — через 50 мс, чтобы не обрезать его начало
            nextClickFrame = framesWritten + 0.05 * SAMPLE_RATE;
            clickCounter = 0;
            synchronized (tickLock) {
                tickCount = 0;
            }
        });
    }

    /**
     * Поменять настройки на ходу. Темп и звук меняются со следующего щелчка;
     * при смене размера или дробления счёт начинается с первой доли.
     */
    public void updateMetronome(MetronomeConfig config) {
        activeMetronome = config;
        commands.add(() -> {
            if (metronome == null) return;
            boolean restartBar = config.beats != metronome.beats
                    || config.subdivision != metronome.subdivision;
            metronome = config;
            prepareMetronomeSound(config.sound);
            if (restartBar) clickCounter = 0;
        });
    }

    public void stopMetronome() {
        activeMetronome = null;
        commands.add(() -> metronome = null);
    }

    /** Текущая доля метронома или null, если он не играет (или ещё не прозвучал первый щелчок). */
    public MetronomeState getMetronomeState() {
        MetronomeConfig config = activeMetronome;
        AudioTrack at = track;
        if (config == null || at == null) return null;
        long played = at.getPlaybackHeadPosition() & 0xFFFFFFFFL;

        synchronized (tickLock) {
            int count = Math.min(tickCount, TICK_HISTORY);
            for (int k = 0; k < count; k++) {
                int i = (tickCount - 1 - k) % TICK_HISTORY;
                if (tickFrames[i] <= played) {
                    double framesPerSub = SAMPLE_RATE * 60.0 / config.bpm / config.subdivision;
                    double subPhase = Math.min(1, (played - tickFrames[i]) / framesPerSub);
                    double beatPhase = (tickSubs[i] + subPhase) / config.subdivision;
                    return new MetronomeState(tickBeats[i], tickSubs[i], beatPhase);
                }
            }
        }
        return null;
    }

    public void start() {
        if (thread != null) return;
        Thread t = new Thread(this::audioLoop, "AudioEngine");
        thread = t;
        t.start();
    }

    /** Останавливает звук и освобождает AudioTrack. */
    public void release() {
        thread = null;
        transport = null;
        activeMetronome = null;
    }

    /** Сразу проиграть ноту или аккорд (подсказка "как это звучит"). */
    public void playStep(TabNote step) {
        start();
        long durationMs = (long) (1600 + STRUM_MS * step.getNotes().size());
        previewBusyUntil = SystemClock.uptimeMillis() + durationMs;
        commands.add(() -> addStepVoices(step, 0));
    }

    /** true — сейчас звучит подсказка, микрофон её услышит. */
    public boolean isPreviewPlaying() {
        return SystemClock.uptimeMillis() < previewBusyUntil;
    }

    /**
     * Запустить воспроизведение по времени песни с позиции fromSongMs.
     * speed: 1.0 — исходный темп, 0.5 — вдвое медленнее.
     */
    public void startTransport(Schedule schedule, double fromSongMs, double speed) {
        start();
        commands.add(() -> {
            this.schedule = schedule;
            this.nextClick = firstIndexAtOrAfter(schedule.clickTimes, fromSongMs);
            this.nextStep = schedule.stepTimes == null ? 0
                    : firstIndexAtOrAfter(schedule.stepTimes, fromSongMs);
            transport = new Transport(framesWritten, fromSongMs, speed);
        });
    }

    public void stopTransport() {
        commands.add(() -> {
            schedule = null;
            transport = null;
            voices.clear();
        });
        transport = null;
    }

    public boolean isTransportRunning() {
        return transport != null;
    }

    /**
     * Время песни (мс), которое сейчас звучит из динамика; NaN — транспорт не запущен.
     * До старта (во время отсчёта) значение меньше начальной позиции.
     */
    public double getSongTimeMs() {
        Transport t = transport;
        AudioTrack at = track;
        if (t == null || at == null) return Double.NaN;
        long played = at.getPlaybackHeadPosition() & 0xFFFFFFFFL;
        return t.songMsAtFrame(played);
    }

    // ---------- аудиопоток ----------

    private void audioLoop() {
        Thread self = Thread.currentThread();

        int minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT);
        AudioTrack at;
        try {
            at = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(minBuffer, CHUNK * 4 * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
        } catch (RuntimeException e) {
            if (thread == self) thread = null;
            return;
        }

        framesWritten = 0;
        voices.clear();
        schedule = null;
        // метроном прошлого запуска не продолжаем; новый придёт командой startMetronome
        metronome = null;
        track = at;
        float[] buffer = new float[CHUNK];

        try {
            at.play();
            while (thread == self) {
                Runnable command;
                while ((command = commands.poll()) != null) command.run();

                renderChunk(buffer);
                int written = at.write(buffer, 0, CHUNK, AudioTrack.WRITE_BLOCKING);
                if (written < 0) break;
                framesWritten += written;
            }
        } catch (RuntimeException ignored) {
        } finally {
            track = null;
            transport = null;
            try {
                at.stop();
            } catch (IllegalStateException ignored) {
            }
            at.release();
            if (thread == self) thread = null;
        }
    }

    private void renderChunk(float[] buffer) {
        java.util.Arrays.fill(buffer, 0f);

        Transport t = transport;
        Schedule s = schedule;
        if (t != null && s != null) {
            double from = t.songMsAtFrame(framesWritten);
            double to = t.songMsAtFrame(framesWritten + CHUNK);

            while (nextClick < s.clickTimes.length && s.clickTimes[nextClick] < to) {
                int offset = offsetInChunk(t, s.clickTimes[nextClick], from);
                voices.add(new Voice(s.clickAccents[nextClick] ? clickAccent : clickNormal, offset, 1f));
                nextClick++;
            }
            if (s.stepTimes != null) {
                while (nextStep < s.stepTimes.length && s.stepTimes[nextStep] < to) {
                    addStepVoices(s.steps.get(nextStep), offsetInChunk(t, s.stepTimes[nextStep], from));
                    nextStep++;
                }
            }
        }

        renderMetronome();

        for (int v = voices.size() - 1; v >= 0; v--) {
            Voice voice = voices.get(v);
            for (int i = 0; i < CHUNK; i++) {
                int p = voice.position + i;
                if (p >= 0 && p < voice.samples.length) buffer[i] += voice.samples[p] * voice.gain;
            }
            voice.position += CHUNK;
            if (voice.position >= voice.samples.length) voices.remove(v);
        }

        for (int i = 0; i < CHUNK; i++) {
            buffer[i] = Math.max(-1f, Math.min(1f, buffer[i]));
        }
    }

    private void prepareMetronomeSound(ClickSound sound) {
        if (sound == metronomeSound && metronomeBuffers != null) return;
        metronomeSound = sound;
        metronomeBuffers = new float[][]{
                sound.render(SAMPLE_RATE, ClickSound.ACCENT),
                sound.render(SAMPLE_RATE, ClickSound.BEAT),
                sound.render(SAMPLE_RATE, ClickSound.SUBDIVISION)
        };
    }

    private void renderMetronome() {
        MetronomeConfig m = metronome;
        if (m == null) return;
        double framesPerSub = SAMPLE_RATE * 60.0 / m.bpm / m.subdivision;

        while (nextClickFrame < framesWritten + CHUNK) {
            int offset = (int) Math.max(0, Math.round(nextClickFrame - framesWritten));
            int sub = (int) (clickCounter % m.subdivision);
            int beat = (int) ((clickCounter / m.subdivision) % m.beats);
            int type = beat < m.beatTypes.length ? m.beatTypes[beat] : MetronomeConfig.NORMAL;

            if (sub > 0) {
                voices.add(new Voice(metronomeBuffers[ClickSound.SUBDIVISION], offset, m.volume));
            } else if (type != MetronomeConfig.MUTE) {
                int level = type == MetronomeConfig.ACCENT ? ClickSound.ACCENT : ClickSound.BEAT;
                voices.add(new Voice(metronomeBuffers[level], offset, m.volume));
            }

            synchronized (tickLock) {
                int i = tickCount % TICK_HISTORY;
                tickFrames[i] = Math.round(nextClickFrame);
                tickBeats[i] = beat;
                tickSubs[i] = sub;
                tickCount++;
            }
            clickCounter++;
            nextClickFrame += framesPerSub;
        }
    }

    private int offsetInChunk(Transport t, double songMs, double chunkStartSongMs) {
        double frames = (songMs - chunkStartSongMs) / 1000.0 * SAMPLE_RATE / t.speed;
        return (int) Math.max(0, Math.min(CHUNK - 1, Math.round(frames)));
    }

    // аккорд — "удар" по струнам от низкой к высокой с небольшой задержкой
    private void addStepVoices(TabNote step, int delayFrames) {
        List<TabNote> notes = step.getNotes();
        float gain = 1f / (float) Math.sqrt(notes.size());
        int strumFrames = (int) (STRUM_MS / 1000.0 * SAMPLE_RATE);
        for (int i = notes.size() - 1, k = 0; i >= 0; i--, k++) {
            voices.add(new Voice(synth.note(notes.get(i).getMidi()), delayFrames + k * strumFrames, gain));
        }
    }

    private static int firstIndexAtOrAfter(double[] times, double value) {
        int lo = 0;
        int hi = times.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (times[mid] < value) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }
}
