package com.example.guitartuner.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
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
import com.example.guitartuner.models.TabNote;
import com.example.guitartuner.tuner.PitchDetector;
import com.example.guitartuner.views.TabView;

import java.util.ArrayList;
import java.util.List;

public class LessonDetailFragment extends Fragment {

    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_STRINGS = "arg_strings";
    private static final String ARG_FRETS = "arg_frets";

    private TabView tabView;
    private TextView titleView;
    private TextView currentNoteView;
    private Button btnPlay;

    private String title;
    private List<TabNote> notes;

    // колбэки приходят в главном потоке, можно сразу трогать View
    private final PitchDetector pitchDetector =
            new PitchDetector(frequency -> tabView.onPitchDetected(frequency));

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
        notes = new ArrayList<>();

        if (args != null) {
            title = args.getString(ARG_TITLE, "");
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
        tabView = view.findViewById(R.id.tabView);
        currentNoteView = view.findViewById(R.id.textCurrentNote);
        btnPlay = view.findViewById(R.id.btnPlayLesson);

        titleView.setText(title);
        tabView.setNotes(notes);

        tabView.setOnNotePlayedListener((note, noteName) ->
                currentNoteView.setText("Сыграно: " + noteName));

        tabView.setOnLessonCompleteListener(() -> {
            pitchDetector.stop();
            currentNoteView.setText("Урок завершён!");
            btnPlay.setText("ЕЩЁ РАЗ");
        });

        btnPlay.setOnClickListener(v -> {
            if (pitchDetector.isRunning()) {
                pauseLesson();
            } else {
                startLesson();
            }
        });

        return view;
    }

    /** Запускает урок: с начала, если он пройден, иначе продолжает с текущей ноты. */
    private void startLesson() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }

        if (tabView.isComplete()) {
            tabView.reset();
            currentNoteView.setText("—");
        }

        pitchDetector.start();
        tabView.play();
        btnPlay.setText("СТОП");
    }

    private void pauseLesson() {
        pitchDetector.stop();
        if (tabView != null) {
            tabView.pause();
            btnPlay.setText(tabView.isComplete() ? "ЕЩЁ РАЗ" : "ИГРАТЬ");
        }
    }

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
        tabView = null;
    }
}
