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
import com.example.guitartuner.models.Lesson;
import com.example.guitartuner.models.LessonLibrary;
import com.example.guitartuner.models.TabNote;
import com.example.guitartuner.tuner.NoteUtils;
import com.example.guitartuner.tuner.PitchDetector;
import com.example.guitartuner.utils.GuitarNoteUtils;
import com.example.guitartuner.views.FretboardView;
import com.example.guitartuner.views.TabView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.slider.RangeSlider;

import java.util.ArrayList;
import java.util.List;

/**
 * Прохождение урока: нужно по очереди сыграть ноты урока (точная высота, с октавой).
 * Ноты показываются бегущей лентой или на грифе — вид переключается.
 */
public class LessonDetailFragment extends Fragment {

    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_STRINGS = "arg_strings";
    private static final String ARG_FRETS = "arg_frets";
    private static final String ARG_REGION_SELECTABLE = "arg_region_selectable";

    // сколько мс подряд нужно слышать верную ноту, чтобы засчитать
    private static final long HOLD_MS = 120;
    // защита от повторного засчитывания сразу после попадания
    private static final long ADVANCE_COOLDOWN_MS = 250;

    private static final String PREFS = "lessons";
    private static final String KEY_VIEW_MODE = "view_mode_fretboard";
    private static final String KEY_REGION_START = "chromatic_region_start";
    private static final String KEY_REGION_END = "chromatic_region_end";

    private TextView titleView;
    private MaterialButtonToggleGroup toggleViewMode;
    private View regionPanel;
    private TextView textRegion;
    private RangeSlider rangeRegion;
    private TextView textCurrentNote;
    private TextView textPosition;
    private TextView textHeard;
    private TextView textProgress;
    private TabView tabView;
    private FretboardView fretboardView;
    private Button btnPlay;
    private View flashOverlay;

    private String title;
    private List<TabNote> notes = new ArrayList<>();
    private boolean regionSelectable;
    private int regionStart = LessonLibrary.DEFAULT_REGION_START;
    private int regionEnd = LessonLibrary.DEFAULT_REGION_END;

    private int currentIndex = 0;
    private long correctSince = 0L;
    private long lastAdvanceTime = 0L;

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
                    startLesson();
                } else {
                    Toast.makeText(requireContext(),
                            "Без доступа к микрофону урок не работает",
                            Toast.LENGTH_SHORT).show();
                }
            });

    public static LessonDetailFragment newInstance(Lesson lesson) {
        LessonDetailFragment fragment = new LessonDetailFragment();

        Bundle args = new Bundle();
        args.putString(ARG_TITLE, lesson.getTitle());
        args.putBoolean(ARG_REGION_SELECTABLE, lesson.isRegionSelectable());

        int size = lesson.getNotes().size();
        int[] strings = new int[size];
        int[] frets = new int[size];

        for (int i = 0; i < size; i++) {
            strings[i] = lesson.getNotes().get(i).getStringNumber();
            frets[i] = lesson.getNotes().get(i).getFret();
        }

        args.putIntArray(ARG_STRINGS, strings);
        args.putIntArray(ARG_FRETS, frets);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args == null) return;

        title = args.getString(ARG_TITLE, "");
        regionSelectable = args.getBoolean(ARG_REGION_SELECTABLE, false);

        if (regionSelectable) {
            SharedPreferences p = prefs();
            regionStart = p.getInt(KEY_REGION_START, LessonLibrary.DEFAULT_REGION_START);
            regionEnd = p.getInt(KEY_REGION_END, LessonLibrary.DEFAULT_REGION_END);
            notes = LessonLibrary.chromaticCascade(regionStart, regionEnd);
        } else {
            int[] strings = args.getIntArray(ARG_STRINGS);
            int[] frets = args.getIntArray(ARG_FRETS);
            if (strings != null && frets != null) {
                for (int i = 0; i < strings.length; i++) {
                    notes.add(new TabNote(strings[i], frets[i]));
                }
            }
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_lesson_detail, container, false);

        titleView = view.findViewById(R.id.textLessonTitle);
        toggleViewMode = view.findViewById(R.id.toggleViewMode);
        regionPanel = view.findViewById(R.id.regionPanel);
        textRegion = view.findViewById(R.id.textRegion);
        rangeRegion = view.findViewById(R.id.rangeRegion);
        textCurrentNote = view.findViewById(R.id.textCurrentNote);
        textPosition = view.findViewById(R.id.textPosition);
        textHeard = view.findViewById(R.id.textHeard);
        textProgress = view.findViewById(R.id.textProgress);
        tabView = view.findViewById(R.id.tabView);
        fretboardView = view.findViewById(R.id.fretboardView);
        btnPlay = view.findViewById(R.id.btnPlayLesson);
        flashOverlay = view.findViewById(R.id.flashOverlay);

        titleView.setText(title);
        tabView.setNotes(notes);

        setupViewModeToggle();
        setupRegion();

        btnPlay.setOnClickListener(v -> {
            if (pitchDetector.isRunning()) pauseLesson();
            else startLesson();
        });

        updateLessonUi();
        return view;
    }

    // ---------- вид: бегущие ноты / гриф ----------

    private void setupViewModeToggle() {
        boolean fretboardMode = prefs().getBoolean(KEY_VIEW_MODE, false);
        toggleViewMode.check(fretboardMode ? R.id.btnViewFretboard : R.id.btnViewTab);
        applyViewMode(fretboardMode);

        toggleViewMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            boolean fretboard = checkedId == R.id.btnViewFretboard;
            applyViewMode(fretboard);
            prefs().edit().putBoolean(KEY_VIEW_MODE, fretboard).apply();
        });
    }

    private void applyViewMode(boolean fretboard) {
        tabView.setVisibility(fretboard ? View.GONE : View.VISIBLE);
        fretboardView.setVisibility(fretboard ? View.VISIBLE : View.GONE);
    }

    // ---------- участок грифа (хроматический урок) ----------

    private void setupRegion() {
        if (!regionSelectable) {
            regionPanel.setVisibility(View.GONE);
            fretboardView.setRegionMode(false);
            return;
        }

        regionPanel.setVisibility(View.VISIBLE);
        rangeRegion.setValueTo(FretboardView.FRET_COUNT);
        rangeRegion.setValues((float) regionStart, (float) regionEnd);

        fretboardView.setRegionMode(true);
        fretboardView.setRegion(regionStart, regionEnd);

        // ползунок — в обоих видах
        rangeRegion.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) return;
            List<Float> values = slider.getValues();
            int start = Math.round(values.get(0));
            int end = Math.round(values.get(1));
            fretboardView.setRegion(start, end);
            applyRegion(start, end);
        });

        // нажатия на гриф — в виде грифа
        fretboardView.setOnRegionChangeListener((start, end, pending) -> {
            if (pending) {
                textRegion.setText("Начало: лад " + start + ". Нажмите на лад, где участок заканчивается");
                return;
            }
            rangeRegion.setValues((float) start, (float) end);
            applyRegion(start, end);
        });

        updateRegionLabel();
    }

    private void applyRegion(int start, int end) {
        if (start == regionStart && end == regionEnd) {
            updateRegionLabel();
            return;
        }
        pauseLesson();

        regionStart = start;
        regionEnd = end;
        prefs().edit()
                .putInt(KEY_REGION_START, start)
                .putInt(KEY_REGION_END, end)
                .apply();

        notes = LessonLibrary.chromaticCascade(start, end);
        currentIndex = 0;
        tabView.setNotes(notes);
        textHeard.setText("Нажмите ИГРАТЬ");
        updateRegionLabel();
        updateLessonUi();
    }

    private void updateRegionLabel() {
        textRegion.setText("Участок: лады " + regionStart + "–" + regionEnd
                + " · " + notes.size() + " нот. Двигайте ползунок или нажмите на гриф");
    }

    // ---------- урок ----------

    private boolean isComplete() {
        return currentIndex >= notes.size();
    }

    private void startLesson() {
        if (notes.isEmpty()) return;

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }

        if (isComplete()) {
            currentIndex = 0;
            tabView.setNotes(notes);
        }

        correctSince = 0L;
        pitchDetector.start();
        tabView.setPlaying(true);
        btnPlay.setText("СТОП");
        textHeard.setText("Сыграйте подсвеченную ноту");
        updateLessonUi();
    }

    private void pauseLesson() {
        pitchDetector.stop();
        if (btnPlay == null) return;

        tabView.setPlaying(false);
        btnPlay.setText(isComplete() ? "ЕЩЁ РАЗ" : "ИГРАТЬ");
    }

    private void updateLessonUi() {
        TabNote current = isComplete() ? null : notes.get(currentIndex);
        TabNote next = currentIndex + 1 < notes.size() ? notes.get(currentIndex + 1) : null;

        if (current != null) {
            textCurrentNote.setText(GuitarNoteUtils.getNoteName(current.getStringNumber(), current.getFret()));
            textPosition.setText("Струна " + current.getStringNumber() + " · лад " + current.getFret());
        } else {
            textCurrentNote.setText("✓");
            textPosition.setText("Урок пройден!");
        }
        textProgress.setText(Math.min(currentIndex, notes.size()) + " / " + notes.size());

        tabView.setCurrentIndex(currentIndex);
        fretboardView.setLessonMarkers(current, next);
    }

    // вызывается в главном потоке (так гарантирует PitchDetector)
    private void onPitch(double frequencyHz) {
        if (isComplete()) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastAdvanceTime < ADVANCE_COOLDOWN_MS) return;

        NoteUtils.NoteInfo detected = NoteUtils.frequencyToNote(frequencyHz);
        if (detected == null) return;
        textHeard.setText("Слышу: " + detected.fullName);

        TabNote expected = notes.get(currentIndex);
        int expectedMidi = GuitarNoteUtils.getMidi(expected.getStringNumber(), expected.getFret());

        if (detected.midi != expectedMidi) {
            correctSince = 0L;
            return;
        }

        if (correctSince == 0L) {
            correctSince = now;
        } else if (now - correctSince >= HOLD_MS) {
            advance(now);
        }
    }

    private void advance(long now) {
        currentIndex++;
        correctSince = 0L;
        lastAdvanceTime = now;

        if (isComplete()) {
            flash(0.6f, 700);
            pauseLesson();
            textHeard.setText("Отлично! Все ноты сыграны");
        } else {
            flash(0.35f, 350);
        }
        updateLessonUi();
    }

    private void flash(float alpha, long durationMs) {
        flashOverlay.animate().cancel();
        flashOverlay.setAlpha(alpha);
        flashOverlay.animate().alpha(0f).setDuration(durationMs).start();
    }

    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------- жизненный цикл ----------

    @Override
    public void onPause() {
        super.onPause();
        // не держим микрофон, когда экран не виден
        pauseLesson();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        pitchDetector.stop();
        btnPlay = null;
    }
}
