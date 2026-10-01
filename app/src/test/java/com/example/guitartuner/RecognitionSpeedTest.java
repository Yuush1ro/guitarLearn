package com.example.guitartuner;

import static org.junit.Assert.assertTrue;

import com.example.guitartuner.audio.GuitarSynth;
import com.example.guitartuner.models.LessonLibrary;
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
 * "Музыкант" ровно играет урок в быстром темпе (синтез гитарной струны, разброс ±15 мс,
 * шум) — распознавание должно успевать засчитывать почти все ноты.
 */
public class RecognitionSpeedTest {

    private static final int SR = FrameAnalyzer.SAMPLE_RATE;
    private static final double START_MS = 300;
    private final GuitarSynth synth = new GuitarSynth(SR);

    private float[] perform(List<TabNote> steps, double intervalMs, Random rnd) {
        int total = (int) ((steps.size() * intervalMs + 1500) / 1000 * SR);
        float[] out = new float[total];
        int[] onsets = new int[steps.size() + 1];
        for (int i = 0; i < steps.size(); i++) {
            double jitter = (rnd.nextDouble() - 0.5) * 30;
            onsets[i] = (int) ((START_MS + i * intervalMs + jitter) / 1000 * SR);
        }
        onsets[steps.size()] = total;
        int fade = (int) (0.015 * SR);
        for (int i = 0; i < steps.size(); i++) {
            List<TabNote> notes = steps.get(i).getNotes();
            double vol = (0.6 + rnd.nextDouble() * 0.6) / Math.sqrt(notes.size());
            for (int k = 0; k < notes.size(); k++) {
                float[] s = synth.note(notes.get(notes.size() - 1 - k).getMidi());
                int start = onsets[i] + k * (int) (0.018 * SR);
                for (int n = 0; n < s.length && start + n < total; n++) {
                    int pos = start + n;
                    double g = vol;
                    // нота глушится, когда звучит следующая (как при игре мелодии)
                    if (pos >= onsets[i + 1]) {
                        int into = pos - onsets[i + 1];
                        if (into >= fade) break;
                        g *= 1 - into / (double) fade;
                    }
                    out[pos] += (float) (s[n] * g);
                }
            }
        }
        for (int i = 0; i < total; i++) out[i] += (float) (0.003 * rnd.nextGaussian());
        return out;
    }

    private double freeRate(List<TabNote> steps, double intervalMs) {
        byte[] results = new byte[steps.size()];
        StepMatcher matcher = new StepMatcher(steps, results);
        feed(perform(steps, intervalMs, new Random(11)), hasChords(steps), (frame, nowMs) -> matcher.onFrame(frame));
        return hitRate(results);
    }

    private double timedRate(List<TabNote> steps, double intervalMs) {
        double[] start = new double[steps.size()];
        double[] duration = new double[steps.size()];
        for (int i = 0; i < start.length; i++) {
            start[i] = START_MS + i * intervalMs;
            duration[i] = intervalMs;
        }
        PlayTiming timing = new PlayTiming(60000 / intervalMs, start, duration, new double[0], new boolean[0]);
        byte[] results = new byte[steps.size()];
        TimedMatcher matcher = new TimedMatcher(steps, timing, results);
        feed(perform(steps, intervalMs, new Random(12)), hasChords(steps), (frame, nowMs) -> {
            matcher.onFrame(frame, nowMs, 1.0);
            matcher.onTick(nowMs, 1.0);
        });
        return hitRate(results);
    }

    private interface FrameSink {
        void accept(AudioFrame frame, double nowMs);
    }

    private static void feed(float[] audio, boolean chroma, FrameSink sink) {
        FrameAnalyzer analyzer = new FrameAnalyzer(chroma);
        float[] hop = new float[FrameAnalyzer.HOP_SIZE];
        for (int p = 0; p + hop.length <= audio.length; p += hop.length) {
            System.arraycopy(audio, p, hop, 0, hop.length);
            AudioFrame frame = analyzer.process(hop, hop.length);
            if (frame != null) sink.accept(frame, (p + hop.length) * 1000.0 / SR);
        }
    }

    private static double hitRate(byte[] results) {
        int hits = 0;
        for (byte r : results) if (r == StepMatcher.HIT) hits++;
        return hits / (double) results.length;
    }

    private static boolean hasChords(List<TabNote> steps) {
        for (TabNote s : steps) if (s.isChord()) return true;
        return false;
    }

    @Test
    public void fastChromaticIsRecognized() {
        List<TabNote> chromatic = LessonLibrary.chromaticCascade(0, 4);
        // 8 нот в секунду — шестнадцатые на 120 BPM
        assertTrue(freeRate(chromatic, 125) >= 0.95);
        assertTrue(timedRate(chromatic, 125) >= 0.95);
    }

    @Test
    public void repeatedNotesNeedNewPluckButAreCounted() {
        List<TabNote> melody = new ArrayList<>();
        for (int m : new int[]{64, 64, 64, 62, 60, 60, 59, 59, 57, 57, 57, 55, 57, 59, 60, 60}) {
            melody.add(new TabNote(1, 0, m));
        }
        assertTrue(freeRate(melody, 250) >= 0.85);
        assertTrue(timedRate(melody, 250) >= 0.85);
    }

    @Test
    public void chordChangesAreRecognized() {
        List<TabNote> chords = new ArrayList<>();
        int[][] progression = {{40, 47, 52, 56, 59, 64}, {45, 52, 57, 60, 64},
                {48, 52, 55, 60, 64}, {43, 47, 50, 55, 59, 67}, {50, 57, 62, 66}};
        for (int[] chord : progression) {
            List<TabNote> notes = new ArrayList<>();
            for (int m : chord) notes.add(new TabNote(1, 0, m));
            chords.add(TabNote.chord(notes, ""));
        }
        // 3 аккорда в секунду
        assertTrue(freeRate(chords, 333) >= 0.9);
        assertTrue(timedRate(chords, 333) >= 0.9);
    }
}
