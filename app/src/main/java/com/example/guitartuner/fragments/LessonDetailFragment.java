package com.example.guitartuner.fragments;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
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
import com.example.guitartuner.songs.SongStore;
import com.example.guitartuner.tuner.ChordMatcher;
import com.example.guitartuner.tuner.NoteUtils;
import com.example.guitartuner.tuner.PitchDetector;
import com.example.guitartuner.utils.GuitarNoteUtils;
import com.example.guitartuner.views.FretboardView;
import com.example.guitartuner.views.TabView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.slider.Slider;

import java.io.File;
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
    private static final String ARG_SONG_PATH = "arg_song_path";

    // сколько мс подряд нужно слышать верную ноту, чтобы засчитать
    private static final long HOLD_MS = 120;
    // защита от повторного засчитывания сразу после попадания
    private static final long ADVANCE_COOLDOWN_MS = 250;
    // во сколько раз должна вырасти громкость, чтобы считать это новым щипком струны
    private static final double ATTACK_RATIO = 1.5;
    // аккорд засчитывается после стольких кадров подряд (~46 мс каждый) с совпадением
    private static final int CHORD_FRAMES = 2;

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
    private View seekPanel;
    private Slider seekSlider;

    private String title;
    private List<TabNote> notes = new ArrayList<>();
    private boolean regionSelectable;
    private int regionStart = LessonLibrary.DEFAULT_REGION_START;
    private int regionEnd = LessonLibrary.DEFAULT_REGION_END;

    // для песен: подписи струн по строю и сколько ладов показывать на грифе
    private String[] stringLabels;
    private int fretCount = FretboardView.FRET_COUNT;
    // для песен: номер такта (с 0) каждого шага; null — тактов нет (уроки, старые песни)
    private int[] bars;

    private int currentIndex = 0;
    private long correctSince = 0L;
    private long lastAdvanceTime = 0L;

    // следующая нота совпадает с только что сыгранной: ждём нового щипка струны,
    // иначе ещё звучащая струна засчитала бы её сама
    private boolean needsReattack = false;
    private double minLevelSinceAdvance = Double.MAX_VALUE;

    // сколько кадров подряд хромаграмма совпадает с ожидаемым аккордом
    private int chordMatchedFrames = 0;

    // хромаграмма нужна для аккордов — считаем её всегда, это один FFT на кадр
    private final PitchDetector pitchDetector = new PitchDetector(new PitchDetector.PitchListener() {
        @Override
        public void onPitchDetected(double frequencyHz) {
            // не вызывается: PitchDetector передаёт и громкость
        }

        @Override
        public void onPitchDetected(double frequencyHz, double level) {
            onPitch(frequencyHz, level);
        }

        @Override
        public void onChroma(double[] chroma, double level) {
            onChromaFrame(chroma, level);
        }

        @Override
        public void onNoPitch() {
            // для аккорда это нормально: YIN не находит одну ноту, работает хромаграмма
            correctSince = 0L;
        }

        @Override
        public void onSilence() {
            correctSince = 0L;
            chordMatchedFrames = 0;
            // струна затихла — следующий звук точно новый щипок
            needsReattack = false;
        }
    }, true);

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

    /** Песня, сконвертированная из Guitar Pro (см. SongStore). */
    public static LessonDetailFragment newInstanceForSong(File convertedSong) {
        LessonDetailFragment fragment = new LessonDetailFragment();
        Bundle args = new Bundle();
        // ноты песни могут исчисляться тысячами — передаём путь, а не сами ноты
        args.putString(ARG_SONG_PATH, convertedSong.getAbsolutePath());
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

        String songPath = args.getString(ARG_SONG_PATH);
        if (songPath != null) {
            loadSong(new File(songPath));
        } else if (regionSelectable) {
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

    private void loadSong(File file) {
        SongStore.ConvertedSong song = SongStore.load(file);
        if (song == null) {
            title = "Не удалось открыть песню";
            return;
        }

        title = song.trackName.isEmpty() ? song.title : song.title + " · " + song.trackName;
        notes = song.notes;
        bars = song.bars;

        stringLabels = new String[song.tuning.length];
        for (int i = 0; i < song.tuning.length; i++) {
            String name = GuitarNoteUtils.pitchClassName(song.tuning[i]);
            // 1-ю струну пишем строчной, как в табулатуре (e B G D A E)
            stringLabels[i] = i == 0 ? name.toLowerCase() : name;
        }

        int maxFret = 0;
        for (TabNote step : notes) {
            for (TabNote note : step.getNotes()) maxFret = Math.max(maxFret, note.getFret());
        }
        fretCount = maxFret;
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
        seekPanel = view.findViewById(R.id.seekPanel);
        seekSlider = view.findViewById(R.id.seekSlider);

        titleView.setText(title);
        tabView.setStringLabels(stringLabels);
        tabView.setNotes(notes);
        fretboardView.setFretCount(fretCount);

        setupSeek(view);

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
        configureSeek();
        textHeard.setText("Нажмите ИГРАТЬ");
        updateRegionLabel();
        updateLessonUi();
    }

    private void updateRegionLabel() {
        textRegion.setText("Участок: лады " + regionStart + "–" + regionEnd
                + " · " + notes.size() + " нот. Двигайте ползунок или нажмите на гриф");
    }

    // ---------- перемотка ----------

    private void setupSeek(View view) {
        view.findViewById(R.id.btnSeekStart).setOnClickListener(v -> seekTo(0));
        view.findViewById(R.id.btnSeekPrev).setOnClickListener(v -> seekTo(previousPosition()));
        view.findViewById(R.id.btnSeekNext).setOnClickListener(v -> seekTo(nextPosition()));

        seekSlider.setLabelFormatter(value -> positionLabel((int) value));
        seekSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) seekTo((int) value);
        });

        tabView.setOnSeekListener(new TabView.OnSeekListener() {
            @Override
            public void onSeek(int index) {
                seekTo(index);
            }

            @Override
            public void onSeekPreview(int index) {
                textProgress.setText(progressText(index));
            }
        });

        configureSeek();
    }

    /** Диапазон ползунка — по числу шагов; вызывать при смене нот урока. */
    private void configureSeek() {
        if (notes.size() < 2) {
            seekPanel.setVisibility(View.GONE);
            return;
        }
        seekPanel.setVisibility(View.VISIBLE);
        seekSlider.setValue(0);
        seekSlider.setValueTo(notes.size() - 1);
    }

    /** Перейти к шагу index: урок продолжится с него (если идёт — сразу слушаем эту ноту). */
    private void seekTo(int index) {
        if (notes.isEmpty()) return;
        currentIndex = Math.max(0, Math.min(notes.size() - 1, index));
        correctSince = 0L;
        chordMatchedFrames = 0;
        needsReattack = false;
        // ещё звучащая прежняя нота не должна сразу засчитать новую
        lastAdvanceTime = SystemClock.elapsedRealtime();

        if (pitchDetector.isRunning()) {
            textHeard.setText("Сыграйте подсвеченную ноту");
        } else {
            btnPlay.setText("ИГРАТЬ");
        }
        updateLessonUi();
    }

    // "назад": в начало текущего такта, а если уже в начале — в начало предыдущего
    private int previousPosition() {
        int index = Math.min(currentIndex, notes.size() - 1);
        if (bars == null) return index - 1;

        int barStart = firstStepOfBar(bars[index]);
        if (index > barStart) return barStart;
        return index == 0 ? 0 : firstStepOfBar(bars[index - 1]);
    }

    // "вперёд": в начало следующего такта (с нотами)
    private int nextPosition() {
        int index = Math.min(currentIndex, notes.size() - 1);
        if (bars == null) return index + 1;

        for (int i = index + 1; i < bars.length; i++) {
            if (bars[i] != bars[index]) return i;
        }
        return notes.size() - 1;
    }

    private int firstStepOfBar(int bar) {
        for (int i = 0; i < bars.length; i++) {
            if (bars[i] == bar) return i;
        }
        return 0;
    }

    /** Подпись позиции: "Такт 5 из 40 · 37 / 400" или "37 / 400". */
    private String progressText(int index) {
        int shown = Math.min(index, notes.size());
        String steps = shown + " / " + notes.size();
        if (bars == null || notes.isEmpty()) return steps;

        int bar = bars[Math.min(index, notes.size() - 1)] + 1;
        int barTotal = bars[bars.length - 1] + 1;
        return "Такт " + bar + " из " + barTotal + " · " + steps;
    }

    // подпись над бегунком ползунка
    private String positionLabel(int index) {
        if (bars != null && index < bars.length) return "Такт " + (bars[index] + 1);
        return String.valueOf(index + 1);
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
        needsReattack = false;
        chordMatchedFrames = 0;
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
            String name = current.getDisplayName();
            // длинные подписи (ноты аккорда без названия) не влезают крупным шрифтом
            textCurrentNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, name.length() <= 5 ? 72 : 36);
            textCurrentNote.setText(name);
            textPosition.setText(current.isChord()
                    ? "Аккорд: " + current.notesListing()
                    : "Струна " + current.getStringNumber() + " · лад " + current.getFret());
        } else {
            textCurrentNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 72);
            textCurrentNote.setText("✓");
            textPosition.setText("Урок пройден!");
        }
        textProgress.setText(progressText(currentIndex));
        if (notes.size() >= 2) {
            seekSlider.setValue(Math.min(currentIndex, notes.size() - 1));
        }

        tabView.setCurrentIndex(currentIndex);
        fretboardView.setLessonMarkers(current, next);
    }

    // вызывается в главном потоке (так гарантирует PitchDetector)
    private void onPitch(double frequencyHz, double level) {
        if (isComplete()) return;
        TabNote expected = notes.get(currentIndex);
        // аккорды проверяются по хромаграмме в onChromaFrame
        if (expected.isChord()) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastAdvanceTime < ADVANCE_COOLDOWN_MS) return;

        NoteUtils.NoteInfo detected = NoteUtils.frequencyToNote(frequencyHz);
        if (detected == null) return;

        if (waitingForReattack(level)) return;
        textHeard.setText("Слышу: " + detected.fullName);

        if (detected.midi != expected.getMidi()) {
            correctSince = 0L;
            return;
        }

        if (correctSince == 0L) {
            correctSince = now;
        } else if (now - correctSince >= HOLD_MS) {
            advance(now);
        }
    }

    // вызывается в главном потоке (так гарантирует PitchDetector)
    private void onChromaFrame(double[] chroma, double level) {
        if (isComplete()) return;
        TabNote expected = notes.get(currentIndex);
        if (!expected.isChord()) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastAdvanceTime < ADVANCE_COOLDOWN_MS) return;

        if (waitingForReattack(level)) return;
        textHeard.setText("Слышу: " + describeChroma(chroma));

        if (!ChordMatcher.matches(chroma, expected.getPitchClasses())) {
            chordMatchedFrames = 0;
            return;
        }
        chordMatchedFrames++;
        if (chordMatchedFrames >= CHORD_FRAMES) advance(now);
    }

    /**
     * Следующий шаг совпадает с только что сыгранным: ждём, пока громкость упадёт
     * и снова резко вырастет (новый удар по струнам). true — пока ждём.
     */
    private boolean waitingForReattack(double level) {
        if (!needsReattack) return false;
        minLevelSinceAdvance = Math.min(minLevelSinceAdvance, level);
        if (level < minLevelSinceAdvance * ATTACK_RATIO) {
            textHeard.setText(notes.get(currentIndex).isChord()
                    ? "Сыграйте этот аккорд ещё раз"
                    : "Сыграйте эту ноту ещё раз");
            return true;
        }
        needsReattack = false;
        return false;
    }

    /** Самые громкие ноты хромаграммы, например "E B G#". */
    private static String describeChroma(double[] chroma) {
        StringBuilder sb = new StringBuilder();
        boolean[] used = new boolean[12];
        for (int k = 0; k < 4; k++) {
            int best = -1;
            for (int pc = 0; pc < 12; pc++) {
                if (!used[pc] && chroma[pc] >= 0.35 && (best < 0 || chroma[pc] > chroma[best])) best = pc;
            }
            if (best < 0) break;
            used[best] = true;
            if (sb.length() > 0) sb.append(' ');
            sb.append(NoteUtils.NOTE_NAMES[best]);
        }
        return sb.length() > 0 ? sb.toString() : "…";
    }

    private void advance(long now) {
        TabNote played = notes.get(currentIndex);
        currentIndex++;
        correctSince = 0L;
        chordMatchedFrames = 0;
        lastAdvanceTime = now;

        needsReattack = !isComplete() && isSameStep(played, notes.get(currentIndex));
        minLevelSinceAdvance = Double.MAX_VALUE;

        if (isComplete()) {
            flash(0.6f, 700);
            pauseLesson();
            textHeard.setText("Отлично! Все ноты сыграны");
        } else {
            flash(0.35f, 350);
        }
        updateLessonUi();
    }

    // тот же аккорд (по набору нот) или та же нота (точная высота)
    private static boolean isSameStep(TabNote a, TabNote b) {
        if (a.isChord() != b.isChord()) return false;
        return a.isChord() ? a.samePitchClasses(b) : a.getMidi() == b.getMidi();
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
