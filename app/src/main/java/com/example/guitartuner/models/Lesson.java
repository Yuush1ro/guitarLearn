package com.example.guitartuner.models;

import java.util.ArrayList;
import java.util.List;

public class Lesson {

    private final String title;
    private final List<TabNote> notes;

    public Lesson(String title) {
        this(title, new ArrayList<>());
    }

    public Lesson(String title, List<TabNote> notes) {
        this.title = title;
        this.notes = notes;
    }

    public String getTitle() {
        return title;
    }

    public List<TabNote> getNotes() {
        return notes;
    }
}