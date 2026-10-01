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

    private static final String[] RUSSIAN_NOTES = {
            "до", "до-диез", "ре", "ре-диез", "ми", "фа",
            "фа-диез", "соль", "соль-диез", "ля", "ля-диез", "си"
    };
    // суффикс названия -> расшифровка
    private static final String[][] QUALITIES = {
            {"", "мажор"}, {"m", "минор"}, {"5", "пауэр-аккорд (тоника и квинта)"},
            {"7", "доминантсептаккорд"}, {"m7", "минорный септаккорд"},
            {"maj7", "большой мажорный септаккорд"}, {"sus2", "с задержанием (sus2)"},
            {"sus4", "с задержанием (sus4)"}, {"dim", "уменьшённый"}, {"aug", "увеличенный"},
            {"6", "мажорный секстаккорд"}, {"m6", "минорный секстаккорд"},
            {"add9", "мажор с добавленной ноной"}, {"m7b5", "полууменьшённый септаккорд"},
            {"dim7", "уменьшённый септаккорд"}, {"7sus4", "септаккорд с задержанием"},
    };

    private ChordNamer() {
    }

    /** "Am" -> "ля минор", "G7" -> "соль, доминантсептаккорд", "C/E" -> "до мажор, бас ми". */
    public static String describe(String name) {
        if (name == null || name.isEmpty()) return "";
        String bass = null;
        int slash = name.indexOf('/');
        if (slash > 0) {
            bass = name.substring(slash + 1);
            name = name.substring(0, slash);
        }
        int rootLength = name.length() > 1 && (name.charAt(1) == '#' || name.charAt(1) == 'b') ? 2 : 1;
        int root = pitchClassOf(name.substring(0, rootLength));
        if (root < 0) return "";
        String suffix = name.substring(rootLength);

        String quality = null;
        for (String[] q : QUALITIES) {
            if (q[0].equals(suffix)) quality = q[1];
        }
        String text;
        if (quality == null) text = RUSSIAN_NOTES[root] + " " + suffix;
        else if (suffix.isEmpty() || suffix.equals("m")) text = RUSSIAN_NOTES[root] + " " + quality;
        else text = RUSSIAN_NOTES[root] + ", " + quality;

        if (bass != null) {
            int bassPc = pitchClassOf(bass);
            if (bassPc >= 0) text += ", бас " + RUSSIAN_NOTES[bassPc];
        }
        return text;
    }

    private static int pitchClassOf(String note) {
        String sharp = note.length() == 2 && note.charAt(1) == 'b'
                ? flatToSharp(note) : note;
        for (int i = 0; i < 12; i++) {
            if (NoteUtils.NOTE_NAMES[i].equals(sharp)) return i;
        }
        return -1;
    }

    private static String flatToSharp(String flat) {
        int natural = pitchClassOf(flat.substring(0, 1));
        return natural < 0 ? flat : NoteUtils.NOTE_NAMES[(natural + 11) % 12];
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
