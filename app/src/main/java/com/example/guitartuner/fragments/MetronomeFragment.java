package com.example.guitartuner.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.guitartuner.R;
import com.example.guitartuner.audio.AudioEngine;
import com.example.guitartuner.audio.ClickSound;
import com.example.guitartuner.ui.Anim;
import com.example.guitartuner.views.MetronomeView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.Slider;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Метроном: темп (в том числе "тапом"), размер такта, длительность щелчков,
 * звук и громкость. Визуализация синхронизирована с тем, что звучит из динамика.
 */
public class MetronomeFragment extends Fragment {

    private static final int MIN_BPM = 30;
    private static final int MAX_BPM = 250;
    private static final int MAX_BEATS = 12;
    // тапы дальше этого друг от друга — начало нового отсчёта
    private static final long TAP_RESET_MS = 2000;

    private static final String PREFS = "metronome";
    private static final String KEY_BPM = "bpm";
    private static final String KEY_BEATS = "beats";
    private static final String KEY_DENOMINATOR = "denominator";
    private static final String KEY_SUBDIVISION = "subdivision";
    private static final String KEY_SOUND = "sound";
    private static final String KEY_VOLUME = "volume";
    private static final String KEY_BEAT_TYPES = "beat_types";

    private TextView textBpm;
    private TextView textTempoName;
    private TextView textTimeSignature;
    private Slider sliderBpm;
    private MetronomeView metronomeView;
    private Button btnStart;

    private int bpm = 100;
    private int beats = 4;
    private int denominator = 4;
    private int subdivision = 1;
    private ClickSound sound = ClickSound.CLASSIC;
    private float volume = 0.8f;
    private int[] beatTypes = defaultBeatTypes(4);

    private final AudioEngine audioEngine = new AudioEngine();
    private boolean playing = false;
    private final ArrayDeque<Long> taps = new ArrayDeque<>();

    // кадр анимации: визуализация берёт состояние из звука
    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!playing || metronomeView == null) return;
            metronomeView.setState(audioEngine.getMetronomeState());
            metronomeView.postOnAnimation(this);
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_metronome, container, false);

        textBpm = view.findViewById(R.id.textBpm);
        textTempoName = view.findViewById(R.id.textTempoName);
        textTimeSignature = view.findViewById(R.id.textTimeSignature);
        sliderBpm = view.findViewById(R.id.sliderBpm);
        metronomeView = view.findViewById(R.id.metronomeView);
        btnStart = view.findViewById(R.id.btnMetronomeStart);

        restore();
        setupTempo(view);
        setupTimeSignature(view);
        setupSound(view);

        metronomeView.setOnBeatTapListener(beat -> {
            // акцент → обычная → без звука → акцент
            beatTypes[beat] = (beatTypes[beat] + 1) % 3;
            applyConfig();
        });

        btnStart.setOnClickListener(v -> {
            if (playing) stop();
            else start();
        });

        refreshUi();
        Anim.cascadeIn(view.findViewById(R.id.metronomeContent));
        return view;
    }

    // ---------- темп ----------

    private void setupTempo(View view) {
        sliderBpm.setValueFrom(MIN_BPM);
        sliderBpm.setValueTo(MAX_BPM);
        sliderBpm.setValue(bpm);
        sliderBpm.addOnChangeListener((s, value, fromUser) -> {
            if (fromUser) setBpm(Math.round(value));
        });

        view.findViewById(R.id.btnBpmDown).setOnClickListener(v -> setBpm(bpm - 1));
        view.findViewById(R.id.btnBpmUp).setOnClickListener(v -> setBpm(bpm + 1));
        view.findViewById(R.id.btnBpmMinus5).setOnClickListener(v -> setBpm(bpm - 5));
        view.findViewById(R.id.btnBpmPlus5).setOnClickListener(v -> setBpm(bpm + 5));
        view.findViewById(R.id.btnTap).setOnClickListener(v -> onTap());
    }

    private void setBpm(int value) {
        bpm = Math.max(MIN_BPM, Math.min(MAX_BPM, value));
        sliderBpm.setValue(bpm);
        applyConfig();
        Anim.pop(textBpm);
    }

    /** "Тап": темп по среднему интервалу между последними нажатиями. */
    private void onTap() {
        long now = SystemClock.elapsedRealtime();
        if (!taps.isEmpty() && now - taps.peekLast() > TAP_RESET_MS) taps.clear();
        taps.addLast(now);
        while (taps.size() > 6) taps.removeFirst();
        if (taps.size() < 2) return;

        double averageMs = (taps.peekLast() - taps.peekFirst()) / (double) (taps.size() - 1);
        setBpm((int) Math.round(60000 / averageMs));
    }

    // ---------- размер и длительность ----------

    private void setupTimeSignature(View view) {
        view.findViewById(R.id.btnBeatsDown).setOnClickListener(v -> setBeats(beats - 1));
        view.findViewById(R.id.btnBeatsUp).setOnClickListener(v -> setBeats(beats + 1));

        ChipGroup denominators = view.findViewById(R.id.chipGroupDenominator);
        int[] denIds = {R.id.chipDen2, R.id.chipDen4, R.id.chipDen8, R.id.chipDen16};
        int[] denValues = {2, 4, 8, 16};
        for (int i = 0; i < denIds.length; i++) {
            if (denValues[i] == denominator) denominators.check(denIds[i]);
        }
        denominators.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            for (int i = 0; i < denIds.length; i++) {
                if (denIds[i] == ids.get(0)) denominator = denValues[i];
            }
            applyConfig();
        });

        ChipGroup subdivisions = view.findViewById(R.id.chipGroupSubdivision);
        int[] subIds = {R.id.chipSub1, R.id.chipSub2, R.id.chipSub3, R.id.chipSub4};
        subdivisions.check(subIds[subdivision - 1]);
        subdivisions.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            for (int i = 0; i < subIds.length; i++) {
                if (subIds[i] == ids.get(0)) subdivision = i + 1;
            }
            applyConfig();
        });
    }

    private void setBeats(int value) {
        int newBeats = Math.max(1, Math.min(MAX_BEATS, value));
        if (newBeats == beats) return;
        int[] newTypes = Arrays.copyOf(beatTypes, newBeats);
        for (int i = beats; i < newBeats; i++) newTypes[i] = AudioEngine.MetronomeConfig.NORMAL;
        beats = newBeats;
        beatTypes = newTypes;
        applyConfig();
        Anim.pop(textTimeSignature);
    }

    private static int[] defaultBeatTypes(int beats) {
        int[] types = new int[beats];
        Arrays.fill(types, AudioEngine.MetronomeConfig.NORMAL);
        types[0] = AudioEngine.MetronomeConfig.ACCENT;
        return types;
    }

    // ---------- звук ----------

    private void setupSound(View view) {
        ChipGroup sounds = view.findViewById(R.id.chipGroupSound);
        for (ClickSound s : ClickSound.values()) {
            Chip chip = new Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle);
            chip.setId(View.generateViewId());
            chip.setText(s.label);
            chip.setCheckable(true);
            chip.setTag(s);
            sounds.addView(chip);
            if (s == sound) sounds.check(chip.getId());
        }
        sounds.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            sound = (ClickSound) group.findViewById(ids.get(0)).getTag();
            applyConfig();
            // короткое прослушивание звука, если метроном стоит
            if (!playing) previewSound();
        });

        Slider sliderVolume = view.findViewById(R.id.sliderVolume);
        sliderVolume.setValue(Math.round(volume * 20) * 5f);
        sliderVolume.addOnChangeListener((s, value, fromUser) -> {
            if (!fromUser) return;
            volume = value / 100f;
            applyConfig();
        });
    }

    private void previewSound() {
        audioEngine.startMetronome(new AudioEngine.MetronomeConfig(
                240, 1, 1, new int[]{AudioEngine.MetronomeConfig.ACCENT}, sound, volume));
        metronomeView.postDelayed(() -> {
            if (!playing) audioEngine.stopMetronome();
        }, 200);
    }

    // ---------- старт / стоп ----------

    private void start() {
        playing = true;
        audioEngine.startMetronome(config());
        btnStart.setText("СТОП");
        metronomeView.postOnAnimation(frame);
    }

    private void stop() {
        playing = false;
        audioEngine.stopMetronome();
        if (btnStart == null) return;
        btnStart.setText("СТАРТ");
        metronomeView.setState(null);
    }

    private AudioEngine.MetronomeConfig config() {
        return new AudioEngine.MetronomeConfig(bpm, beats, subdivision, beatTypes.clone(), sound, volume);
    }

    /** Новые настройки: в звук (на ходу), на экран и в память. */
    private void applyConfig() {
        if (playing) audioEngine.updateMetronome(config());
        refreshUi();
        save();
    }

    private void refreshUi() {
        textBpm.setText(String.valueOf(bpm));
        textTempoName.setText("BPM · " + tempoName(bpm));
        textTimeSignature.setText(beats + "/" + denominator);
        metronomeView.setBeats(beats, beatTypes, subdivision);
    }

    // итальянские названия темпов — как на механических метрономах
    private static String tempoName(int bpm) {
        if (bpm < 60) return "Largo";
        if (bpm < 66) return "Larghetto";
        if (bpm < 76) return "Adagio";
        if (bpm < 108) return "Andante";
        if (bpm < 120) return "Moderato";
        if (bpm < 156) return "Allegro";
        if (bpm < 176) return "Vivace";
        if (bpm < 200) return "Presto";
        return "Prestissimo";
    }

    // ---------- сохранение ----------

    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void restore() {
        SharedPreferences p = prefs();
        bpm = Math.max(MIN_BPM, Math.min(MAX_BPM, p.getInt(KEY_BPM, 100)));
        beats = Math.max(1, Math.min(MAX_BEATS, p.getInt(KEY_BEATS, 4)));
        denominator = p.getInt(KEY_DENOMINATOR, 4);
        subdivision = Math.max(1, Math.min(4, p.getInt(KEY_SUBDIVISION, 1)));
        volume = p.getFloat(KEY_VOLUME, 0.8f);
        try {
            sound = ClickSound.valueOf(p.getString(KEY_SOUND, ClickSound.CLASSIC.name()));
        } catch (IllegalArgumentException e) {
            sound = ClickSound.CLASSIC;
        }

        beatTypes = defaultBeatTypes(beats);
        String saved = p.getString(KEY_BEAT_TYPES, "");
        if (saved.length() == beats) {
            for (int i = 0; i < beats; i++) {
                int type = saved.charAt(i) - '0';
                if (type >= 0 && type <= 2) beatTypes[i] = type;
            }
        }
    }

    private void save() {
        StringBuilder types = new StringBuilder();
        for (int t : beatTypes) types.append(t);
        prefs().edit()
                .putInt(KEY_BPM, bpm)
                .putInt(KEY_BEATS, beats)
                .putInt(KEY_DENOMINATOR, denominator)
                .putInt(KEY_SUBDIVISION, subdivision)
                .putString(KEY_SOUND, sound.name())
                .putFloat(KEY_VOLUME, volume)
                .putString(KEY_BEAT_TYPES, types.toString())
                .apply();
    }

    // ---------- жизненный цикл ----------

    @Override
    public void onPause() {
        super.onPause();
        stop();
        audioEngine.release();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        playing = false;
        metronomeView = null;
        btnStart = null;
    }
}
