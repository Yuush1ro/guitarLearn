package com.example.guitartuner.audio;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Звук гитарной струны по алгоритму Карплуса–Стронга: короткий шумовой импульс
 * многократно проходит по линии задержки длиной в один период ноты и сглаживается
 * фильтром — получается затухающий "щипок". Готовые звуки кэшируются по MIDI-номеру.
 */
public class GuitarSynth {

    private static final double DURATION_SEC = 1.6;
    private static final double FADE_OUT_SEC = 0.08;
    // чуть меньше 1: насколько быстро затухает струна
    private static final double DECAY = 0.996;
    private static final float GAIN = 0.35f;

    private final int sampleRate;
    private final Map<Integer, float[]> cache = new HashMap<>();
    private final Random random = new Random(1);

    public GuitarSynth(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    /** Звук ноты (моно, -1..1). Один и тот же массив для одной ноты — не изменять. */
    public synchronized float[] note(int midi) {
        float[] cached = cache.get(midi);
        if (cached != null) return cached;
        float[] samples = render(midi);
        cache.put(midi, samples);
        return samples;
    }

    private float[] render(int midi) {
        double freq = 440.0 * Math.pow(2, (midi - 69) / 12.0);

        // Период в сэмплах дробный, а линия задержки — целая. Остаток добираем
        // всепропускающим фильтром (иначе высокие ноты фальшивят на десятки центов).
        // Фильтр усреднения сам добавляет полсэмпла задержки.
        double delay = sampleRate / freq - 0.5;
        int period = (int) Math.floor(delay);
        double fraction = delay - period;
        if (fraction < 0.1) {
            period -= 1;
            fraction += 1;
        }
        double allpass = (1 - fraction) / (1 + fraction);

        double[] line = new double[period];
        // начальный импульс: шум, слегка сглаженный — звучит мягче, как щипок пальцем
        double prev = 0;
        for (int i = 0; i < period; i++) {
            double noise = random.nextDouble() * 2 - 1;
            prev = 0.5 * noise + 0.5 * prev;
            line[i] = prev;
        }

        int length = (int) (DURATION_SEC * sampleRate);
        int fadeOut = (int) (FADE_OUT_SEC * sampleRate);
        float[] out = new float[length];
        int pos = 0;
        double peak = 1e-9;
        double lastOut = 0;
        double apIn = 0;
        double apOut = 0;
        for (int n = 0; n < length; n++) {
            double value = line[pos];
            // усреднение двух соседних сэмплов — струна теряет высокие частоты
            double averaged = DECAY * 0.5 * (value + lastOut);
            lastOut = value;
            // всепропускающий фильтр первого порядка — дробная часть задержки
            double shifted = allpass * averaged + apIn - allpass * apOut;
            apIn = averaged;
            apOut = shifted;

            line[pos] = shifted;
            pos = pos + 1 == period ? 0 : pos + 1;
            out[n] = (float) value;
            peak = Math.max(peak, Math.abs(value));
        }

        float scale = (float) (GAIN / peak);
        for (int n = 0; n < length; n++) {
            float fade = n >= length - fadeOut ? (length - n) / (float) fadeOut : 1f;
            out[n] *= scale * fade;
        }
        return out;
    }

    /** Щелчок метронома: короткий высокий "тик" выше 2.5 кГц, чтобы микрофон не принял его за ноту. */
    public float[] click(boolean accent) {
        double freq = accent ? 3600 : 3000;
        int length = (int) (0.025 * sampleRate);
        float[] out = new float[length];
        float gain = accent ? 0.55f : 0.35f;
        for (int n = 0; n < length; n++) {
            double t = n / (double) sampleRate;
            double envelope = Math.exp(-t * 180);
            out[n] = (float) (gain * envelope * Math.sin(2 * Math.PI * freq * t));
        }
        return out;
    }
}
