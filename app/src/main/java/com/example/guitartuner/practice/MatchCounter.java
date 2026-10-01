package com.example.guitartuner.practice;

/**
 * Сколько кадров подряд звук совпадает с шагом — с допуском в один "мигнувший" кадр:
 * совпадение, промах, совпадение — это всё ещё 2 совпадения. Хромаграмма аккорда
 * от кадра к кадру немного мерцает (особенно тихая нота на одной струне, как F в G7).
 */
public final class MatchCounter {

    private int frames = 0;
    private int gap = 0;

    /** Учесть кадр; возвращает число совпадений в текущей серии. */
    public int onFrame(boolean matched) {
        if (matched) {
            if (gap > 1) frames = 0;
            frames++;
            gap = 0;
        } else {
            gap++;
            if (gap > 1) frames = 0;
        }
        return frames;
    }

    public void reset() {
        frames = 0;
        gap = 0;
    }
}
