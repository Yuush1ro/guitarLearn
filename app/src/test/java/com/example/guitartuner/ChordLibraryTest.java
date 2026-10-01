package com.example.guitartuner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.guitartuner.models.ChordLibrary;
import com.example.guitartuner.models.ChordShape;
import com.example.guitartuner.models.TabNote;
import com.example.guitartuner.tuner.ChordNamer;

import org.junit.Test;

import java.util.List;

public class ChordLibraryTest {

    /** Название по нотам аппликатуры (как в конструкторе). */
    private static String nameFromNotes(ChordShape shape) {
        TabNote step = shape.toStep();
        List<TabNote> notes = step.getNotes();
        int bass = notes.get(notes.size() - 1).getMidi() % 12;
        return ChordNamer.name(step.getPitchClasses(), bass);
    }

    @Test
    public void everyLibraryChordIsNamedCorrectlyByItsNotes() {
        // проверяет сразу и аппликатуры библиотеки, и распознавание названий
        for (ChordShape shape : ChordLibrary.all()) {
            assertEquals("аппликатура " + shape.name, shape.name, nameFromNotes(shape));
        }
    }

    @Test
    public void libraryHasStandardChords() {
        assertTrue(ChordLibrary.all().size() >= 30);
        for (String name : new String[]{"C", "D", "E", "G", "A", "Am", "Em", "Dm", "E7", "F"}) {
            assertTrue(name, ChordLibrary.find(name) != null);
        }
    }

    @Test
    public void openAndMutedStrings() {
        ChordShape c = ChordLibrary.find("C");
        int[] frets = c.frets();
        assertEquals(ChordShape.MUTED, frets[0]); // 6-я струна не звучит
        assertEquals(3, frets[1]);                // 5-я струна, 3-й лад — C3
        assertEquals(0, frets[5]);                // 1-я открытая
        assertEquals(5, c.toStep().getNotes().size());
        assertEquals(48, c.toStep().getNotes().get(4).getMidi()); // самая низкая нота — C3
    }

    @Test
    public void builderShapes() {
        // одна струна — одиночная нота, без звука — нет шага
        ChordShape single = new ChordShape("?", "x3xxxx", null);
        assertTrue(!single.toStep().isChord());
        assertNull(new ChordShape("?", "xxxxxx", null).toStep());
        // нестандартная аппликатура тоже получает название
        assertEquals("C/E", nameFromNotes(new ChordShape("?", "032010", null)));
    }

    @Test
    public void russianDescriptions() {
        assertEquals("ля минор", ChordNamer.describe("Am"));
        assertEquals("до мажор", ChordNamer.describe("C"));
        assertEquals("соль, доминантсептаккорд", ChordNamer.describe("G7"));
        assertEquals("фа-диез минор", ChordNamer.describe("F#m"));
        assertEquals("до мажор, бас ми", ChordNamer.describe("C/E"));
        assertEquals("ми, пауэр-аккорд (тоника и квинта)", ChordNamer.describe("E5"));
    }
}
