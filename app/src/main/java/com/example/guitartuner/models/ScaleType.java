package com.example.guitartuner.models;

public enum ScaleType {
    CHROMATIC("Хроматическая", 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
    MAJOR("Мажор", 0, 2, 4, 5, 7, 9, 11),
    MINOR("Натуральный минор", 0, 2, 3, 5, 7, 8, 10),
    MAJOR_PENTATONIC("Мажорная пентатоника", 0, 2, 4, 7, 9),
    MINOR_PENTATONIC("Минорная пентатоника", 0, 3, 5, 7, 10);

    public final String label;
    // интервалы от тоники в полутонах
    private final int[] intervals;

    ScaleType(String label, int... intervals) {
        this.label = label;
        this.intervals = intervals;
    }

    /** Ноты гаммы как номера 0..11 (0 = C), начиная с тоники. */
    public int[] pitchClasses(int rootPitchClass) {
        int[] result = new int[intervals.length];
        for (int i = 0; i < intervals.length; i++) {
            result[i] = (rootPitchClass + intervals[i]) % 12;
        }
        return result;
    }
}
