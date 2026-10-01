package com.example.guitartuner;

import static org.junit.Assert.assertEquals;

import com.example.guitartuner.models.PlayTiming;

import org.junit.Test;

public class PlayTimingTest {

    // 4 ноты: четверти при 120 BPM, затем половинная
    private final PlayTiming song = new PlayTiming(120,
            new double[]{0, 500, 1000, 1500},
            new double[]{500, 500, 500, 1000},
            new double[]{0, 500, 1000, 1500, 2000},
            new boolean[]{true, false, false, false, true});

    @Test
    public void lessonBeatsAreOneNotePerBeat() {
        PlayTiming t = PlayTiming.evenBeats(5, 60);
        assertEquals(5, t.size());
        assertEquals(4000, t.stepStart[4], 1e-9);
        assertEquals(1000, t.stepDuration[0], 1e-9);
        assertEquals(5000, t.endMs(), 1e-9);
        assertEquals(true, t.clickAccents[4]);
        assertEquals(false, t.clickAccents[3]);
    }

    @Test
    public void currentStepByTime() {
        assertEquals(-1, song.indexAt(-200, 0));
        assertEquals(0, song.indexAt(0, 0));
        assertEquals(0, song.indexAt(499, 0));
        assertEquals(1, song.indexAt(500, 0));
        assertEquals(3, song.indexAt(2400, 0));
        // нота включается заранее
        assertEquals(1, song.indexAt(400, 120));
        assertEquals(0, song.indexAt(370, 120));
    }

    @Test
    public void smoothPositionBetweenSteps() {
        assertEquals(0.5f, song.positionAt(250), 1e-6);
        assertEquals(2.0f, song.positionAt(1000), 1e-6);
        assertEquals(3.0f, song.positionAt(2000), 1e-6);
        // отсчёт: за одну долю (500 мс) до первой ноты
        assertEquals(-1.0f, song.positionAt(-500), 1e-6);
        assertEquals(-0.5f, song.positionAt(-250), 1e-6);
    }

    @Test
    public void endAndBeat() {
        assertEquals(2500, song.endMs(), 1e-9);
        assertEquals(500, song.beatMs(), 1e-9);
    }
}
