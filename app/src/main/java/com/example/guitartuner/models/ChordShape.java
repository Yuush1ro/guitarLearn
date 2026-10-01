package com.example.guitartuner.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Аппликатура аккорда на 6-струнной гитаре в стандартном строе.
 *
 * Лады и пальцы записаны как в песенниках — от 6-й (толстой) струны к 1-й:
 * "x32010" — до мажор. 'x' — струна не звучит, '0' — открытая струна.
 */
public final class ChordShape {

    public static final int MUTED = -1;

    public final String name;
    // лады от 6-й струны к 1-й; MUTED — не звучит, 0 — открытая
    private final int[] frets;
    // пальцы (1 — указательный … 4 — мизинец), 0 — не указан
    private final int[] fingers;

    public ChordShape(String name, String frets, String fingers) {
        this.name = name;
        this.frets = parse(frets, MUTED);
        this.fingers = fingers == null ? new int[6] : parse(fingers, 0);
    }

    public ChordShape(String name, int[] frets, int[] fingers) {
        this.name = name;
        this.frets = frets.clone();
        this.fingers = fingers == null ? new int[6] : fingers.clone();
    }

    private static int[] parse(String text, int mutedValue) {
        int[] result = new int[6];
        for (int i = 0; i < 6; i++) {
            char c = text.charAt(i);
            result[i] = c == 'x' || c == 'X' ? mutedValue : c - '0';
        }
        return result;
    }

    /** Лады от 6-й струны к 1-й (копия). */
    public int[] frets() {
        return frets.clone();
    }

    public int[] fingers() {
        return fingers.clone();
    }

    /** Есть ли хоть одна звучащая струна. */
    public boolean hasNotes() {
        for (int f : frets) if (f != MUTED) return true;
        return false;
    }

    /** Самый низкий прижатый лад (для диаграммы), 0 — только открытые. */
    public int lowestFret() {
        int min = Integer.MAX_VALUE;
        for (int f : frets) if (f > 0) min = Math.min(min, f);
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    public int highestFret() {
        int max = 0;
        for (int f : frets) max = Math.max(max, f);
        return max;
    }

    /** Шаг урока: все звучащие струны вместе (одна струна — одиночная нота). null — ничего не звучит. */
    public TabNote toStep() {
        List<TabNote> notes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            if (frets[i] == MUTED) continue;
            int stringNumber = 6 - i; // индекс 0 — 6-я струна
            notes.add(new TabNote(stringNumber, frets[i]));
        }
        if (notes.isEmpty()) return null;
        return TabNote.chord(notes, name);
    }
}
