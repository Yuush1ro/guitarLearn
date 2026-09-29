package com.example.guitartuner;

import static org.junit.Assert.assertEquals;

import com.example.guitartuner.models.ScaleType;
import com.example.guitartuner.tuner.NoteUtils;

import org.junit.Test;

public class ScaleTypeTest {

    private static int pc(String name) {
        for (int i = 0; i < NoteUtils.NOTE_NAMES.length; i++) {
            if (NoteUtils.NOTE_NAMES[i].equals(name)) return i;
        }
        throw new IllegalArgumentException(name);
    }

    private static String names(ScaleType type, String root) {
        StringBuilder sb = new StringBuilder();
        for (int p : type.pitchClasses(pc(root))) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(NoteUtils.NOTE_NAMES[p]);
        }
        return sb.toString();
    }

    @Test
    public void majorScales() {
        assertEquals("C D E F G A B", names(ScaleType.MAJOR, "C"));
        assertEquals("G A B C D E F#", names(ScaleType.MAJOR, "G"));
        assertEquals("E F# G# A B C# D#", names(ScaleType.MAJOR, "E"));
    }

    @Test
    public void minorScales() {
        assertEquals("A B C D E F G", names(ScaleType.MINOR, "A"));
        assertEquals("E F# G A B C D", names(ScaleType.MINOR, "E"));
    }

    @Test
    public void pentatonics() {
        assertEquals("A C D E G", names(ScaleType.MINOR_PENTATONIC, "A"));
        assertEquals("E G A B D", names(ScaleType.MINOR_PENTATONIC, "E"));
        assertEquals("C D E G A", names(ScaleType.MAJOR_PENTATONIC, "C"));
        assertEquals("G A B D E", names(ScaleType.MAJOR_PENTATONIC, "G"));
    }

    @Test
    public void chromaticHasAllTwelveNotes() {
        assertEquals("F F# G G# A A# B C C# D D# E", names(ScaleType.CHROMATIC, "F"));
    }
}
