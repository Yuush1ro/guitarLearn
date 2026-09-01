package com.example.guitartuner.models;

public enum LessonDifficulty {
    EASY("Лёгкий", 1500),
    MEDIUM("Средний", 1100),
    HARD("Сложный", 800);

    public final String label;
    public final long spawnIntervalMs;

    LessonDifficulty(String label, long spawnIntervalMs) {
        this.label = label;
        this.spawnIntervalMs = spawnIntervalMs;
    }
}