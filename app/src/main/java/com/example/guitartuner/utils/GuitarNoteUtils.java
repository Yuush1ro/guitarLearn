package com.example.guitartuner.utils;

public class GuitarNoteUtils {

    private static final String[] NOTE_NAMES = {
            "C", "C#", "D", "D#", "E", "F",
            "F#", "G", "G#", "A", "A#", "B"
    };

    // MIDI-номера открытых струн в стандартном строе
    // индекс 0 = струна 1 (высокая E4), индекс 5 = струна 6 (низкая E2)
    private static final int[] OPEN_STRING_MIDI = {
            64, // 1: E4
            59, // 2: B3
            55, // 3: G3
            50, // 4: D3
            45, // 5: A2
            40  // 6: E2
    };

    public static int getMidi(int stringNumber, int fret) {
        if (stringNumber < 1 || stringNumber > 6) {
            throw new IllegalArgumentException("stringNumber must be 1..6");
        }
        return OPEN_STRING_MIDI[stringNumber - 1] + fret;
    }

    /** Возвращает имя ноты с октавой, например "E4" или "C#3" */
    public static String getNoteName(int stringNumber, int fret) {
        int midi = getMidi(stringNumber, fret);
        String name = NOTE_NAMES[((midi % 12) + 12) % 12];
        int octave = (midi / 12) - 1;
        return name + octave;
    }
}