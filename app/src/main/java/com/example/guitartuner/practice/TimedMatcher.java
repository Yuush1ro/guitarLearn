package com.example.guitartuner.practice;

import com.example.guitartuner.models.PlayTiming;
import com.example.guitartuner.models.TabNote;
import com.example.guitartuner.tuner.AudioFrame;

import java.util.List;

/**
 * Режим "Под метроном": сыгранный звук сопоставляется с нотами по времени удара.
 *
 * Распознавание запаздывает на несколько десятков мс, поэтому время звука оценивается
 * как "сейчас минус задержка". Нота засчитывается, если она прозвучала в окне
 * [начало − EARLY, начало + LATE] — так быстрые ноты не "переезжают" в соседний шаг.
 * Все допуски заданы в реальных миллисекундах и пересчитываются с учётом скорости.
 */
public class TimedMatcher {

    // можно сыграть чуть раньше времени
    static final double EARLY_MS = 150;
    // и позже — но не дольше LATE_MAX и не меньше LATE_MIN, в пределах длительности ноты
    static final double LATE_MIN_MS = 150;
    static final double LATE_MAX_MS = 350;
    // средняя задержка распознавания: окно 46 мс + шаг кадра 23 мс
    static final double LATENCY_MS = 45;

    private final List<TabNote> steps;
    private final PlayTiming timing;
    private final byte[] results;
    // когда (время песни) шаг был засчитан — чтобы повтор той же ноты требовал новой атаки
    private final double[] hitTime;

    private int firstOpen = 0;
    private int pendingStep = -1;
    private int pendingFrames = 0;
    private double lastOnsetTime = Double.NEGATIVE_INFINITY;
    private int hits = 0;
    private int misses = 0;

    public TimedMatcher(List<TabNote> steps, PlayTiming timing, byte[] results) {
        this.steps = steps;
        this.timing = timing;
        this.results = results;
        this.hitTime = new double[steps.size()];
    }

    /** Начать с шага startIndex; более ранние результаты не трогаем. */
    public void reset(int startIndex) {
        firstOpen = startIndex;
        pendingStep = -1;
        pendingFrames = 0;
        lastOnsetTime = Double.NEGATIVE_INFINITY;
        hits = 0;
        misses = 0;
    }

    public int hits() {
        return hits;
    }

    public int misses() {
        return misses;
    }

    /** Все шаги получили результат. */
    public boolean isFinished() {
        return firstOpen >= steps.size();
    }

    /**
     * Обрабатывает кадр, полученный в момент songNowMs (время песни).
     * true — какой-то шаг засчитан.
     */
    public boolean onFrame(AudioFrame frame, double songNowMs, double speed) {
        double soundTime = songNowMs - LATENCY_MS * speed;
        if (frame.onset) lastOnsetTime = soundTime;
        if (frame.silent) {
            pendingStep = -1;
            pendingFrames = 0;
            return false;
        }

        for (int i = firstOpen; i < steps.size(); i++) {
            double start = timing.stepStart[i];
            if (start - EARLY_MS * speed > soundTime) break;
            if (results[i] != StepMatcher.NONE) continue;
            if (soundTime > start + lateWindow(i, speed)) continue;

            TabNote step = steps.get(i);
            // повтор той же ноты: ещё звучащая струна не считается, нужен новый щипок
            if (i > 0 && results[i - 1] == StepMatcher.HIT
                    && StepMatcher.isSameStep(steps.get(i - 1), step)
                    && lastOnsetTime <= hitTime[i - 1]) {
                continue;
            }
            if (!StepMatcher.matches(step, frame)) continue;

            if (pendingStep == i) pendingFrames++;
            else {
                pendingStep = i;
                pendingFrames = 1;
            }
            if (StepMatcher.accepted(step, frame, pendingFrames)) {
                results[i] = StepMatcher.HIT;
                hitTime[i] = soundTime;
                hits++;
                pendingStep = -1;
                pendingFrames = 0;
                return true;
            }
            return false;
        }

        pendingStep = -1;
        pendingFrames = 0;
        return false;
    }

    /** Отмечает пропущенными шаги, окно которых уже закрылось. true — что-то изменилось. */
    public boolean onTick(double songNowMs, double speed) {
        boolean changed = false;
        while (firstOpen < steps.size()) {
            double closes = timing.stepStart[firstOpen] + lateWindow(firstOpen, speed)
                    + (LATENCY_MS + 30) * speed;
            if (songNowMs <= closes) break;
            if (results[firstOpen] == StepMatcher.NONE) {
                results[firstOpen] = StepMatcher.MISS;
                misses++;
                changed = true;
            }
            firstOpen++;
        }
        return changed;
    }

    // окно "позже" в мс песни
    private double lateWindow(int i, double speed) {
        double realDuration = timing.stepDuration[i] / speed;
        double lateReal = Math.max(LATE_MIN_MS, Math.min(LATE_MAX_MS, realDuration));
        return lateReal * speed;
    }
}
