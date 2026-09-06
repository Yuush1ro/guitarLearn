package com.example.guitartuner.models;

import com.example.guitartuner.utils.GuitarNoteUtils;

import java.util.ArrayList;
import java.util.List;

public class Lesson {

    private final String title;
    private final List<TabNote> notes;
    private final LessonDifficulty difficulty;

    public Lesson(String title) {
        this(title, new ArrayList<>());
    }

    public Lesson(String title, List<TabNote> notes) {
        this(title, notes, LessonDifficulty.MEDIUM);
    }

    public Lesson(String title, List<TabNote> notes, LessonDifficulty difficulty) {
        this.title = title;
        this.notes = notes;
        this.difficulty = difficulty;
    }

    public String getTitle() {
        return title;
    }

    public List<TabNote> getNotes() {
        return notes;
    }

    public LessonDifficulty getDifficulty() {
        return difficulty;
    }

    // Имена нот без октавы — для старой мини-игры с падающими нотами
    public List<String> getNoteSequence() {
        List<String> sequence = new ArrayList<>();
        for (TabNote note : notes) {
            String fullName = GuitarNoteUtils.getNoteName(note.getStringNumber(), note.getFret());
            String baseName = fullName.replaceAll("\\d+$", ""); // убираем цифру октавы
            sequence.add(baseName);
        }
        return sequence;
    }
}