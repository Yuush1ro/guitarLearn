package com.example.guitartuner.models;

public class Song {

    private final String title;
    private final String filePath; // путь к исходному файлу Guitar Pro
    private final String subtitle;

    public Song(String title) {
        this(title, null, "");
    }

    public Song(String title, String filePath) {
        this(title, filePath, "");
    }

    public Song(String title, String filePath, String subtitle) {
        this.title = title;
        this.filePath = filePath;
        this.subtitle = subtitle;
    }

    public String getTitle() {
        return title;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getSubtitle() {
        return subtitle;
    }
}
