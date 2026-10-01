package com.example.guitartuner.fragments;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.guitartuner.R;
import com.example.guitartuner.audio.AudioEngine;
import com.example.guitartuner.models.ScaleType;
import com.example.guitartuner.models.TabNote;
import com.example.guitartuner.tuner.NoteUtils;
import com.example.guitartuner.tuner.PitchDetector;
import com.example.guitartuner.ui.Anim;
import com.example.guitartuner.views.FretboardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Random;

/**
 * Тренировка нот: показывается нота из выбранной гаммы, пользователь играет её
 * на гитаре в любой октаве. Верная нота — зелёная вспышка и следующая нота.
 */
public class NoteTrainerFragment extends Fragment {

    // сколько мс подряд нужно слышать верную ноту, чтобы засчитать
    private static final long HOLD_MS = 150;
    // пауза между верным ответом и следующей нотой
    private static final long NEXT_NOTE_DELAY_MS = 600;

    private static final String PREFS = "note_trainer";
    private static final String KEY_ROOT = "root";
    private static final String KEY_SCALE = "scale";
    private static final String KEY_SHOW_NOTES = "show_notes";
    private static final String KEY_REGION_MODE = "region_mode";
    private static final String KEY_REGION_START = "region_start";
    private static final String KEY_REGION_END = "region_end";

    private ChipGroup chipGroupRoot;
    private ChipGroup chipGroupScale;
    private ChipGroup chipGroupPentatonic;
    private TextView textScaleNotes;
    private TextView textTargetNote;
    private TextView textHeard;
    private TextView textScore;
    private TextView textStreak;
    private MaterialSwitch switchShowNotes;
    private MaterialSwitch switchRegion;
    private TextView textRegionHint;
    private FretboardView fretboardView;
    private Button btnStart;
    private View flashOverlay;

    private final Random random = new Random();

    private int rootPitchClass = 0; // C
    private ScaleType scaleType = ScaleType.CHROMATIC;
    private int[] scalePitchClasses = ScaleType.CHROMATIC.pitchClasses(0);

    private int targetPitchClass = -1;
    private int score = 0;
    // верных ответов подряд без подсказки "Послушать"
    private int streak = 0;

    private final AudioEngine audioEngine = new AudioEngine();
    private long correctSince = 0L;
    // пока true — ждём следующую ноту и не реагируем на звук
    private boolean waitingNextNote = false;

    private final Runnable nextNoteRunnable = () -> {
        waitingNextNote = false;
        pickNextTarget();
    };

    private final PitchDetector pitchDetector = new PitchDetector(new PitchDetector.PitchListener() {
        @Override
        public void onPitchDetected(double frequencyHz) {
            onPitch(frequencyHz);
        }

        @Override
        public void onSilence() {
            correctSince = 0L;
        }
    });

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startTraining();
                } else {
                    Toast.makeText(requireContext(),
                            "Без доступа к микрофону тренировка не работает",
                            Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_note_trainer, container, false);

        chipGroupRoot = view.findViewById(R.id.chipGroupRoot);
        chipGroupScale = view.findViewById(R.id.chipGroupScale);
        chipGroupPentatonic = view.findViewById(R.id.chipGroupPentatonic);
        textScaleNotes = view.findViewById(R.id.textScaleNotes);
        textTargetNote = view.findViewById(R.id.textTargetNote);
        textHeard = view.findViewById(R.id.textHeard);
        textScore = view.findViewById(R.id.textScore);
        textStreak = view.findViewById(R.id.textStreak);
        switchShowNotes = view.findViewById(R.id.switchShowNotes);
        switchRegion = view.findViewById(R.id.switchRegion);
        textRegionHint = view.findViewById(R.id.textRegionHint);
        fretboardView = view.findViewById(R.id.fretboardView);
        btnStart = view.findViewById(R.id.btnStartTrainer);
        flashOverlay = view.findViewById(R.id.flashOverlay);

        restoreSettings();
        setupRootChips();
        setupScaleChips();
        setupFretboardControls();

        btnStart.setOnClickListener(v -> {
            if (pitchDetector.isRunning()) stopTraining();
            else startTraining();
        });
        view.findViewById(R.id.btnListenTrainer).setOnClickListener(v -> listenTarget());

        applyScale();
        Anim.cascadeIn(view.findViewById(R.id.trainerContent));
        return view;
    }

    // ---------- настройки ----------

    // 12 чипов C … B в одну прокручиваемую строку
    private void setupRootChips() {
        for (int pc = 0; pc < 12; pc++) {
            Chip chip = new Chip(requireContext(), null,
                    com.google.android.material.R.attr.chipStyle);
            chip.setId(View.generateViewId());
            chip.setTag(pc);
            chip.setText(NoteUtils.NOTE_NAMES[pc]);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chipGroupRoot.addView(chip);
            if (pc == rootPitchClass) chipGroupRoot.check(chip.getId());
        }

        chipGroupRoot.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            Chip chip = group.findViewById(checkedIds.get(0));
            int pc = (int) chip.getTag();
            if (pc == rootPitchClass) return;
            rootPitchClass = pc;
            applyScale();
        });
    }

    private void setupScaleChips() {
        // отмечаем сохранённый выбор до подписки на изменения
        if (scaleType == ScaleType.MAJOR) chipGroupScale.check(R.id.chipMajor);
        else if (scaleType == ScaleType.MINOR) chipGroupScale.check(R.id.chipMinor);
        else if (scaleType == ScaleType.MAJOR_PENTATONIC) chipGroupPentatonic.check(R.id.chipPentaMajor);
        else if (scaleType == ScaleType.MINOR_PENTATONIC) chipGroupPentatonic.check(R.id.chipPentaMinor);

        // гамма и пентатоника взаимоисключающие; ничего не выбрано — хроматическая
        chipGroupScale.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) chipGroupPentatonic.clearCheck();
            onScaleChipsChanged();
        });
        chipGroupPentatonic.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) chipGroupScale.clearCheck();
            onScaleChipsChanged();
        });
    }

    private void onScaleChipsChanged() {
        ScaleType newType = readScaleFromChips();
        if (newType == scaleType) return;
        scaleType = newType;
        applyScale();
    }

    private ScaleType readScaleFromChips() {
        int scaleId = chipGroupScale.getCheckedChipId();
        int pentaId = chipGroupPentatonic.getCheckedChipId();
        if (scaleId == R.id.chipMajor) return ScaleType.MAJOR;
        if (scaleId == R.id.chipMinor) return ScaleType.MINOR;
        if (pentaId == R.id.chipPentaMajor) return ScaleType.MAJOR_PENTATONIC;
        if (pentaId == R.id.chipPentaMinor) return ScaleType.MINOR_PENTATONIC;
        return ScaleType.CHROMATIC;
    }

    private void setupFretboardControls() {
        switchShowNotes.setOnCheckedChangeListener((button, checked) -> {
            fretboardView.setShowNotes(checked);
            saveSettings();
        });

        switchRegion.setOnCheckedChangeListener((button, checked) -> {
            fretboardView.setRegionMode(checked);
            updateRegionHint(false);
            saveSettings();
        });

        fretboardView.setOnRegionChangeListener((start, end, pending) -> {
            updateRegionHint(pending);
            if (!pending) saveSettings();
        });

        fretboardView.setShowNotes(switchShowNotes.isChecked());
        fretboardView.setRegionMode(switchRegion.isChecked());
        updateRegionHint(false);
    }

    private void updateRegionHint(boolean pending) {
        if (!switchRegion.isChecked()) {
            textRegionHint.setVisibility(View.GONE);
            return;
        }
        textRegionHint.setVisibility(View.VISIBLE);
        if (pending) {
            textRegionHint.setText("Начало: лад " + fretboardView.getRegionStart()
                    + ". Нажмите на лад, где участок заканчивается");
        } else {
            textRegionHint.setText("Участок: лады " + fretboardView.getRegionStart()
                    + "–" + fretboardView.getRegionEnd()
                    + ". Нажмите на гриф, чтобы выбрать другой");
        }
    }

    /** Пересчитывает ноты гаммы и, если тренировка идёт, выбирает новую ноту. */
    private void applyScale() {
        scalePitchClasses = scaleType.pitchClasses(rootPitchClass);

        StringBuilder names = new StringBuilder();
        for (int pc : scalePitchClasses) {
            if (names.length() > 0) names.append("  ");
            names.append(NoteUtils.NOTE_NAMES[pc]);
        }
        String title = scaleType == ScaleType.CHROMATIC
                ? scaleType.label
                : NoteUtils.NOTE_NAMES[rootPitchClass] + " " + scaleType.label.toLowerCase();
        textScaleNotes.setText(title + ": " + names);

        fretboardView.setScalePitchClasses(scalePitchClasses);

        if (pitchDetector.isRunning()) {
            cancelPendingNextNote();
            pickNextTarget();
        }
        saveSettings();
    }

    // ---------- тренировка ----------

    private void startTraining() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }

        score = 0;
        streak = 0;
        textScore.setText("Верно: 0");
        textStreak.setText("Серия: 0");
        pitchDetector.start();
        btnStart.setText("СТОП");
        pickNextTarget();
    }

    private void stopTraining() {
        pitchDetector.stop();
        cancelPendingNextNote();
        targetPitchClass = -1;
        if (btnStart == null) return;

        btnStart.setText("СТАРТ");
        textTargetNote.setText("—");
        textHeard.setText("Нажмите СТАРТ и сыграйте показанную ноту");
        fretboardView.setTargetPitchClass(-1);
    }

    private void cancelPendingNextNote() {
        waitingNextNote = false;
        if (textTargetNote != null) textTargetNote.removeCallbacks(nextNoteRunnable);
    }

    private void pickNextTarget() {
        int next;
        if (scalePitchClasses.length == 1) {
            next = scalePitchClasses[0];
        } else {
            // не повторяем ту же ноту дважды подряд — иначе засчитается ещё звучащая струна
            do {
                next = scalePitchClasses[random.nextInt(scalePitchClasses.length)];
            } while (next == targetPitchClass);
        }

        targetPitchClass = next;
        correctSince = 0L;
        textTargetNote.setText(NoteUtils.NOTE_NAMES[next]);
        Anim.pop(textTargetNote);
        textHeard.setText("Сыграйте эту ноту");
        fretboardView.setTargetPitchClass(next);
    }

    // вызывается в главном потоке (так гарантирует PitchDetector)
    private void onPitch(double frequencyHz) {
        if (targetPitchClass < 0 || waitingNextNote) return;
        // звучит подсказка из динамика — это не игра пользователя
        if (audioEngine.isPreviewPlaying()) return;

        NoteUtils.NoteInfo note = NoteUtils.frequencyToNote(frequencyHz);
        if (note == null) return;

        textHeard.setText("Слышу: " + note.fullName);

        int playedPitchClass = ((note.midi % 12) + 12) % 12;
        if (playedPitchClass != targetPitchClass) {
            correctSince = 0L;
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (correctSince == 0L) {
            correctSince = now;
        } else if (now - correctSince >= HOLD_MS) {
            onCorrectNote(note.fullName);
        }
    }

    /** Проиграть загаданную ноту (в удобной средней октаве: E3 … D#4). */
    private void listenTarget() {
        if (targetPitchClass < 0) {
            textHeard.setText("Сначала нажмите СТАРТ");
            return;
        }
        int midi = 52 + ((targetPitchClass - 4 + 12) % 12);
        audioEngine.playStep(new TabNote(1, 0, midi));
        // с подсказкой серия обнуляется
        streak = 0;
        textStreak.setText("Серия: 0");
        correctSince = 0L;
    }

    private void onCorrectNote(String playedName) {
        score++;
        streak++;
        textScore.setText("Верно: " + score);
        textStreak.setText("Серия: " + streak);
        textHeard.setText("Верно! " + playedName);

        flashGreen();

        waitingNextNote = true;
        textTargetNote.postDelayed(nextNoteRunnable, NEXT_NOTE_DELAY_MS);
    }

    private void flashGreen() {
        flashOverlay.animate().cancel();
        flashOverlay.setAlpha(0.55f);
        flashOverlay.animate().alpha(0f).setDuration(500).start();
    }

    // ---------- сохранение настроек ----------

    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void restoreSettings() {
        SharedPreferences p = prefs();
        rootPitchClass = p.getInt(KEY_ROOT, 0);
        try {
            scaleType = ScaleType.valueOf(p.getString(KEY_SCALE, ScaleType.CHROMATIC.name()));
        } catch (IllegalArgumentException e) {
            scaleType = ScaleType.CHROMATIC;
        }
        switchShowNotes.setChecked(p.getBoolean(KEY_SHOW_NOTES, true));
        switchRegion.setChecked(p.getBoolean(KEY_REGION_MODE, false));
        fretboardView.setRegion(p.getInt(KEY_REGION_START, 0), p.getInt(KEY_REGION_END, 4));
    }

    private void saveSettings() {
        if (fretboardView == null) return;
        prefs().edit()
                .putInt(KEY_ROOT, rootPitchClass)
                .putString(KEY_SCALE, scaleType.name())
                .putBoolean(KEY_SHOW_NOTES, switchShowNotes.isChecked())
                .putBoolean(KEY_REGION_MODE, switchRegion.isChecked())
                .putInt(KEY_REGION_START, fretboardView.getRegionStart())
                .putInt(KEY_REGION_END, fretboardView.getRegionEnd())
                .apply();
    }

    // ---------- жизненный цикл ----------

    @Override
    public void onPause() {
        super.onPause();
        // не держим микрофон и звук, когда экран не виден
        stopTraining();
        audioEngine.release();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelPendingNextNote();
        fretboardView = null;
    }
}
