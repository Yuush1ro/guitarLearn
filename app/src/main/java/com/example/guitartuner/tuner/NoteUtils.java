package com.example.guitartuner.tuner;

public class NoteUtils {

    public static final String[] NOTE_NAMES = {
            "C", "C#", "D", "D#", "E", "F",
            "F#", "G", "G#", "A", "A#", "B"
    };

    public static class NoteInfo {
        public final String name;
        public final int semitoneFromA4;
        public final double cents;

        public NoteInfo(String name, int semitoneFromA4, double cents) {
            this.name = name;
            this.semitoneFromA4 = semitoneFromA4;
            this.cents = cents;
        }
    }

    public static NoteInfo frequencyToNote(double freq) {
        if (freq <= 0) return null;

        double semitoneOffset = 12 * (Math.log(freq / 440.0) / Math.log(2));
        int semitone = (int) Math.round(semitoneOffset);

        double targetFreq = 440.0 * Math.pow(2, semitone / 12.0);
        double cents = 1200 * (Math.log(freq / targetFreq) / Math.log(2));

        int noteIndex = (semitone + 9) % 12;
        if (noteIndex < 0) noteIndex += 12;

        return new NoteInfo(NOTE_NAMES[noteIndex], semitone, cents);
    }
}