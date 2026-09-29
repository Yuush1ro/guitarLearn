package com.example.guitartuner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.guitartuner.tuner.ChordMatcher;
import com.example.guitartuner.tuner.ChordNamer;
import com.example.guitartuner.tuner.ChromaAnalyzer;

import org.junit.Test;

import java.util.Random;

/**
 * Распознавание аккордов на синтетическом "гитарном" звуке: гармоники со случайной
 * силой, негармоничность, затухание, шум. Проверяем долю кадров, где аккорд засчитан.
 */
public class ChordRecognitionTest {

    private static final int SR = 44100;
    private static final int N = 8192;
    private static final int TRIALS = 60;

    private static final int[] E = {40, 47, 52, 56, 59, 64};
    private static final int[] EM = {40, 47, 52, 55, 59, 64};
    private static final int[] AM = {45, 52, 57, 60, 64};
    private static final int[] A = {45, 52, 57, 61, 64};
    private static final int[] C = {48, 52, 55, 60, 64};
    private static final int[] G = {43, 47, 50, 55, 59, 67};
    private static final int[] D = {50, 57, 62, 66};
    private static final int[] F = {41, 48, 53, 57, 60, 65};

    private final ChromaAnalyzer analyzer = new ChromaAnalyzer(SR, N);
    private final Random random = new Random(7);

    private float[] strum(int[] midis) {
        float[] x = new float[N];
        double t0 = 0.05 + random.nextDouble() * 0.3;
        for (int m : midis) {
            double f0 = 440 * Math.pow(2, (m - 69) / 12.0) * (1 + (random.nextDouble() - 0.5) * 0.006);
            double vol = 0.5 + random.nextDouble();
            double decay = 1.5 + random.nextDouble() * 2;
            for (int h = 1; h <= 10; h++) {
                double fh = f0 * h * Math.sqrt(1 + 0.0002 * h * h);
                double amp = vol / h * (0.4 + random.nextDouble() * 1.2);
                double phase = random.nextDouble() * 2 * Math.PI;
                double hd = decay * (1 + 0.3 * h);
                for (int i = 0; i < N; i++) {
                    double t = t0 + i / (double) SR;
                    x[i] += (float) (amp * Math.exp(-hd * t) * Math.sin(2 * Math.PI * fh * t + phase));
                }
            }
        }
        for (int i = 0; i < N; i++) x[i] = (float) (x[i] * 0.05 + 0.002 * random.nextGaussian());
        return x;
    }

    private static boolean[] pitchClasses(int[] midis) {
        boolean[] result = new boolean[12];
        for (int m : midis) result[m % 12] = true;
        return result;
    }

    private double matchRate(int[] played, int[] expected) {
        int matched = 0;
        for (int i = 0; i < TRIALS; i++) {
            if (ChordMatcher.matches(analyzer.analyze(strum(played)), pitchClasses(expected))) matched++;
        }
        return matched / (double) TRIALS;
    }

    @Test
    public void rightChordIsRecognized() {
        for (int[] chord : new int[][]{E, EM, AM, A, C, G, D, F}) {
            assertTrue(matchRate(chord, chord) >= 0.85);
        }
    }

    @Test
    public void wrongChordIsRejected() {
        int[][][] pairs = {{EM, E}, {E, EM}, {A, AM}, {AM, A}, {C, AM}, {AM, C}, {G, C}, {F, AM}};
        for (int[][] pair : pairs) {
            assertTrue(matchRate(pair[0], pair[1]) <= 0.1);
        }
    }

    @Test
    public void singleBassNoteIsNotAFullChord() {
        assertTrue(matchRate(new int[]{40}, E) <= 0.1);
        assertTrue(matchRate(new int[]{45}, A) <= 0.1);
    }

    @Test
    public void chordNames() {
        assertEquals("E", ChordNamer.name(pitchClasses(E), 4));
        assertEquals("Am", ChordNamer.name(pitchClasses(AM), 9));
        assertEquals("G", ChordNamer.name(pitchClasses(G), 7));
        assertEquals("E5", ChordNamer.name(pitchClasses(new int[]{40, 47, 52}), 4));
        assertEquals("C/E", ChordNamer.name(pitchClasses(new int[]{52, 55, 60, 64}), 4));
        assertEquals("Dsus4", ChordNamer.name(pitchClasses(new int[]{50, 57, 62, 67}), 2));
        assertNull(ChordNamer.name(pitchClasses(new int[]{40}), 4));
    }
}
