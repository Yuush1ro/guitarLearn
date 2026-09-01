package com.example.guitartuner.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.guitartuner.R;
import com.example.guitartuner.adapters.LessonsAdapter;
import com.example.guitartuner.models.Lesson;
import com.example.guitartuner.models.LessonDifficulty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LessonsFragment extends Fragment {

    RecyclerView recyclerView;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(
                R.layout.fragment_lessons,
                container,
                false
        );

        recyclerView = view.findViewById(R.id.recyclerLessons);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        List<Lesson> lessons = buildLessons();
        LessonsAdapter adapter = new LessonsAdapter(lessons, this::openLesson);
        recyclerView.setAdapter(adapter);

        return view;
    }

    private List<Lesson> buildLessons() {

        List<Lesson> lessons = new ArrayList<>();

        lessons.add(new Lesson("Открытые струны", LessonDifficulty.EASY,
                Arrays.asList("E", "A", "D", "G", "B", "E")));

        lessons.add(new Lesson("Открытые струны наоборот", LessonDifficulty.EASY,
                Arrays.asList("E", "B", "G", "D", "A", "E")));

        lessons.add(new Lesson("Гамма до мажор", LessonDifficulty.MEDIUM,
                Arrays.asList("C", "D", "E", "F", "G", "A", "B", "C")));

        lessons.add(new Lesson("Смешанные ноты", LessonDifficulty.MEDIUM,
                Arrays.asList("E", "G", "A", "D", "B", "E", "A", "D")));

        lessons.add(new Lesson("Хроматика", LessonDifficulty.HARD,
                Arrays.asList("E", "F", "F#", "G", "G#", "A", "A#", "B")));

        lessons.add(new Lesson("Быстрая практика", LessonDifficulty.HARD,
                Arrays.asList("E", "A", "D", "G", "B", "E", "B", "G", "D", "A", "E")));

        return lessons;
    }

    private void openLesson(Lesson lesson) {

        LessonGameFragment fragment = LessonGameFragment.newInstance(lesson);

        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit();
    }
}