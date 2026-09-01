package com.example.guitartuner.models;

import java.util.List;

public class Lesson {

    private final String title;
    private final LessonDifficulty difficulty;
    private final List<String> noteSequence;

    public Lesson(String title, LessonDifficulty difficulty, List<String> noteSequence) {
        this.title = title;
        this.difficulty = difficulty;
        this.noteSequence = noteSequence;
    }

    public String getTitle() {
        return title;
    }

    public LessonDifficulty getDifficulty() {
        return difficulty;
    }

    public List<String> getNoteSequence() {
        return noteSequence;
    }
}