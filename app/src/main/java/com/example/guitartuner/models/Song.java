package com.example.guitartuner.models;

public class Song {

    private final String title;
    private final String filePath; // null для демо-записей, путь — для импортированных

    public Song(String title) {
        this(title, null);
    }

    public Song(String title, String filePath) {
        this.title = title;
        this.filePath = filePath;
    }

    public String getTitle() {
        return title;
    }

    public String getFilePath() {
        return filePath;
    }
}