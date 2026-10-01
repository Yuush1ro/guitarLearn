package com.example.guitartuner.fragments;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.guitartuner.R;
import com.example.guitartuner.audio.AudioEngine;
import com.example.guitartuner.models.ChordLibrary;
import com.example.guitartuner.models.ChordShape;
import com.example.guitartuner.models.TabNote;
import com.example.guitartuner.practice.MatchCounter;
import com.example.guitartuner.practice.StepMatcher;
import com.example.guitartuner.tuner.AudioFrame;
import com.example.guitartuner.tuner.ChordNamer;
import com.example.guitartuner.tuner.NoteUtils;
import com.example.guitartuner.tuner.PitchDetector;
import com.example.guitartuner.ui.Anim;
import com.example.guitartuner.views.ChordDiagramView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

/**
 * Аккорды: библиотека стандартных аккордов и конструктор. Аккорд можно послушать
 * и сыграть — микрофон проверит, верно ли он звучит. В конструкторе пальцы ставятся
 * нажатием на диаграмму, а приложение называет получившийся аккорд.
 */
public class ChordsFragment extends Fragment {

    private static final String PREFS = "chords";
    private static final String KEY_MODE_BUILDER = "mode_builder";
    private static final String KEY_LIBRARY_CHORD = "library_chord";
    private static final String KEY_BUILDER_FRETS = "builder_frets";
    private static final String KEY_BUILDER_BASE = "builder_base_fret";
    private static final int MAX_BASE_FRET = 15;

    private MaterialButtonToggleGroup toggleMode;
    private View libraryCard;
    private View builderPanel;
    private TextView textCardTitle;
    private TextView textChordName;
    private TextView textChordInfo;
    private TextView textChordNotes;
    private TextView textHeard;
    private TextView textScore;
    private TextView textBaseFret;
    private ChordDiagramView diagram;
    private Button btnPlay;
    private View flashOverlay;

    private boolean builderMode = false;
    private ChordShape libraryShape;
    private Chip selectedChip;
    private int[] builderFrets = {ChordShape.MUTED, ChordShape.MUTED, ChordShape.MUTED,
            ChordShape.MUTED, ChordShape.MUTED, ChordShape.MUTED};
    private int builderBaseFret = 1;

    // что сейчас проверяем при игре; null — нечего играть
    private TabNote target;
    private int score = 0;
    private int matchFrames = 0;
    private final MatchCounter matchCounter = new MatchCounter();
    // после засчитанного аккорда ждём новый удар, иначе звенящие струны засчитаются снова
    private boolean needOnset = false;
    private long lastHitMs = Long.MIN_VALUE / 2;

    private final AudioEngine audioEngine = new AudioEngine();
    private final PitchDetector pitchDetector = new PitchDetector(new PitchDetector.PitchListener() {
        @Override
        public void onPitchDetected(double frequencyHz) {
            // не вызывается: кадры целиком приходят в onFrame
        }

        @Override
        public void onFrame(AudioFrame frame) {
            onAudioFrame(frame);
        }
    }, true);

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startListening();
                else Toast.makeText(requireContext(),
                        "Без доступа к микрофону проверка аккорда не работает", Toast.LENGTH_SHORT).show();
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chords, container, false);

        toggleMode = view.findViewById(R.id.toggleChordMode);
        libraryCard = view.findViewById(R.id.libraryCard);
        builderPanel = view.findViewById(R.id.builderPanel);
        textCardTitle = view.findViewById(R.id.textChordCardTitle);
        textChordName = view.findViewById(R.id.textChordName);
        textChordInfo = view.findViewById(R.id.textChordInfo);
        textChordNotes = view.findViewById(R.id.textChordNotes);
        textHeard = view.findViewById(R.id.textChordHeard);
        textScore = view.findViewById(R.id.textChordScore);
        textBaseFret = view.findViewById(R.id.textBaseFret);
        diagram = view.findViewById(R.id.chordDiagram);
        btnPlay = view.findViewById(R.id.btnChordPlay);
        flashOverlay = view.findViewById(R.id.flashOverlay);

        restore();
        buildLibrary(view.findViewById(R.id.libraryGroups));
        setupBuilder(view);

        toggleMode.check(builderMode ? R.id.btnModeBuilder : R.id.btnModeLibrary);
        toggleMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            stopListening();
            builderMode = checkedId == R.id.btnModeBuilder;
            prefs().edit().putBoolean(KEY_MODE_BUILDER, builderMode).apply();
            applyMode();
        });
        applyMode();

        view.findViewById(R.id.btnChordListen).setOnClickListener(v -> {
            if (target != null) audioEngine.playStep(target);
        });
        btnPlay.setOnClickListener(v -> {
            if (pitchDetector.isRunning()) stopListening();
            else startListening();
        });

        Anim.cascadeIn(view.findViewById(R.id.chordsContent));
        return view;
    }

    // ---------- библиотека ----------

    private void buildLibrary(LinearLayout container) {
        for (ChordLibrary.Group group : ChordLibrary.groups()) {
            TextView label = new TextView(requireContext());
            label.setText(group.title);
            label.setTextAppearance(R.style.TextAppearance_GuitarTuner_Label);
            label.setPadding(0, dp(10), 0, 0);
            container.addView(label);

            ChipGroup chips = new ChipGroup(requireContext());
            for (ChordShape shape : group.chords) {
                Chip chip = new Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle);
                chip.setText(shape.name);
                chip.setCheckable(true);
                chip.setCheckedIconVisible(false);
                chip.setOnClickListener(v -> selectLibraryChord(shape, chip));
                if (libraryShape != null && libraryShape.name.equals(shape.name)) {
                    chip.setChecked(true);
                    selectedChip = chip;
                }
                chips.addView(chip);
            }
            container.addView(chips);
        }
    }

    private void selectLibraryChord(ChordShape shape, Chip chip) {
        if (selectedChip != null && selectedChip != chip) selectedChip.setChecked(false);
        chip.setChecked(true);
        selectedChip = chip;
        libraryShape = shape;
        prefs().edit().putString(KEY_LIBRARY_CHORD, shape.name).apply();
        showLibraryChord();
        Anim.pop(textChordName);
    }

    private void showLibraryChord() {
        diagram.setEditable(false);
        diagram.setShape(libraryShape);
        setTarget(libraryShape.toStep(), libraryShape.name);
    }

    // ---------- конструктор ----------

    private void setupBuilder(View view) {
        diagram.setOnShapeChangeListener(frets -> {
            builderFrets = frets;
            saveBuilder();
            showBuilderChord();
        });
        view.findViewById(R.id.btnBaseFretDown).setOnClickListener(v -> setBaseFret(builderBaseFret - 1));
        view.findViewById(R.id.btnBaseFretUp).setOnClickListener(v -> setBaseFret(builderBaseFret + 1));
        view.findViewById(R.id.btnClearShape).setOnClickListener(v -> diagram.clear());
    }

    private void setBaseFret(int value) {
        builderBaseFret = Math.max(1, Math.min(MAX_BASE_FRET, value));
        diagram.setBaseFret(builderBaseFret);
        textBaseFret.setText("Лады " + builderBaseFret + "–" + (builderBaseFret + ChordDiagramView.VISIBLE_FRETS - 1));
        saveBuilder();
    }

    private void showBuilderChord() {
        TabNote step = new ChordShape("", builderFrets, null).toStep();
        if (step == null) {
            setTarget(null, null);
            return;
        }
        if (!step.isChord()) {
            setTarget(step, null);
            return;
        }
        int bass = step.getNotes().get(step.getNotes().size() - 1).getMidi() % 12;
        String name = ChordNamer.name(step.getPitchClasses(), bass);
        // с названием — чтобы оно показывалось и проигрывалось как аккорд
        setTarget(TabNote.chord(step.getNotes(), name == null ? "" : name), name);
    }

    // ---------- режим ----------

    private void applyMode() {
        libraryCard.setVisibility(builderMode ? View.GONE : View.VISIBLE);
        builderPanel.setVisibility(builderMode ? View.VISIBLE : View.GONE);
        textCardTitle.setText(builderMode ? "Ваш аккорд" : "Аккорд");

        if (builderMode) {
            diagram.setEditable(true);
            diagram.setShape(new ChordShape("", builderFrets, null));
            diagram.setBaseFret(builderBaseFret);
            setBaseFret(builderBaseFret);
            showBuilderChord();
        } else {
            if (libraryShape == null) libraryShape = ChordLibrary.find("Am");
            showLibraryChord();
        }
    }

    /** Что показывать и проверять. name == null для одиночной ноты или неизвестного аккорда. */
    private void setTarget(TabNote step, String name) {
        target = step;
        score = 0;
        matchFrames = 0;
        matchCounter.reset();
        needOnset = false;
        textScore.setText("Сыграно верно: 0");

        if (step == null) {
            textChordName.setText("—");
            textChordInfo.setText("Поставьте пальцы на гриф");
            textChordNotes.setVisibility(View.GONE);
            return;
        }
        textChordNotes.setVisibility(View.VISIBLE);
        textChordNotes.setText("Ноты: " + step.notesListing());

        if (!step.isChord()) {
            textChordName.setText(step.getNoteName());
            textChordInfo.setText("одна нота — добавьте ещё струны, чтобы получился аккорд");
        } else if (name == null || name.isEmpty()) {
            textChordName.setText("?");
            textChordInfo.setText("такого аккорда нет в справочнике — но его можно сыграть");
        } else {
            textChordName.setText(name);
            textChordInfo.setText(ChordNamer.describe(name));
        }
    }

    // ---------- проверка игры ----------

    private void startListening() {
        if (target == null) {
            Toast.makeText(requireContext(), "Сначала выберите или соберите аккорд", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }
        matchFrames = 0;
        matchCounter.reset();
        needOnset = false;
        pitchDetector.start();
        btnPlay.setText("СТОП");
        textHeard.setText("Ударьте по струнам");
    }

    private void stopListening() {
        pitchDetector.stop();
        if (btnPlay != null) btnPlay.setText("СЫГРАТЬ");
    }

    // вызывается в главном потоке (так гарантирует PitchDetector)
    private void onAudioFrame(AudioFrame frame) {
        // звучит подсказка из динамика — это не игра пользователя
        if (target == null || audioEngine.isPreviewPlaying() || frame.silent) {
            matchFrames = 0;
        matchCounter.reset();
            return;
        }
        showHeard(frame);

        if (needOnset) {
            if (frame.onset && frame.streamMs - lastHitMs > 70) needOnset = false;
            else return;
        }
        int strength = StepMatcher.matchStrength(target, frame);
        matchFrames = matchCounter.onFrame(strength > StepMatcher.NO_MATCH);
        if (!StepMatcher.accepted(strength, matchFrames)) return;

        score++;
        matchFrames = 0;
        matchCounter.reset();
        needOnset = true;
        lastHitMs = frame.streamMs;
        textScore.setText("Сыграно верно: " + score);
        textHeard.setText("Верно! Ударьте ещё раз");
        flashOverlay.animate().cancel();
        flashOverlay.setAlpha(0.4f);
        flashOverlay.animate().alpha(0f).setDuration(450).start();
        diagram.flash();
        Anim.pop(textChordName);
    }

    /** "Слышу: Am · A C E" — какой аккорд звучит сейчас (полезно, если сыграли не тот). */
    private void showHeard(AudioFrame frame) {
        if (frame.chroma == null) {
            if (frame.frequency > 0) textHeard.setText("Слышу: " + NoteUtils.frequencyToNote(frame.frequency).fullName);
            return;
        }
        boolean[] pitchClasses = new boolean[12];
        StringBuilder notes = new StringBuilder();
        int count = 0;
        for (int pc = 0; pc < 12; pc++) {
            if (frame.chroma[pc] >= 0.35) {
                pitchClasses[pc] = true;
                if (notes.length() > 0) notes.append(' ');
                notes.append(NoteUtils.NOTE_NAMES[pc]);
                count++;
            }
        }
        if (count == 0) return;
        int midi = frame.midi();
        int bass = midi > 0 && pitchClasses[midi % 12] ? midi % 12 : firstSet(pitchClasses);
        String name = count >= 2 ? ChordNamer.name(pitchClasses, bass) : null;
        textHeard.setText(name != null ? "Слышу: " + name + " · " + notes : "Слышу: " + notes);
    }

    private static int firstSet(boolean[] values) {
        for (int i = 0; i < values.length; i++) if (values[i]) return i;
        return 0;
    }

    // ---------- сохранение ----------

    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void restore() {
        SharedPreferences p = prefs();
        builderMode = p.getBoolean(KEY_MODE_BUILDER, false);
        libraryShape = ChordLibrary.find(p.getString(KEY_LIBRARY_CHORD, "Am"));
        builderBaseFret = p.getInt(KEY_BUILDER_BASE, 1);
        String saved = p.getString(KEY_BUILDER_FRETS, null);
        if (saved != null && saved.split(",").length == 6) {
            String[] parts = saved.split(",");
            for (int i = 0; i < 6; i++) {
                try {
                    builderFrets[i] = Integer.parseInt(parts[i]);
                } catch (NumberFormatException e) {
                    builderFrets[i] = ChordShape.MUTED;
                }
            }
        }
    }

    private void saveBuilder() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) sb.append(',');
            sb.append(builderFrets[i]);
        }
        prefs().edit()
                .putString(KEY_BUILDER_FRETS, sb.toString())
                .putInt(KEY_BUILDER_BASE, builderBaseFret)
                .apply();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ---------- жизненный цикл ----------

    @Override
    public void onPause() {
        super.onPause();
        stopListening();
        audioEngine.release();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        pitchDetector.stop();
        btnPlay = null;
    }
}
