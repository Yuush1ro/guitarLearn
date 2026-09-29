package com.example.guitartuner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.example.guitartuner.models.LessonLibrary;
import com.example.guitartuner.models.TabNote;
import com.example.guitartuner.utils.GuitarNoteUtils;

import org.junit.Test;

import java.util.List;

public class LessonLibraryTest {

    private static int midi(TabNote n) {
        return GuitarNoteUtils.getMidi(n.getStringNumber(), n.getFret());
    }

    @Test
    public void cascadeFirstPositionIsFullChromaticUpAndDown() {
        List<TabNote> cascade = LessonLibrary.chromaticCascade(0, 4);

        // вверх: E2 (40) .. G#4 (68) — 29 нот без пропусков, затем 28 обратно
        assertEquals(29 + 28, cascade.size());
        for (int i = 0; i < 29; i++) {
            assertEquals(40 + i, midi(cascade.get(i)));
        }
        for (int i = 29; i < cascade.size(); i++) {
            assertEquals(midi(cascade.get(i - 1)) - 1, midi(cascade.get(i)));
        }
    }

    @Test
    public void bNoteBetweenStrings3And2IsPlayedOnceOnString2() {
        List<TabNote> cascade = LessonLibrary.chromaticCascade(0, 4);
        for (TabNote n : cascade) {
            // 3-я струна 4-й лад (B3) заменён открытой 2-й струной
            assertTrue(!(n.getStringNumber() == 3 && n.getFret() == 4));
        }
    }

    @Test
    public void noSameNoteTwiceInARowForAnyRegion() {
        for (int start = 0; start <= 12; start++) {
            for (int end = start; end <= 12; end++) {
                List<TabNote> cascade = LessonLibrary.chromaticCascade(start, end);
                for (int i = 1; i < cascade.size(); i++) {
                    assertNotEquals("region " + start + "-" + end + " at " + i,
                            midi(cascade.get(i - 1)), midi(cascade.get(i)));
                }
                for (TabNote n : cascade) {
                    assertTrue(n.getFret() >= start && n.getFret() <= end);
                }
            }
        }
    }

    @Test
    public void regionOnlyUsesSelectedFrets() {
        List<TabNote> cascade = LessonLibrary.chromaticCascade(5, 8);
        assertEquals(6, cascade.get(0).getStringNumber());
        assertEquals(5, cascade.get(0).getFret());
        TabNote last = cascade.get(cascade.size() - 1);
        assertEquals(6, last.getStringNumber());
        assertEquals(5, last.getFret());
    }
}
