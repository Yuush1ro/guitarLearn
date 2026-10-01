package com.example.guitartuner.practice;

import com.example.guitartuner.models.PlayTiming;
import com.example.guitartuner.models.TabNote;
import com.example.guitartuner.tuner.AudioFrame;

import java.util.Arrays;
import java.util.List;

/**
 * Режим "Под метроном": сыгранный звук сопоставляется с нотами по времени удара.
 *
 * Звук попадает в приложение с задержкой: микрофон телефона (30–150 мс, зависит от модели)
 * плюс анализ. Задержка подстраивается сама — по тому, насколько засчитанные ноты
 * в среднем отстают от своего времени. Нота засчитывается, если прозвучала в окне
 * [начало − EARLY, начало + LATE]. Допуски заданы в реальных миллисекундах
 * и пересчитываются с учётом скорости.
 */
public class TimedMatcher {

    // можно сыграть чуть раньше времени
    static final double EARLY_MS = 150;
    // и позже — но не дольше LATE_MAX и не меньше LATE_MIN, в пределах длительности ноты
    static final double LATE_MIN_MS = 180;
    static final double LATE_MAX_MS = 350;

    // задержка "звук → кадр": начальная оценка и пределы подстройки
    static final double INITIAL_LATENCY_MS = 60;
    static final double MIN_LATENCY_MS = 20;
    static final double MAX_LATENCY_MS = 250;
    private static final int LATENCY_SAMPLES = 15;
    private static final int LATENCY_MIN_SAMPLES = 3;

    private final List<TabNote> steps;
    private final PlayTiming timing;
    private final byte[] results;
    // когда (время песни) шаг был засчитан — чтобы повтор той же ноты требовал новой атаки
    private final double[] hitTime;

    private int firstOpen = 0;
    private int pendingStep = -1;
    private int pendingFrames = 0;
    // допускает один "мигнувший" кадр между совпадениями
    private final MatchCounter pendingCounter = new MatchCounter();
    private double lastOnsetTime = Double.NEGATIVE_INFINITY;
    private int hits = 0;
    private int misses = 0;

    private double latencyMs = INITIAL_LATENCY_MS;
    private final double[] lags = new double[LATENCY_SAMPLES];
    private int lagCount = 0;

    public TimedMatcher(List<TabNote> steps, PlayTiming timing, byte[] results) {
        this.steps = steps;
        this.timing = timing;
        this.results = results;
        this.hitTime = new double[steps.size()];
    }

    /** Начать с шага startIndex; более ранние результаты не трогаем. Задержка запоминается. */
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

    /** Текущая оценка задержки звука, мс. */
    public double latencyMs() {
        return latencyMs;
    }

    /** Начальная оценка задержки (например, запомненная с прошлого раза). */
    public void setLatencyMs(double value) {
        latencyMs = Math.max(MIN_LATENCY_MS, Math.min(MAX_LATENCY_MS, value));
    }

    /** Все шаги получили результат. */
    public boolean isFinished() {
        return firstOpen >= steps.size();
    }

    /**
     * Обрабатывает кадр; songAtCaptureMs — время песни в момент записи кадра.
     * true — какой-то шаг засчитан.
     */
    public boolean onFrame(AudioFrame frame, double songAtCaptureMs, double speed) {
        double soundTime = songAtCaptureMs - latencyMs * speed;
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
            TabNote previous = i > 0 ? steps.get(i - 1) : null;
            boolean repeatOfHit = previous != null && results[i - 1] == StepMatcher.HIT
                    && StepMatcher.isSameStep(previous, step);
            // повтор той же ноты: ещё звучащая струна не считается, нужен новый удар
            if (repeatOfHit && lastOnsetTime <= hitTime[i - 1] + StepMatcher.ONSET_REFRACTORY_MS * speed) {
                continue;
            }
            // звенеть может только действительно сыгранный предыдущий шаг
            TabNote ringing = previous != null && results[i - 1] == StepMatcher.HIT ? previous : null;
            int strength = StepMatcher.matchStrength(step, ringing, frame, repeatOfHit);
            if (strength == StepMatcher.NO_MATCH) continue;

            if (pendingStep != i) {
                pendingStep = i;
                pendingCounter.reset();
            }
            pendingFrames = pendingCounter.onFrame(true);
            if (StepMatcher.accepted(strength, pendingFrames)) {
                results[i] = StepMatcher.HIT;
                hitTime[i] = soundTime;
                hits++;
                pendingStep = -1;
                pendingFrames = 0;
                pendingCounter.reset();
                learnLatency((songAtCaptureMs - start) / speed);
                return true;
            }
            return false;
        }

        // в этом кадре ничего не совпало — серия терпит один такой кадр
        pendingFrames = pendingCounter.onFrame(false);
        if (pendingFrames == 0) pendingStep = -1;
        return false;
    }

    /** Отмечает пропущенными шаги, окно которых уже закрылось. true — что-то изменилось. */
    public boolean onTick(double songNowMs, double speed) {
        boolean changed = false;
        while (firstOpen < steps.size()) {
            double closes = timing.stepStart[firstOpen] + lateWindow(firstOpen, speed)
                    + (latencyMs + 30) * speed;
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

    // задержка = медиана отставания засчитанных нот (разброс самой игры медиана сглаживает)
    private void learnLatency(double lagRealMs) {
        lags[lagCount % LATENCY_SAMPLES] = lagRealMs;
        lagCount++;
        int n = Math.min(lagCount, LATENCY_SAMPLES);
        if (n < LATENCY_MIN_SAMPLES) return;
        double[] sorted = Arrays.copyOf(lags, n);
        Arrays.sort(sorted);
        latencyMs = Math.max(MIN_LATENCY_MS, Math.min(MAX_LATENCY_MS, sorted[n / 2]));
    }

    // окно "позже" в мс песни
    private double lateWindow(int i, double speed) {
        double realDuration = timing.stepDuration[i] / speed;
        double lateReal = Math.max(LATE_MIN_MS, Math.min(LATE_MAX_MS, realDuration));
        return lateReal * speed;
    }
}
