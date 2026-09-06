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
import androidx.core.app.ActivityCompat;
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
    private static final int REQ_RECORD_AUDIO = 3;

    private TabView tabView;
    private TextView titleView;
    private TextView currentNoteView;
    private Button btnPlay;

    private String title;
    private List<TabNote> notes;

    private PitchDetector pitchDetector;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
            currentNoteView.setText("Урок завершён!");
            btnPlay.setText("ЕЩЁ РАЗ");
            stopListening();
        });

        btnPlay.setOnClickListener(v -> {
            tabView.reset();
            currentNoteView.setText("—");
            btnPlay.setText("ИГРАТЬ");
            startListening();
        });

        return view;
    }

    private void startListening() {

        if (ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
            return;
        }

        if (pitchDetector != null) return;

        pitchDetector = new PitchDetector(frequency ->
                mainHandler.post(() -> tabView.onPitchDetected(frequency)));
        pitchDetector.start();
        tabView.play();
    }

    private void stopListening() {
        if (pitchDetector != null) {
            pitchDetector.stop();
            pitchDetector = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        tabView.pause();
        stopListening();
    }
}