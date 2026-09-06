package com.example.guitartuner.tuner;

public class NoteUtils {

    public static final String[] NOTE_NAMES = {
            "C", "C#", "D", "D#", "E", "F",
            "F#", "G", "G#", "A", "A#", "B"
    };

    public static class NoteInfo {
        public final String name;       // например "E" (без октавы, для обратной совместимости)
        public final int octave;        // например 4
        public final String fullName;   // например "E4"
        public final int midi;
        public final double cents;

        public NoteInfo(String name, int octave, int midi, double cents) {
            this.name = name;
            this.octave = octave;
            this.fullName = name + octave;
            this.midi = midi;
            this.cents = cents;
        }
    }

    public static NoteInfo frequencyToNote(double freq) {
        if (freq <= 0) return null;

        // MIDI A4 = 69, 440 Гц
        double midiExact = 69 + 12 * (Math.log(freq / 440.0) / Math.log(2));
        int midi = (int) Math.round(midiExact);

        double targetFreq = 440.0 * Math.pow(2, (midi - 69) / 12.0);
        double cents = 1200 * (Math.log(freq / targetFreq) / Math.log(2));

        int noteIndex = ((midi % 12) + 12) % 12;
        int octave = (midi / 12) - 1;

        return new NoteInfo(NOTE_NAMES[noteIndex], octave, midi, cents);
    }
}