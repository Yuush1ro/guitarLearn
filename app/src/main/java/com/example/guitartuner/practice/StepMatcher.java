package com.example.guitartuner.practice;

import com.example.guitartuner.models.TabNote;
import com.example.guitartuner.tuner.AudioFrame;
import com.example.guitartuner.tuner.ChordMatcher;

import java.util.List;

/**
 * Режим "Свой темп": урок ждёт, пока текущая нота или аккорд сыграны верно.
 *
 * Чтобы успевать за ровной игрой музыканта:
 *  - уверенное совпадение засчитывается с первого кадра (~23 мс), иначе — через 2 кадра;
 *  - новая нота опознаётся и по атаке (что именно зазвучало), даже поверх звенящего аккорда;
 *  - после ноты нет паузы; повтор той же ноты/аккорда засчитывается после нового удара;
 *  - если короткая нота не распозналась, а музыкант уже играет следующую — пропущенная
 *    отмечается промахом, и урок идёт дальше, а не застревает.
 */
public class StepMatcher {

    public static final byte NONE = 0;
    public static final byte HIT = 1;
    public static final byte MISS = 2;

    /** Насколько кадр похож на шаг. */
    public static final int NO_MATCH = 0;
    public static final int WEAK = 1;
    public static final int STRONG = 2;

    // уверенность YIN, при которой нота засчитывается с первого кадра
    static final double STRONG_CONFIDENCE = 0.9;
    // слабое совпадение — столько кадров подряд
    static final int CONFIRM_FRAMES = 2;
    // одна атака бывает видна в двух соседних кадрах — не считаем её дважды
    static final long ONSET_REFRACTORY_MS = 70;
    // нота, которая зазвучала в атаке, должна выделяться среди остальных; порог мягкий —
    // вместе с нотой растут и её обертоны (у C — G и E)
    static final double ONSET_DOMINANCE = 0.9;

    private final List<TabNote> steps;
    private final byte[] results;

    private int index = 0;
    // допускает один "мигнувший" кадр между совпадениями
    private final MatchCounter currentCounter = new MatchCounter();
    private int currentFrames = 0;
    private int nextFrames = 0;
    // текущий шаг совпадает с только что сыгранным: нужна новая атака
    private boolean needsOnset = false;
    private boolean onsetSeen = false;
    private long lastHitMs = Long.MIN_VALUE / 2;

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
        currentCounter.reset();
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
            currentCounter.reset();
            nextFrames = 0;
            // струны заглушены — следующий звук точно новый
            needsOnset = false;
            return false;
        }
        if (frame.onset && frame.streamMs - lastHitMs > ONSET_REFRACTORY_MS) onsetSeen = true;

        TabNote current = steps.get(index);
        // звенеть может только действительно сыгранный предыдущий шаг
        TabNote previous = index > 0 && results[index - 1] == HIT ? steps.get(index - 1) : null;
        boolean gated = needsOnset && !onsetSeen;
        int currentStrength = gated ? NO_MATCH
                : matchStrength(current, previous, frame, needsOnset && onsetSeen);
        currentFrames = currentCounter.onFrame(currentStrength > NO_MATCH);

        // перескок через ноту — только после нового щипка: иначе ещё звучащая
        // прежняя нота (A в последовательности A-G-A) засчитала бы следующую
        TabNote next = index + 1 < steps.size() ? steps.get(index + 1) : null;
        int nextStrength = next != null && onsetSeen && !isSameStep(current, next)
                ? matchStrength(next, null, frame, false) : NO_MATCH;
        nextFrames = nextStrength > NO_MATCH ? nextFrames + 1 : 0;

        if (accepted(currentStrength, currentFrames)) {
            results[index] = HIT;
            advance(1, current, frame);
            return true;
        }
        // перескок — только если текущий шаг сейчас совсем не звучит, а следующий подтверждён
        // двумя кадрами: иначе гармоники аккорда G, похожие на D, перескочили бы через G
        if (next != null && currentStrength == NO_MATCH && nextFrames >= CONFIRM_FRAMES) {
            // текущая не распозналась, музыкант уже играет следующую
            results[index] = MISS;
            results[index + 1] = HIT;
            advance(2, next, frame);
            return true;
        }
        return false;
    }

    private void advance(int count, TabNote played, AudioFrame frame) {
        index += count;
        currentFrames = 0;
        currentCounter.reset();
        nextFrames = 0;
        onsetSeen = false;
        lastHitMs = frame.streamMs;
        needsOnset = index < steps.size() && isSameStep(steps.get(index), played);
    }

    /** Засчитать: уверенное совпадение — сразу, слабое — подтверждённое вторым кадром. */
    public static boolean accepted(int strength, int frames) {
        if (strength == STRONG) return true;
        return strength == WEAK && frames >= CONFIRM_FRAMES;
    }

    /** Насколько кадр похож на шаг (без учёта предыдущего шага). */
    public static int matchStrength(TabNote step, AudioFrame frame) {
        return matchStrength(step, null, frame, false);
    }

    /**
     * Насколько кадр похож на шаг: нота — точная высота, аккорд — набор нот.
     *
     * @param previous      предыдущий шаг — его ноты ещё могут звенеть (не считаются лишними)
     * @param repeatAttack  шаг повторяет предыдущий и только что был новый удар: тот же
     *                      аккорд/нота уже звучит, проверенный шагом раньше, — хватает
     *                      и слабого совпадения
     */
    public static int matchStrength(TabNote step, TabNote previous, AudioFrame frame, boolean repeatAttack) {
        if (repeatAttack) {
            int base = matchStrength(step, null, frame, false);
            if (base > NO_MATCH) return STRONG;
            if (step.isChord() && frame.chroma != null && allPresent(frame.chroma, step.getPitchClasses())) {
                return STRONG;
            }
            return NO_MATCH;
        }

        if (step.isChord()) {
            boolean[] pitchClasses = step.getPitchClasses();
            boolean[] background = previous != null && !isSameStep(previous, step)
                    ? previous.getPitchClasses() : new boolean[12];
            int strength = NO_MATCH;
            if (frame.chroma != null) {
                if (ChordMatcher.strongMatch(frame.chroma, pitchClasses, background)) return STRONG;
                if (ChordMatcher.matches(frame.chroma, pitchClasses, background)) strength = WEAK;
            }
            // новый удар поверх звенящего прежнего аккорда: зазвучали именно новые ноты
            if (frame.onsetChroma != null
                    && ChordMatcher.onsetMatches(frame.onsetChroma, pitchClasses, background)) {
                return STRONG;
            }
            return strength;
        }

        int midi = frame.midi();
        if (midi == step.getMidi()) {
            return frame.confidence >= STRONG_CONFIDENCE ? STRONG : WEAK;
        }
        // та же нота в другой октаве — частая ошибка распознавания поверх звенящих струн:
        // засчитываем, но только подтверждённую вторым кадром
        if (midi > 0 && pitchClass(midi) == pitchClass(step.getMidi())) {
            return WEAK;
        }
        // поверх звенящих струн YIN может не выделить ноту — смотрим, что зазвучало в атаке
        if (frame.onsetChroma != null && dominantPitchClass(frame.onsetChroma) == pitchClass(step.getMidi())) {
            return STRONG;
        }
        return NO_MATCH;
    }

    public static boolean matches(TabNote step, AudioFrame frame) {
        return matchStrength(step, frame) > NO_MATCH;
    }

    // самая заметная нота атаки, если она явно выделяется; иначе -1
    private static int dominantPitchClass(double[] onsetChroma) {
        int best = -1;
        double second = 0;
        for (int pc = 0; pc < 12; pc++) {
            if (best < 0 || onsetChroma[pc] > onsetChroma[best]) {
                if (best >= 0) second = Math.max(second, onsetChroma[best]);
                best = pc;
            } else {
                second = Math.max(second, onsetChroma[pc]);
            }
        }
        return best >= 0 && second <= ONSET_DOMINANCE * onsetChroma[best] ? best : -1;
    }

    // все ноты аккорда хоть сколько-то слышны
    private static boolean allPresent(double[] chroma, boolean[] pitchClasses) {
        for (int pc = 0; pc < 12; pc++) {
            if (pitchClasses[pc] && chroma[pc] < ChordMatcher.MIN_PRESENCE) return false;
        }
        return true;
    }

    private static int pitchClass(int midi) {
        return ((midi % 12) + 12) % 12;
    }

    /** Тот же аккорд (по набору нот) или та же нота (точная высота). */
    public static boolean isSameStep(TabNote a, TabNote b) {
        if (a.isChord() != b.isChord()) return false;
        return a.isChord() ? a.samePitchClasses(b) : a.getMidi() == b.getMidi();
    }
}
