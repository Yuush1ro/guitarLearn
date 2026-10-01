package com.example.guitartuner.practice;

import com.example.guitartuner.models.TabNote;
import com.example.guitartuner.tuner.AudioFrame;
import com.example.guitartuner.tuner.ChordMatcher;

import java.util.List;

/**
 * Режим "Свой темп": урок ждёт, пока текущая нота или аккорд сыграны верно.
 *
 * Чтобы успевать за ровной игрой музыканта:
 *  - нота засчитывается сразу, если YIN в ней уверен, иначе через 2 кадра (~46 мс);
 *  - после ноты нет паузы: ещё звучащая прежняя струна другой высоты не мешает;
 *    повтор той же ноты засчитывается только после новой атаки (щипка);
 *  - если короткая нота не распозналась, а музыкант уже играет следующую — пропущенная
 *    отмечается промахом, и урок идёт дальше, а не застревает.
 */
public class StepMatcher {

    public static final byte NONE = 0;
    public static final byte HIT = 1;
    public static final byte MISS = 2;

    // уверенность YIN, при которой нота засчитывается с первого кадра
    static final double STRONG_CONFIDENCE = 0.9;
    // иначе — столько кадров подряд
    static final int NOTE_FRAMES = 2;
    static final int CHORD_FRAMES = 2;
    // перескок через ноту — только по устойчивому совпадению, чтобы не перескакивать случайно
    static final int SKIP_FRAMES = 2;

    private final List<TabNote> steps;
    private final byte[] results;

    private int index = 0;
    private int currentFrames = 0;
    private int nextFrames = 0;
    // текущий шаг совпадает с только что сыгранным: нужна новая атака
    private boolean needsOnset = false;
    private boolean onsetSeen = false;

    /** results — общий с экраном массив результатов (размер = числу шагов). */
    public StepMatcher(List<TabNote> steps, byte[] results) {
        this.steps = steps;
        this.results = results;
    }

    public int index() {
        return index;
    }

    public boolean isComplete() {
        return index >= steps.size();
    }

    /** Продолжить с шага index (перемотка, старт). */
    public void seek(int index) {
        this.index = index;
        currentFrames = 0;
        nextFrames = 0;
        needsOnset = false;
        onsetSeen = false;
    }

    /** Ждём новой атаки для текущего шага (тот же шаг, что и предыдущий). */
    public boolean isWaitingForOnset() {
        return needsOnset && !onsetSeen;
    }

    /** Обрабатывает кадр. true — сменился шаг или результаты. */
    public boolean onFrame(AudioFrame frame) {
        if (isComplete()) return false;

        if (frame.silent) {
            currentFrames = 0;
            nextFrames = 0;
            // струны заглушены — следующий звук точно новый
            needsOnset = false;
            return false;
        }
        if (frame.onset) onsetSeen = true;

        TabNote current = steps.get(index);
        boolean gated = needsOnset && !onsetSeen;
        currentFrames = !gated && matches(current, frame) ? currentFrames + 1 : 0;

        // перескок через ноту — только после нового щипка: иначе ещё звучащая
        // прежняя нота (A в последовательности A-G-A) засчитала бы следующую
        TabNote next = index + 1 < steps.size() ? steps.get(index + 1) : null;
        boolean nextMatches = next != null && onsetSeen && !isSameStep(current, next)
                && matches(next, frame);
        nextFrames = nextMatches ? nextFrames + 1 : 0;

        if (accepted(current, frame, currentFrames)) {
            results[index] = HIT;
            advance(1, current);
            return true;
        }
        if (next != null && nextFrames >= Math.max(SKIP_FRAMES, next.isChord() ? CHORD_FRAMES : 0)) {
            // текущая не распозналась, музыкант уже играет следующую
            results[index] = MISS;
            results[index + 1] = HIT;
            advance(2, next);
            return true;
        }
        return false;
    }

    private void advance(int count, TabNote played) {
        index += count;
        currentFrames = 0;
        nextFrames = 0;
        onsetSeen = false;
        needsOnset = index < steps.size() && isSameStep(steps.get(index), played);
    }

    static boolean accepted(TabNote step, AudioFrame frame, int frames) {
        if (frames <= 0) return false;
        if (step.isChord()) return frames >= CHORD_FRAMES;
        return frames >= NOTE_FRAMES || frame.confidence >= STRONG_CONFIDENCE;
    }

    /** Звучит ли в кадре эта нота (точная высота) или этот аккорд (набор нот). */
    public static boolean matches(TabNote step, AudioFrame frame) {
        if (step.isChord()) {
            return frame.chroma != null && ChordMatcher.matches(frame.chroma, step.getPitchClasses());
        }
        return frame.midi() == step.getMidi();
    }

    /** Тот же аккорд (по набору нот) или та же нота (точная высота). */
    public static boolean isSameStep(TabNote a, TabNote b) {
        if (a.isChord() != b.isChord()) return false;
        return a.isChord() ? a.samePitchClasses(b) : a.getMidi() == b.getMidi();
    }
}
