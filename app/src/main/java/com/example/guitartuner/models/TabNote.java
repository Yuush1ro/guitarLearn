package com.example.guitartuner.models;

public class TabNote {

    private final int stringNumber; // 1 = самая тонкая (высокая E), 6 = самая толстая (низкая E)
    private final int fret;

    public TabNote(int stringNumber, int fret) {
        this.stringNumber = stringNumber;
        this.fret = fret;
    }

    public int getStringNumber() {
        return stringNumber;
    }

    public int getFret() {
        return fret;
    }
}