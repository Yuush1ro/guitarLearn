package com.example.guitartuner.models;

import com.example.guitartuner.tuner.ChordNamer;
import com.example.guitartuner.utils.GuitarNoteUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Шаг урока: одна нота или аккорд.
 *
 * У аккорда сам объект описывает верхнюю ноту (для совместимости с кодом,
 * который работает с одиночными нотами), а getNotes() возвращает все ноты аккорда.
 */
public class TabNote {

    private final int stringNumber; // 1 = самая тонкая (высокая E), 6 = самая толстая (низкая E)
    private final int fret;
    // точная высота ноты (MIDI); для песен учитывает их строй и каподастр
    private final int midi;

    // для аккорда: все ноты от высокой к низкой; null — одиночная нота
    private final List<TabNote> chordNotes;
    // название аккорда из файла (может быть пустым)
    private final String chordName;

    /** Нота в стандартном строе (E A D G B E). */
    public TabNote(int stringNumber, int fret) {
        this(stringNumber, fret, GuitarNoteUtils.getMidi(stringNumber, fret));
    }

    public TabNote(int stringNumber, int fret, int midi) {
        this(stringNumber, fret, midi, null, "");
    }

    private TabNote(int stringNumber, int fret, int midi, List<TabNote> chordNotes, String chordName) {
        this.stringNumber = stringNumber;
        this.fret = fret;
        this.midi = midi;
        this.chordNotes = chordNotes;
        this.chordName = chordName == null ? "" : chordName;
    }

    /**
     * Шаг из нескольких нот, сыгранных вместе. Для одной ноты вернёт её саму.
     * @param name название аккорда из файла или пустая строка
     */
    public static TabNote chord(List<TabNote> notes, String name) {
        List<TabNote> sorted = new ArrayList<>(notes);
        Collections.sort(sorted, (a, b) -> Integer.compare(b.midi, a.midi));
        TabNote top = sorted.get(0);
        if (sorted.size() == 1) return top;
        return new TabNote(top.stringNumber, top.fret, top.midi,
                Collections.unmodifiableList(sorted), name);
    }

    public int getStringNumber() {
        return stringNumber;
    }

    public int getFret() {
        return fret;
    }

    public int getMidi() {
        return midi;
    }

    public boolean isChord() {
        return chordNotes != null;
    }

    /** Все ноты шага: для аккорда — от высокой к низкой, для ноты — она сама. */
    public List<TabNote> getNotes() {
        return chordNotes != null ? chordNotes : Collections.singletonList(this);
    }

    /** Какие ноты (без октавы, 0 = C) звучат в этом шаге. */
    public boolean[] getPitchClasses() {
        boolean[] result = new boolean[12];
        for (TabNote n : getNotes()) result[((n.midi % 12) + 12) % 12] = true;
        return result;
    }

    /** Совпадает ли набор нот (без учёта октав) с другим шагом. */
    public boolean samePitchClasses(TabNote other) {
        return java.util.Arrays.equals(getPitchClasses(), other.getPitchClasses());
    }

    /** Название аккорда из файла, либо определённое по нотам; null — не удалось назвать. */
    public String getChordName() {
        if (!isChord()) return null;
        if (!chordName.isEmpty()) return chordName;
        int bass = chordNotes.get(chordNotes.size() - 1).midi;
        return ChordNamer.name(getPitchClasses(), ((bass % 12) + 12) % 12);
    }

    /** Имя с октавой, например "E2" или "C#4" (для аккорда — верхняя нота). */
    public String getNoteName() {
        return GuitarNoteUtils.midiToName(midi);
    }

    /** Что показать крупно: название аккорда (или его ноты) либо имя ноты. */
    public String getDisplayName() {
        if (!isChord()) return getNoteName();
        String name = getChordName();
        return name != null ? name : notesListing();
    }

    /** Ноты аккорда от низкой к высокой, например "E2 B2 E3". */
    public String notesListing() {
        StringBuilder sb = new StringBuilder();
        List<TabNote> notes = getNotes();
        for (int i = notes.size() - 1; i >= 0; i--) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(notes.get(i).getNoteName());
        }
        return sb.toString();
    }
}
