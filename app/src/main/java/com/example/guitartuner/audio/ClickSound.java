package com.example.guitartuner.audio;

import java.util.Random;

/** Звуки метронома. Каждый — три варианта: акцент, обычная доля, дробление доли. */
public enum ClickSound {
    CLASSIC("Классика"),
    WOOD("Дерево"),
    BEEP("Бип"),
    HIHAT("Хай-хэт");

    public static final int ACCENT = 0;
    public static final int BEAT = 1;
    public static final int SUBDIVISION = 2;

    public final String label;

    ClickSound(String label) {
        this.label = label;
    }

    /** level: ACCENT, BEAT или SUBDIVISION. */
    public float[] render(int sampleRate, int level) {
        float gain = level == ACCENT ? 0.9f : level == BEAT ? 0.6f : 0.35f;
        switch (this) {
            case WOOD:
                return wood(sampleRate, level, gain);
            case BEEP:
                return beep(sampleRate, level, gain);
            case HIHAT:
                return hihat(sampleRate, level, gain);
            case CLASSIC:
            default:
                return classic(sampleRate, level, gain);
        }
    }

    // короткий высокий "тик"
    private static float[] classic(int sr, int level, float gain) {
        double freq = level == ACCENT ? 3600 : level == BEAT ? 3000 : 2400;
        int length = (int) (0.025 * sr);
        float[] out = new float[length];
        for (int n = 0; n < length; n++) {
            double t = n / (double) sr;
            out[n] = (float) (gain * Math.exp(-t * 180) * Math.sin(2 * Math.PI * freq * t));
        }
        return out;
    }

    // деревянная коробочка: два затухающих резонанса и короткий шумовой удар
    private static float[] wood(int sr, int level, float gain) {
        double f1 = level == ACCENT ? 1150 : level == BEAT ? 950 : 800;
        double f2 = f1 * 2.71;
        int length = (int) (0.06 * sr);
        float[] out = new float[length];
        Random random = new Random(level);
        for (int n = 0; n < length; n++) {
            double t = n / (double) sr;
            double body = Math.sin(2 * Math.PI * f1 * t) * Math.exp(-t * 70)
                    + 0.4 * Math.sin(2 * Math.PI * f2 * t) * Math.exp(-t * 120);
            double knock = (random.nextDouble() * 2 - 1) * Math.exp(-t * 900);
            out[n] = (float) (gain * 0.7 * (body + 0.5 * knock));
        }
        return out;
    }

    // электронный бип: мягкий прямоугольник с плавными краями
    private static float[] beep(int sr, int level, float gain) {
        double freq = level == ACCENT ? 1760 : level == BEAT ? 880 : 660;
        int length = (int) (0.05 * sr);
        int edge = (int) (0.004 * sr);
        float[] out = new float[length];
        for (int n = 0; n < length; n++) {
            double t = n / (double) sr;
            double square = Math.signum(Math.sin(2 * Math.PI * freq * t))
                    + 0.3 * Math.sin(2 * Math.PI * freq * t);
            double env = Math.min(1, Math.min(n / (double) edge, (length - n) / (double) edge));
            out[n] = (float) (gain * 0.35 * square * env);
        }
        return out;
    }

    // хай-хэт: шум, из которого убраны низкие частоты
    private static float[] hihat(int sr, int level, float gain) {
        int length = (int) ((level == ACCENT ? 0.09 : 0.05) * sr);
        float[] out = new float[length];
        Random random = new Random(42 + level);
        double prev = 0;
        for (int n = 0; n < length; n++) {
            double t = n / (double) sr;
            double noise = random.nextDouble() * 2 - 1;
            double high = noise - prev; // простейший фильтр верхних частот
            prev = noise;
            out[n] = (float) (gain * 0.6 * high * Math.exp(-t * (level == ACCENT ? 45 : 80)));
        }
        return out;
    }
}
