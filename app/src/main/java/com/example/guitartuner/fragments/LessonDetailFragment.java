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
import com.example.guitartuner.audio.AudioEngine;
import com.example.guitartuner.models.Lesson;
import com.example.guitartuner.models.LessonLibrary;
import com.example.guitartuner.models.PlayTiming;
import com.example.guitartuner.models.TabNote;
import com.example.guitartuner.practice.StepMatcher;
import com.example.guitartuner.practice.TimedMatcher;
import com.example.guitartuner.songs.SongStore;
import com.example.guitartuner.tuner.AudioFrame;
import com.example.guitartuner.tuner.NoteUtils;
import com.example.guitartuner.tuner.PitchDetector;
import com.example.guitartuner.ui.Anim;
import com.example.guitartuner.utils.GuitarNoteUtils;
import com.example.guitartuner.views.FretboardView;
import com.example.guitartuner.views.ResultMapView;
import com.example.guitartuner.views.TabView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.slider.Slider;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Прохождение урока или песни. Ноты показываются бегущей лентой или на грифе.
 *
 * Два режима:
 *  - "Свой темп": урок ждёт, пока нота (точная высота, с октавой) или аккорд сыграны верно;
 *  - "Под метроном": ноты идут во времени по темпу песни (или 1 нота = 1 доля для уроков),
 *    вовремя сыгранные засчитываются, пропущенные отмечаются красным.
 */
public class LessonDetailFragment extends Fragment {

    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_STRINGS = "arg_strings";
    private static final String ARG_FRETS = "arg_frets";
    private static final String ARG_REGION_SELECTABLE = "arg_region_selectable";
    private static final String ARG_SONG_PATH = "arg_song_path";

    // под метрономом подсвечиваем ноту чуть раньше её времени — чтобы успеть сыграть
    private static final double DISPLAY_EARLY_MS = 60;
    // такт отсчёта перед стартом под метроном
    private static final int COUNT_IN_BEATS = 4;

    private static final double MIN_SPEED = 0.25;
    private static final double MAX_SPEED = 2.0;
    private static final double SPEED_STEP = 0.05;

    private static final String PREFS = "lessons";
    private static final String KEY_VIEW_MODE = "view_mode_fretboard";
    private static final String KEY_PLAY_MODE = "play_mode_metronome";
    private static final String KEY_CLICKS = "metronome_clicks";
    private static final String KEY_VOICE = "metronome_voice";
    private static final String KEY_SPEED_PREFIX = "speed_";
    private static final String KEY_REGION_START = "chromatic_region_start";
    private static final String KEY_REGION_END = "chromatic_region_end";
    // подстроенная задержка микрофона — у телефона она постоянная, запоминаем
    private static final String KEY_LATENCY = "input_latency_ms";
    // результаты последней игры (какие ноты сыграны, какие пропущены) — по песне/уроку
    private static final String KEY_RESULTS_PREFIX = "results_";

    // ---------- views ----------
    private TextView titleView;
    private MaterialButtonToggleGroup toggleViewMode;
    private View regionPanel;
    private TextView textRegion;
    private RangeSlider rangeRegion;
    private TextView textCurrentNote;
    private TextView textPosition;
    private TextView textHeard;
    private TextView textProgress;
    private TextView textStats;
    private LinearProgressIndicator progressLesson;
    private TabView tabView;
    private FretboardView fretboardView;
    private View seekPanel;
    private Slider seekSlider;
    private MaterialButtonToggleGroup togglePlayMode;
    private TextView textModeHint;
    private View tempoPanel;
    private TextView textTempo;
    private TextView textTempoInfo;
    private MaterialSwitch switchClicks;
    private MaterialSwitch switchVoice;
    private Button btnPlay;
    private View flashOverlay;
    private ResultMapView resultMap;
    private View mistakesPanel;
    private TextView textMistakes;
    private Button btnClearResults;

    // ---------- данные урока / песни ----------
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
    // время нот из файла песни; null — уроки и старые песни (тогда 1 нота = 1 доля)
    private PlayTiming songTiming;
    private PlayTiming lessonTiming;
    // ключ для запоминания скорости: имя файла песни или "lessons"
    private String speedKey = "lessons";

    // ---------- состояние игры ----------
    private int currentIndex = 0;
    private int lastShownIndex = -1;

    // результаты шагов (StepMatcher.HIT/MISS — те же значения, что TabView.RESULT_*)
    private byte[] results = new byte[0];
    private int hits = 0;
    private int misses = 0;

    // сопоставление сыгранного с нотами: "свой темп" и "под метроном"
    private StepMatcher freeMatcher;
    private TimedMatcher timedMatcher;

    private boolean metronomeMode = false;
    private double speed = 1.0;
    // идёт игра под метроном (транспорт звука запущен)
    private boolean timedRunning = false;
    // идёт такт отсчёта перед первой нотой
    private boolean countingIn = false;
    private int runStartIndex = 0;

    private final AudioEngine audioEngine = new AudioEngine();

    // хромаграмма нужна для аккордов — считаем её всегда, это один FFT на кадр
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
                if (granted) {
                    startLesson();
                } else {
                    Toast.makeText(requireContext(),
                            "Без доступа к микрофону урок не работает",
                            Toast.LENGTH_SHORT).show();
                }
            });

    // кадр игры под метроном: двигает ленту по времени, отмечает пропущенные ноты
    private final Runnable timedFrame = new Runnable() {
        @Override
        public void run() {
            if (!timedRunning || tabView == null) return;
            onTimedFrame();
            if (timedRunning) tabView.postOnAnimation(this);
        }
    };

    // ---------- создание ----------

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
        onNotesChanged();
    }

    private void loadSong(File file) {
        speedKey = file.getName();
        SongStore.ConvertedSong song = SongStore.load(file);
        if (song == null) {
            title = "Не удалось открыть песню";
            return;
        }

        title = song.trackName.isEmpty() ? song.title : song.title + " · " + song.trackName;
        notes = song.notes;
        bars = song.bars;
        songTiming = song.timing;

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

    /** Ноты урока изменились (загрузка, смена участка): сбрасываем результаты и время. */
    private void onNotesChanged() {
        results = new byte[notes.size()];
        hits = 0;
        misses = 0;
        currentIndex = 0;
        lessonTiming = PlayTiming.evenBeats(notes.size(), PlayTiming.LESSON_BPM);
        freeMatcher = new StepMatcher(notes, results);
        timedMatcher = new TimedMatcher(notes, timing(), results);
        if (getContext() != null) {
            timedMatcher.setLatencyMs(prefs().getFloat(KEY_LATENCY, 60f));
            restoreResults();
        }
    }

    private PlayTiming timing() {
        return songTiming != null ? songTiming : lessonTiming;
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
        textStats = view.findViewById(R.id.textStats);
        progressLesson = view.findViewById(R.id.progressLesson);
        tabView = view.findViewById(R.id.tabView);
        fretboardView = view.findViewById(R.id.fretboardView);
        seekPanel = view.findViewById(R.id.seekPanel);
        seekSlider = view.findViewById(R.id.seekSlider);
        togglePlayMode = view.findViewById(R.id.togglePlayMode);
        textModeHint = view.findViewById(R.id.textModeHint);
        tempoPanel = view.findViewById(R.id.tempoPanel);
        textTempo = view.findViewById(R.id.textTempo);
        textTempoInfo = view.findViewById(R.id.textTempoInfo);
        switchClicks = view.findViewById(R.id.switchClicks);
        switchVoice = view.findViewById(R.id.switchVoice);
        btnPlay = view.findViewById(R.id.btnPlayLesson);
        flashOverlay = view.findViewById(R.id.flashOverlay);
        resultMap = view.findViewById(R.id.resultMap);
        mistakesPanel = view.findViewById(R.id.mistakesPanel);
        textMistakes = view.findViewById(R.id.textMistakes);
        btnClearResults = view.findViewById(R.id.btnClearResults);

        titleView.setText(title);
        tabView.setStringLabels(stringLabels);
        tabView.setNotes(notes);
        tabView.setStepResults(results);
        fretboardView.setFretCount(fretCount);

        setupSeek(view);
        setupViewModeToggle();
        setupRegion();
        setupPlayMode(view);

        btnPlay.setOnClickListener(v -> {
            if (pitchDetector.isRunning()) pauseLesson();
            else startLesson();
        });
        view.findViewById(R.id.btnListen).setOnClickListener(v -> listenCurrent());
        setupMistakes(view);

        updateLessonUi();
        Anim.cascadeIn(view.findViewById(R.id.lessonContent));
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
        onNotesChanged();
        tabView.setNotes(notes);
        tabView.setStepResults(results);
        configureSeek();
        textHeard.setText("Нажмите ИГРАТЬ");
        updateRegionLabel();
        updateTempoUi();
        updateLessonUi();
    }

    private void updateRegionLabel() {
        textRegion.setText("Лады " + regionStart + "–" + regionEnd + " · " + notes.size()
                + " нот. Двигайте ползунок или нажмите на гриф (в виде «Гриф»)");
    }

    // ---------- режим: свой темп / под метроном ----------

    private void setupPlayMode(View view) {
        SharedPreferences p = prefs();
        metronomeMode = p.getBoolean(KEY_PLAY_MODE, false);
        speed = clampSpeed(p.getFloat(KEY_SPEED_PREFIX + speedKey, 1f));
        switchClicks.setChecked(p.getBoolean(KEY_CLICKS, true));
        switchVoice.setChecked(p.getBoolean(KEY_VOICE, false));

        togglePlayMode.check(metronomeMode ? R.id.btnModeMetronome : R.id.btnModeFree);
        togglePlayMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            boolean metronome = checkedId == R.id.btnModeMetronome;
            if (metronome == metronomeMode) return;
            pauseLesson();
            metronomeMode = metronome;
            prefs().edit().putBoolean(KEY_PLAY_MODE, metronome).apply();
            updateTempoUi();
            updateLessonUi();
        });

        view.findViewById(R.id.btnTempoDown).setOnClickListener(v -> setSpeed(speed - SPEED_STEP));
        view.findViewById(R.id.btnTempoUp).setOnClickListener(v -> setSpeed(speed + SPEED_STEP));
        view.findViewById(R.id.chipSpeed50).setOnClickListener(v -> setSpeed(0.5));
        view.findViewById(R.id.chipSpeed75).setOnClickListener(v -> setSpeed(0.75));
        view.findViewById(R.id.chipSpeed100).setOnClickListener(v -> setSpeed(1.0));

        switchClicks.setOnCheckedChangeListener((b, checked) -> {
            prefs().edit().putBoolean(KEY_CLICKS, checked).apply();
            restartTimedIfRunning();
        });
        switchVoice.setOnCheckedChangeListener((b, checked) -> {
            prefs().edit().putBoolean(KEY_VOICE, checked).apply();
            restartTimedIfRunning();
        });

        updateTempoUi();
    }

    private static double clampSpeed(double value) {
        // округляем до шага, чтобы не копилась погрешность от + / −
        double rounded = Math.round(value / SPEED_STEP) * SPEED_STEP;
        return Math.max(MIN_SPEED, Math.min(MAX_SPEED, rounded));
    }

    private void setSpeed(double value) {
        speed = clampSpeed(value);
        prefs().edit().putFloat(KEY_SPEED_PREFIX + speedKey, (float) speed).apply();
        updateTempoUi();
        restartTimedIfRunning();
    }

    private void updateTempoUi() {
        tempoPanel.setVisibility(metronomeMode ? View.VISIBLE : View.GONE);

        if (!metronomeMode) {
            textModeHint.setText("Урок ждёт, пока вы сыграете подсвеченную ноту верно");
            return;
        }

        PlayTiming timing = timing();
        int bpm = (int) Math.round(timing.baseBpm * speed);
        textTempo.setText(bpm + " BPM");
        int percent = (int) Math.round(speed * 100);
        if (songTiming != null) {
            textTempoInfo.setText("скорость " + percent + "% · темп песни " + Math.round(timing.baseBpm) + " BPM");
            textModeHint.setText("Ноты идут в темпе песни — играйте вовремя. Перед стартом звучит такт отсчёта");
        } else {
            textTempoInfo.setText("скорость " + percent + "% · одна нота на долю");
            textModeHint.setText(bars != null || stringLabels != null
                    ? "В песне нет данных о темпе: долгое нажатие на песню → «Выбрать другую партию», чтобы сконвертировать заново"
                    : "Ноты идут по одной на долю метронома. Перед стартом звучит такт отсчёта");
        }
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

    /** Перейти к шагу index: урок продолжится с него (если идёт — сразу с этого места). */
    private void seekTo(int index) {
        if (notes.isEmpty()) return;
        currentIndex = Math.max(0, Math.min(notes.size() - 1, index));
        freeMatcher.seek(currentIndex);
        // на паузе результаты не стираем: ошибки должны остаться видны при перемотке

        if (pitchDetector.isRunning()) {
            if (timedRunning) {
                // перемотка во время игры под метроном — новая попытка с этого места
                clearResultsFrom(currentIndex);
                startTimedTransport();
            }
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

    // ---------- старт / стоп ----------

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
        clearResultsFrom(currentIndex);
        freeMatcher.seek(currentIndex);

        pitchDetector.start();
        tabView.setPlaying(true);
        btnPlay.setText("СТОП");

        if (metronomeMode) {
            runStartIndex = currentIndex;
            startTimedTransport();
            timedRunning = true;
            tabView.postOnAnimation(timedFrame);
            textHeard.setText("Приготовьтесь…");
        } else {
            textHeard.setText("Сыграйте подсвеченную ноту");
        }
        updateLessonUi();
    }

    private void pauseLesson() {
        pitchDetector.stop();
        if (timedRunning) {
            timedRunning = false;
            audioEngine.stopTransport();
            prefs().edit().putFloat(KEY_LATENCY, (float) timedMatcher.latencyMs()).apply();
        }
        if (getContext() != null) saveResults();
        if (btnPlay == null) return;

        tabView.setPlaying(false);
        // лента встаёт ровно на текущую ноту
        tabView.setCurrentIndex(currentIndex);
        btnPlay.setText(isComplete() ? "ЕЩЁ РАЗ" : "ИГРАТЬ");
    }

    private void clearResultsFrom(int from) {
        for (int i = Math.max(0, from); i < results.length; i++) results[i] = StepMatcher.NONE;
        recountResults();
        tabView.setStepResults(results);
    }

    private void recountResults() {
        hits = 0;
        misses = 0;
        for (byte r : results) {
            if (r == StepMatcher.HIT) hits++;
            else if (r == StepMatcher.MISS) misses++;
        }
    }

    // ---------- игра под метроном ----------

    /** Запускает звук (отсчёт, щелчки, озвучку нот) с текущей ноты. */
    private void startTimedTransport() {
        PlayTiming timing = timing();
        int from = Math.min(currentIndex, notes.size() - 1);
        runStartIndex = from;
        countingIn = true;
        double startMs = timing.stepStart[from];
        double beatMs = timing.beatMs();

        // такт отсчёта + щелчки песни начиная с этой ноты
        List<Double> times = new ArrayList<>();
        List<Boolean> accents = new ArrayList<>();
        for (int k = COUNT_IN_BEATS; k >= 1; k--) {
            times.add(startMs - k * beatMs);
            accents.add(k == COUNT_IN_BEATS);
        }
        if (switchClicks.isChecked()) {
            double end = timing.endMs();
            for (int i = 0; i < timing.clickTimes.length; i++) {
                double t = timing.clickTimes[i];
                if (t >= startMs - 1 && t <= end) {
                    times.add(t);
                    accents.add(timing.clickAccents[i]);
                }
            }
        }

        double[] clickTimes = new double[times.size()];
        boolean[] clickAccents = new boolean[times.size()];
        for (int i = 0; i < clickTimes.length; i++) {
            clickTimes[i] = times.get(i);
            clickAccents[i] = accents.get(i);
        }

        AudioEngine.Schedule schedule = new AudioEngine.Schedule(clickTimes, clickAccents,
                switchVoice.isChecked() ? timing.stepStart : null, notes);
        timedMatcher.reset(from);
        audioEngine.stopTransport();
        audioEngine.startTransport(schedule, startMs - COUNT_IN_BEATS * beatMs, speed);
    }

    private void restartTimedIfRunning() {
        if (timedRunning) startTimedTransport();
    }

    private void onTimedFrame() {
        double songMs = audioEngine.getSongTimeMs();
        if (Double.isNaN(songMs)) return;
        PlayTiming timing = timing();

        tabView.setContinuousPosition(timing.positionAt(songMs));

        double startMs = timing.stepStart[runStartIndex];
        // отсчёт идёт, пока до первой ноты больше, чем допуск "можно сыграть раньше"
        if (songMs < startMs - 150 * speed) {
            int beatsLeft = (int) Math.ceil((startMs - songMs) / timing.beatMs());
            textHeard.setText("Отсчёт: " + Math.max(1, beatsLeft));
            return;
        }
        if (countingIn) {
            countingIn = false;
            textHeard.setText("Играйте!");
        }

        // окна прошедших нот закрываются — несыгранные становятся промахами
        boolean changed = timedMatcher.onTick(songMs, speed);
        if (timedMatcher.isFinished() || songMs > timing.endMs() + 1000) {
            finishTimed();
            return;
        }

        int index = Math.max(runStartIndex, timing.indexAt(songMs, DISPLAY_EARLY_MS * speed));
        if (index != currentIndex || changed) {
            currentIndex = index;
            recountResults();
            updateLessonUi();
        }
    }

    private void finishTimed() {
        for (int i = runStartIndex; i < results.length; i++) {
            if (results[i] == StepMatcher.NONE) results[i] = StepMatcher.MISS;
        }
        recountResults();
        currentIndex = notes.size();
        pauseLesson();

        int played = hits + misses;
        int percent = played == 0 ? 0 : Math.round(hits * 100f / played);
        textHeard.setText("Готово! Вовремя сыграно " + hits + " из " + played + " (" + percent + "%)");
        flash(0.6f, 700);
        updateLessonUi();
    }

    // ---------- отображение ----------

    private void updateLessonUi() {
        TabNote current = isComplete() ? null : notes.get(currentIndex);
        TabNote next = currentIndex + 1 < notes.size() ? notes.get(currentIndex + 1) : null;

        if (current != null) {
            String name = current.getDisplayName();
            // длинные подписи (ноты аккорда без названия) не влезают крупным шрифтом
            textCurrentNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, name.length() <= 5 ? 72 : 36);
            textCurrentNote.setText(name);
            // новая нота "подпрыгивает" — глазу проще заметить смену
            if (currentIndex != lastShownIndex) Anim.pop(textCurrentNote);
            textPosition.setText(current.isChord()
                    ? "Аккорд: " + current.notesListing()
                    : "Струна " + current.getStringNumber() + " · лад " + current.getFret());
        } else {
            textCurrentNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 72);
            textCurrentNote.setText("✓");
            textPosition.setText(notes.isEmpty() ? "" : "Пройдено!");
        }

        lastShownIndex = currentIndex;
        textProgress.setText(progressText(currentIndex));
        progressLesson.setMax(Math.max(1, notes.size()));
        progressLesson.setProgressCompat(Math.min(currentIndex, notes.size()), true);
        updateStats();

        if (notes.size() >= 2) {
            seekSlider.setValue(Math.min(currentIndex, notes.size() - 1));
        }

        tabView.setCurrentIndex(currentIndex);
        tabView.setStepResults(results);
        fretboardView.setLessonMarkers(current, next);
    }

    // ---------- результаты и ошибки ----------

    private void setupMistakes(View view) {
        resultMap.setOnSeekListener(this::seekTo);
        view.findViewById(R.id.btnPrevMistake).setOnClickListener(v -> {
            int i = findMistake(Math.min(currentIndex, notes.size()) - 1, -1);
            if (i >= 0) seekTo(i);
        });
        view.findViewById(R.id.btnNextMistake).setOnClickListener(v -> {
            int i = findMistake(currentIndex + 1, 1);
            if (i >= 0) seekTo(i);
        });
        btnClearResults.setOnClickListener(v -> {
            clearResultsFrom(0);
            saveResults();
            updateLessonUi();
        });
    }

    /** Ближайший пропуск, начиная с from, в направлении step (±1); -1 — нет. */
    private int findMistake(int from, int step) {
        for (int i = from; i >= 0 && i < results.length; i += step) {
            if (results[i] == StepMatcher.MISS) return i;
        }
        return -1;
    }

    /** Карта прохождения и строка "Ошибок: N · такты …". */
    private void updateMistakes() {
        resultMap.setResults(results, Math.min(currentIndex, Math.max(0, notes.size() - 1)));
        btnClearResults.setVisibility(hits + misses > 0 ? View.VISIBLE : View.GONE);

        if (misses == 0) {
            mistakesPanel.setVisibility(View.GONE);
            return;
        }
        mistakesPanel.setVisibility(View.VISIBLE);

        StringBuilder where = new StringBuilder();
        int shown = 0;
        int lastBar = -1;
        for (int i = 0; i < results.length && shown < 8; i++) {
            if (results[i] != StepMatcher.MISS) continue;
            int place = bars != null ? bars[i] + 1 : i + 1;
            if (place == lastBar) continue;
            lastBar = place;
            if (shown > 0) where.append(", ");
            where.append(place);
            shown++;
        }
        String unit = bars != null ? "такты " : "ноты ";
        textMistakes.setText("Ошибок: " + misses + " · " + unit + where + (shown >= 8 ? "…" : ""));
    }

    private String resultsKey() {
        if (stringLabels != null) return KEY_RESULTS_PREFIX + "song_" + speedKey;
        String key = KEY_RESULTS_PREFIX + "lesson_" + title;
        return regionSelectable ? key + "_" + regionStart + "_" + regionEnd : key;
    }

    private void saveResults() {
        StringBuilder sb = new StringBuilder(results.length);
        for (byte r : results) sb.append((char) ('0' + r));
        prefs().edit().putString(resultsKey(), sb.toString()).apply();
    }

    private void restoreResults() {
        String saved = prefs().getString(resultsKey(), null);
        if (saved == null || saved.length() != results.length) return;
        for (int i = 0; i < results.length; i++) {
            int r = saved.charAt(i) - '0';
            results[i] = r >= 0 && r <= 2 ? (byte) r : StepMatcher.NONE;
        }
        recountResults();
    }

    private void updateStats() {
        int played = hits + misses;
        if (played == 0) {
            textStats.setText("");
        } else if (misses == 0) {
            textStats.setText("Верно " + hits);
        } else {
            int percent = Math.round(hits * 100f / played);
            textStats.setText("Верно " + hits + " · Мимо " + misses + " · " + percent + "%");
        }
        updateMistakes();
    }

    // ---------- звук ----------

    /** Проиграть текущую ноту или аккорд — "как это должно звучать". */
    private void listenCurrent() {
        if (notes.isEmpty()) return;
        TabNote step = notes.get(Math.min(currentIndex, notes.size() - 1));
        audioEngine.playStep(step);
    }

    // ---------- распознавание ----------

    // вызывается в главном потоке (так гарантирует PitchDetector), ~раз в 23 мс
    private void onAudioFrame(AudioFrame frame) {
        // звучит подсказка из динамика — это не игра пользователя
        if (audioEngine.isPreviewPlaying() || isComplete()) return;
        showHeard(frame);

        if (metronomeMode) {
            if (!timedRunning || countingIn) return;
            double songMs = audioEngine.getSongTimeMs();
            if (Double.isNaN(songMs)) return;
            // время песни в момент записи кадра (кадр мог постоять в очереди главного потока)
            long waitedMs = frame.captureUptimeMs > 0
                    ? Math.max(0, SystemClock.uptimeMillis() - frame.captureUptimeMs) : 0;
            double songAtCapture = songMs - waitedMs * speed;
            if (timedMatcher.onFrame(frame, songAtCapture, speed)) {
                // под метроном шаги сменяет время — здесь только отмечаем попадание
                recountResults();
                flash(0.3f, 250);
                textHeard.setText("Верно!");
                updateLessonUi();
            }
            return;
        }

        if (!freeMatcher.onFrame(frame)) {
            if (freeMatcher.isWaitingForOnset()) {
                textHeard.setText(notes.get(currentIndex).isChord()
                        ? "Сыграйте этот аккорд ещё раз"
                        : "Сыграйте эту ноту ещё раз");
            }
            return;
        }

        currentIndex = freeMatcher.index();
        recountResults();
        if (isComplete()) {
            flash(0.6f, 700);
            pauseLesson();
            textHeard.setText("Отлично! Все ноты сыграны");
        } else {
            flash(0.35f, 300);
        }
        updateLessonUi();
    }

    /** "Слышу: …" — что сейчас звучит. */
    private void showHeard(AudioFrame frame) {
        if (frame.silent) return;
        TabNote expected = notes.get(Math.min(currentIndex, notes.size() - 1));
        if (expected.isChord() && frame.chroma != null) {
            textHeard.setText("Слышу: " + describeChroma(frame.chroma));
        } else if (frame.frequency > 0) {
            textHeard.setText("Слышу: " + GuitarNoteUtils.midiToName(frame.midi()));
        }
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
        // не держим микрофон и звук, когда экран не виден
        pauseLesson();
        audioEngine.release();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        pitchDetector.stop();
        timedRunning = false;
        btnPlay = null;
        tabView = null;
    }
}
