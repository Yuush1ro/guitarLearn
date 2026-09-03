package com.example.guitartuner.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.example.guitartuner.R;
import com.example.guitartuner.models.FallingNote;
import com.example.guitartuner.models.Lesson;
import com.example.guitartuner.tuner.NoteUtils;
import com.example.guitartuner.tuner.PitchDetector;
import com.example.guitartuner.views.NoteFallView;

import java.util.ArrayList;
import java.util.List;

public class LessonGameFragment extends Fragment {

    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_INTERVAL = "arg_interval";
    private static final String ARG_NOTES = "arg_notes";

    private static final long FALL_DURATION_MS = 3000;
    private static final float HIT_WINDOW = 0.08f;

    private NoteFallView noteFallView;
    private TextView textLessonTitle;
    private TextView textScore;
    private TextView textDetectedNote;
    private Button btnStartGame;

    private PitchDetector pitchDetector;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<FallingNote> activeNotes = new ArrayList<>();
    private List<String> noteSequence;
    private long spawnIntervalMs;
    private String lessonTitle;

    private boolean running = false;
    private int nextNoteIndex = 0;
    private int score = 0;
    private volatile String lastDetectedNote = null;

    private long lastSpawnTime = 0;
    private long lastFrameTime = 0;
// problem here
    public static LessonGameFragment newInstance(Lesson lesson) {
        LessonGameFragment fragment = new LessonGameFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, lesson.getTitle());
        args.putLong(ARG_INTERVAL, lesson.getDifficulty().spawnIntervalMs);
        args.putStringArrayList(ARG_NOTES, new ArrayList<>(lesson.getNoteSequence()));
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_lesson_game, container, false);

        textLessonTitle = view.findViewById(R.id.textLessonTitle);
        textScore = view.findViewById(R.id.textScore);
        textDetectedNote = view.findViewById(R.id.textDetectedNote);
        noteFallView = view.findViewById(R.id.noteFallView);
        btnStartGame = view.findViewById(R.id.btnStartGame);

        Bundle args = getArguments();
        if (args != null) {
            lessonTitle = args.getString(ARG_TITLE, "Урок");
            spawnIntervalMs = args.getLong(ARG_INTERVAL, 1200);
            noteSequence = args.getStringArrayList(ARG_NOTES);
        }
        if (noteSequence == null) noteSequence = new ArrayList<>();

        textLessonTitle.setText(lessonTitle);

        btnStartGame.setOnClickListener(v -> {
            if (!running) startGame();
            else stopGame();
        });

        return view;
    }

    private void startGame() {

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 2);
            return;
        }

        activeNotes.clear();
        nextNoteIndex = 0;
        score = 0;
        updateScoreLabel();

        running = true;
        btnStartGame.setText("СТОП");

        lastSpawnTime = System.currentTimeMillis();
        lastFrameTime = lastSpawnTime;

        pitchDetector = new PitchDetector(frequency -> {
            NoteUtils.NoteInfo info = NoteUtils.frequencyToNote(frequency);
            if (info != null) {
                lastDetectedNote = info.name;
                mainHandler.post(() -> textDetectedNote.setText("Нота: " + info.name));
            }
        });
        pitchDetector.start();

        mainHandler.post(gameLoop);
    }

    private void stopGame() {
        running = false;
        btnStartGame.setText("СТАРТ");

        if (pitchDetector != null) {
            pitchDetector.stop();
            pitchDetector = null;
        }

        mainHandler.removeCallbacks(gameLoop);
    }

    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            if (!running) return;

            long now = System.currentTimeMillis();
            long delta = now - lastFrameTime;
            lastFrameTime = now;

            if (nextNoteIndex < noteSequence.size()
                    && now - lastSpawnTime >= spawnIntervalMs) {

                activeNotes.add(new FallingNote(noteSequence.get(nextNoteIndex)));
                nextNoteIndex++;
                lastSpawnTime = now;
            }

            List<FallingNote> toRemove = new ArrayList<>();

            for (FallingNote note : activeNotes) {

                if (note.hit || note.missed) {
                    note.progress += delta / (float) FALL_DURATION_MS * 0.4f;
                    if (note.progress > 1.3f) toRemove.add(note);
                    continue;
                }

                note.progress += delta / (float) FALL_DURATION_MS;

                boolean inWindow = Math.abs(note.progress - 1f) <= HIT_WINDOW;

                if (inWindow
                        && lastDetectedNote != null
                        && lastDetectedNote.equals(note.noteName)) {

                    note.hit = true;
                    score++;
                    updateScoreLabel();

                } else if (note.progress > 1f + HIT_WINDOW) {
                    note.missed = true;
                }
            }

            activeNotes.removeAll(toRemove);
            noteFallView.setNotes(activeNotes);

            if (nextNoteIndex >= noteSequence.size() && activeNotes.isEmpty()) {
                stopGame();
                textDetectedNote.setText("Урок завершён! Счёт: " + score);
                return;
            }

            mainHandler.postDelayed(this, 16);
        }
    };

    private void updateScoreLabel() {
        textScore.setText("Счёт: " + score);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopGame();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (running) stopGame();
    }
}