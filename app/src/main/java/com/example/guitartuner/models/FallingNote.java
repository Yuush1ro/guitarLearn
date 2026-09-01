package com.example.guitartuner.models;

public class FallingNote {

    public final String noteName;
    public float progress; // 0f (верх) -> 1f (линия попадания)
    public boolean hit = false;
    public boolean missed = false;

    public FallingNote(String noteName) {
        this.noteName = noteName;
    }
}