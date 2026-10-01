package com.example.guitartuner;

import static org.junit.Assert.assertTrue;

import com.example.guitartuner.audio.GuitarSynth;
import com.example.guitartuner.models.PlayTiming;
import com.example.guitartuner.models.TabNote;
import com.example.guitartuner.practice.StepMatcher;
import com.example.guitartuner.practice.TimedMatcher;
import com.example.guitartuner.tuner.AudioFrame;
import com.example.guitartuner.tuner.FrameAnalyzer;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Сценарии "как в песне": струны звенят дальше (бой по одному аккорду, нота поверх
 * аккорда), а звук приходит с задержкой микрофона телефона.
 */
public class SongRecognitionTest {

    private static final int SR = FrameAnalyzer.SAMPLE_RATE;
    private static final double START_MS = 300;
    private final GuitarSynth synth = new GuitarSynth(SR);

    // струны не глушатся: при новом ударе прежний звук лишь стихает вдвое
    private float[] performRinging(List<TabNote> steps, double intervalMs, double latencyMs, Random rnd) {
        int total = (int) ((steps.size() * intervalMs + 2000 + latencyMs) / 1000 * SR);
        float[] out = new float[total];
        int[] onsets = new int[steps.size() + 1];
        for (int i = 0; i < steps.size(); i++) {
            double jitter = (rnd.nextDouble() - 0.5) * 30;
            onsets[i] = (int) ((START_MS + latencyMs + i * intervalMs + jitter) / 1000 * SR);
        }
        onsets[steps.size()] = total;
        for (int i = 0; i < steps.size(); i++) {
            List<TabNote> notes = steps.get(i).getNotes();
            double vol = (0.6 + rnd.nextDouble() * 0.6) / Math.sqrt(notes.size());
            for (int k = 0; k < notes.size(); k++) {
                float[] s = synth.note(notes.get(notes.size() - 1 - k).getMidi());
                int start = onsets[i] + k * (int) (0.012 * SR);
                for (int n = 0; n < s.length && start + n < total; n++) {
                    int pos = start + n;
                    double g = pos >= onsets[i + 1] ? vol * 0.5 : vol;
                    out[pos] += (float) (s[n] * g);
                }
            }
        }
        for (int i = 0; i < total; i++) out[i] += (float) (0.003 * rnd.nextGaussian());
        return out;
    }

    private static void feed(float[] audio, FrameSink sink) {
        FrameAnalyzer analyzer = new FrameAnalyzer(true);
        float[] hop = new float[FrameAnalyzer.HOP_SIZE];
        for (int p = 0; p + hop.length <= audio.length; p += hop.length) {
            System.arraycopy(audio, p, hop, 0, hop.length);
            AudioFrame frame = analyzer.process(hop, hop.length);
            if (frame != null) sink.accept(frame, (p + hop.length) * 1000.0 / SR);
        }
    }

    private interface FrameSink {
        void accept(AudioFrame frame, double nowMs);
    }

    private double freeRate(List<TabNote> steps, double intervalMs) {
        double sum = 0;
        for (int trial = 0; trial < 3; trial++) {
            byte[] results = new byte[steps.size()];
            StepMatcher matcher = new StepMatcher(steps, results);
            feed(performRinging(steps, intervalMs, 0, new Random(trial)), (f, now) -> matcher.onFrame(f));
            sum += hitRate(results);
        }
        return sum / 3;
    }

    private double timedRate(List<TabNote> steps, double intervalMs, double latencyMs) {
        double[] start = new double[steps.size()];
        double[] duration = new double[steps.size()];
        for (int i = 0; i < start.length; i++) {
            start[i] = START_MS + i * intervalMs;
            duration[i] = intervalMs;
        }
        PlayTiming timing = new PlayTiming(60000 / intervalMs, start, duration, new double[0], new boolean[0]);
        double sum = 0;
        for (int trial = 0; trial < 3; trial++) {
            byte[] results = new byte[steps.size()];
            TimedMatcher matcher = new TimedMatcher(steps, timing, results);
            feed(performRinging(steps, intervalMs, latencyMs, new Random(trial + 10)), (f, now) -> {
                matcher.onFrame(f, now, 1.0);
                matcher.onTick(now, 1.0);
            });
            sum += hitRate(results);
        }
        return sum / 3;
    }

    private static double hitRate(byte[] results) {
        int hits = 0;
        for (byte r : results) if (r == StepMatcher.HIT) hits++;
        return hits / (double) results.length;
    }

    private static TabNote chord(int... midis) {
        List<TabNote> notes = new ArrayList<>();
        for (int m : midis) notes.add(new TabNote(1, 0, m));
        return TabNote.chord(notes, "");
    }

    @Test
    public void strummingSameChordRepeatedly() {
        List<TabNote> strum = new ArrayList<>();
        TabNote[] progression = {chord(45, 52, 57, 60, 64), chord(48, 52, 55, 60, 64),
                chord(43, 47, 50, 55, 59, 67), chord(40, 47, 52, 55, 59, 64)};
        for (TabNote c : progression) for (int k = 0; k < 6; k++) strum.add(c);
        // 4 удара в секунду, струны звенят
        assertTrue(freeRate(strum, 250) >= 0.9);
        // и с задержкой микрофона 150 мс — она подстраивается сама
        assertTrue(timedRate(strum, 250, 150) >= 0.85);
    }

    @Test
    public void melodyOverRingingChords() {
        TabNote am = chord(45, 52, 57, 60, 64);
        TabNote c = chord(48, 52, 55, 60, 64);
        List<TabNote> song = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            song.add(am);
            song.add(new TabNote(1, 0, 64));
            song.add(new TabNote(1, 0, 64));
            song.add(new TabNote(1, 0, 62));
            song.add(c);
            song.add(c);
            song.add(new TabNote(1, 0, 60));
            song.add(new TabNote(1, 0, 59));
        }
        assertTrue(freeRate(song, 250) >= 0.8);
        assertTrue(timedRate(song, 250, 120) >= 0.8);
    }
}
