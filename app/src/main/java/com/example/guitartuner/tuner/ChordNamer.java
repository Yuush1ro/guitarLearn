package com.example.guitartuner.tuner;

/** Название аккорда по набору нот: "Am", "E5", "G/B", "Dsus4"… или null, если не узнали. */
public final class ChordNamer {

    // интервалы от тоники (битовая маска) -> суффикс названия
    private static final int[][] TEMPLATES = {
            {mask(0, 4, 7), 0}, {mask(0, 3, 7), 1}, {mask(0, 7), 2},
            {mask(0, 4, 7, 10), 3}, {mask(0, 3, 7, 10), 4}, {mask(0, 4, 7, 11), 5},
            {mask(0, 2, 7), 6}, {mask(0, 5, 7), 7}, {mask(0, 3, 6), 8}, {mask(0, 4, 8), 9},
            {mask(0, 4, 7, 9), 10}, {mask(0, 3, 7, 9), 11}, {mask(0, 2, 4, 7), 12},
            {mask(0, 3, 6, 10), 13}, {mask(0, 3, 6, 9), 14},
            {mask(0, 4, 10), 3}, {mask(0, 3, 10), 4}, {mask(0, 5, 7, 10), 15},
    };
    private static final String[] SUFFIXES = {
            "", "m", "5", "7", "m7", "maj7", "sus2", "sus4", "dim", "aug",
            "6", "m6", "add9", "m7b5", "dim7", "7sus4"
    };

    private ChordNamer() {
    }

    private static int mask(int... intervals) {
        int m = 0;
        for (int i : intervals) m |= 1 << i;
        return m;
    }

    /**
     * @param pitchClasses какие ноты звучат (индекс 0 = C)
     * @param bassPitchClass самая низкая нота — сначала пробуем её как тонику
     */
    public static String name(boolean[] pitchClasses, int bassPitchClass) {
        int count = 0;
        for (boolean b : pitchClasses) if (b) count++;
        if (count < 2) return null;

        // сначала тоника = бас (обычный аккорд), затем остальные ноты (обращения, "C/E")
        for (int attempt = 0; attempt < 13; attempt++) {
            int root = attempt == 0 ? bassPitchClass : attempt - 1;
            if (root < 0 || !pitchClasses[root] || (attempt > 0 && root == bassPitchClass)) continue;

            int intervals = 0;
            for (int pc = 0; pc < 12; pc++) {
                if (pitchClasses[pc]) intervals |= 1 << (((pc - root) % 12 + 12) % 12);
            }
            for (int[] template : TEMPLATES) {
                if (template[0] == intervals) {
                    String name = NoteUtils.NOTE_NAMES[root] + SUFFIXES[template[1]];
                    return root == bassPitchClass ? name : name + "/" + NoteUtils.NOTE_NAMES[bassPitchClass];
                }
            }
        }
        return null;
    }
}
