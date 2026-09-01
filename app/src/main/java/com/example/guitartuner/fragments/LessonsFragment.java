package com.example.guitartuner.fragments;

import android.os.Bundle;
import android.view.*;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.guitartuner.R;
import com.example.guitartuner.adapters.LessonsAdapter;
import com.example.guitartuner.models.Lesson;
import com.example.guitartuner.models.TabNote;

import java.util.ArrayList;
import java.util.List;

public class LessonsFragment extends Fragment {

    RecyclerView recyclerView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_lessons, container, false);

        recyclerView = view.findViewById(R.id.recyclerLessons);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        List<Lesson> lessons = buildLessons();

        LessonsAdapter adapter = new LessonsAdapter(lessons, lesson -> {
            LessonDetailFragment detailFragment = LessonDetailFragment.newInstance(lesson);
            requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, detailFragment)
                    .addToBackStack(null)
                    .commit();
        });

        recyclerView.setAdapter(adapter);

        return view;
    }

    private List<Lesson> buildLessons() {
        List<Lesson> lessons = new ArrayList<>();
        lessons.add(new Lesson("Урок 1: Перебор всех струн", buildOpenStringsLesson()));
        lessons.add(new Lesson("Урок 2: Хроматическая гамма", buildChromaticLesson()));
        lessons.add(new Lesson("Урок 3: Гамма До мажор", buildCMajorLesson()));
        return lessons;
    }

    // Урок 1: открытые струны от басовой (6) к самой тонкой (1)
    private List<TabNote> buildOpenStringsLesson() {
        List<TabNote> notes = new ArrayList<>();
        for (int string = 6; string >= 1; string--) {
            notes.add(new TabNote(string, 0));
        }
        return notes;
    }

    // Урок 2: на каждой струне лады 0-1-2-3-4, затем переход на следующую струну —
    // классическое хроматическое упражнение
    private List<TabNote> buildChromaticLesson() {
        List<TabNote> notes = new ArrayList<>();
        for (int string = 6; string >= 1; string--) {
            for (int fret = 0; fret <= 4; fret++) {
                notes.add(new TabNote(string, fret));
            }
        }
        return notes;
    }

    // Урок 3: гамма До мажор (C D E F G A B C...), две октавы,
    // классическая аппликатура начиная с 3 лада струны Ля
    private List<TabNote> buildCMajorLesson() {
        List<TabNote> notes = new ArrayList<>();
        notes.add(new TabNote(5, 3)); // C3
        notes.add(new TabNote(4, 0)); // D3
        notes.add(new TabNote(4, 2)); // E3
        notes.add(new TabNote(4, 3)); // F3
        notes.add(new TabNote(3, 0)); // G3
        notes.add(new TabNote(3, 2)); // A3
        notes.add(new TabNote(2, 0)); // B3
        notes.add(new TabNote(2, 1)); // C4
        notes.add(new TabNote(2, 3)); // D4
        notes.add(new TabNote(1, 0)); // E4
        notes.add(new TabNote(1, 1)); // F4
        notes.add(new TabNote(1, 3)); // G4
        notes.add(new TabNote(1, 5)); // A4
        notes.add(new TabNote(1, 7)); // B4
        notes.add(new TabNote(1, 8)); // C5
        return notes;
    }
}