package com.example.guitartuner.models;

import com.example.guitartuner.utils.GuitarNoteUtils;

import java.util.ArrayList;
import java.util.List;

/** Встроенные уроки. */
public final class LessonLibrary {

    public static final int DEFAULT_REGION_START = 0;
    public static final int DEFAULT_REGION_END = 4;

    private LessonLibrary() {
    }

    public static List<Lesson> all() {
        List<Lesson> lessons = new ArrayList<>();

        List<TabNote> openStrings = openStrings();
        lessons.add(new Lesson("Урок 1: Перебор всех струн",
                "Открытые струны с 6-й по 1-ю · " + openStrings.size() + " нот",
                openStrings, LessonDifficulty.EASY, false));

        lessons.add(new Lesson("Урок 2: Хроматическая гамма",
                "Каскад вверх и вниз по струнам · можно выбрать участок грифа",
                chromaticCascade(DEFAULT_REGION_START, DEFAULT_REGION_END),
                LessonDifficulty.MEDIUM, true));

        List<TabNote> cMajor = cMajor();
        lessons.add(new Lesson("Урок 3: Гамма До мажор",
                "Две октавы от C3 · " + cMajor.size() + " нот",
                cMajor, LessonDifficulty.MEDIUM, false));

        return lessons;
    }

    // открытые струны от басовой (6) к самой тонкой (1)
    private static List<TabNote> openStrings() {
        List<TabNote> notes = new ArrayList<>();
        for (int string = 6; string >= 1; string--) {
            notes.add(new TabNote(string, 0));
        }
        return notes;
    }

    /**
     * Хроматический каскад на участке грифа: на каждой струне лады start..end,
     * от 6-й струны к 1-й, затем обратно вниз (верхняя нота не повторяется).
     *
     * Если последняя нота струны совпадает по высоте с первой нотой следующей
     * (например, 3-я струна 4-й лад = открытая 2-я, обе B3), она играется один раз —
     * уже на следующей струне, как в классической аппликатуре. Иначе ещё звучащая
     * струна сразу засчитала бы следующую ноту.
     */
    public static List<TabNote> chromaticCascade(int startFret, int endFret) {
        int start = Math.min(startFret, endFret);
        int end = Math.max(startFret, endFret);

        List<TabNote> up = new ArrayList<>();
        for (int string = 6; string >= 1; string--) {
            for (int fret = start; fret <= end; fret++) {
                TabNote note = new TabNote(string, fret);
                if (!up.isEmpty() && midi(up.get(up.size() - 1)) == midi(note)) {
                    up.remove(up.size() - 1);
                }
                up.add(note);
            }
        }

        List<TabNote> cascade = new ArrayList<>(up);
        for (int i = up.size() - 2; i >= 0; i--) {
            cascade.add(up.get(i));
        }
        return cascade;
    }

    // гамма До мажор, две октавы, классическая аппликатура с 3 лада струны Ля
    private static List<TabNote> cMajor() {
        List<TabNote> notes = new ArrayList<>();
        notes.add(new TabNote(5, 3)); // C3
        notes.add(new TabNote(4, 0)); // D3
        notes.add(new TabNote(4, 2)); // E3
        notes.add(new TabNote(4, 3)); // F3
        notes.add(new TabNote(3, 0)); // G3
        notes.add(new TabNote(3, 2)); // A3
        notes.add(new TabNote(2, 0)); // B3
        notes.add(new TabNote(2, 1)); // C4
        notes.add(new TabNote(2, 3)); // D4
        notes.add(new TabNote(1, 0)); // E4
        notes.add(new TabNote(1, 1)); // F4
        notes.add(new TabNote(1, 3)); // G4
        notes.add(new TabNote(1, 5)); // A4
        notes.add(new TabNote(1, 7)); // B4
        notes.add(new TabNote(1, 8)); // C5
        return notes;
    }

    private static int midi(TabNote note) {
        return GuitarNoteUtils.getMidi(note.getStringNumber(), note.getFret());
    }
}
