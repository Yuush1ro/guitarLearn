package com.example.guitartuner.tuner;

/**
 * Проверяет по хромаграмме, звучит ли ожидаемый аккорд.
 *
 * Аккорд засчитывается, если
 *  - каждая его нота хорошо слышна (не слабее MIN_PRESENCE от самой громкой),
 *  - на ноты аккорда приходится основная часть энергии (не меньше MIN_FRACTION), и
 *  - ни одна нота вне аккорда не звучит громко (не сильнее MAX_FOREIGN) —
 *    иначе F засчитывался бы вместо Am: гармоники F-аккорда дают и A, и C, и E.
 * Октава не проверяется: хромаграмма её не различает.
 *
 * Ноты "разрешённого фона" (например, ещё звенящего предыдущего аккорда) не считаются
 * ни лишними, ни своими — они просто не учитываются.
 */
public final class ChordMatcher {

    public static final double MIN_PRESENCE = 0.15;
    public static final double MIN_FRACTION = 0.55;
    public static final double MAX_FOREIGN = 0.5;

    // "уверенное" совпадение: все ноты отчётливо, почти вся энергия на них, лишних почти нет —
    // такой аккорд можно засчитать с первого кадра
    public static final double STRONG_PRESENCE = 0.3;
    public static final double STRONG_FRACTION = 0.7;
    public static final double STRONG_FOREIGN = 0.35;

    // атака при смене аккорда: новые ноты должны заметно вырасти, посторонние — нет
    public static final double ONSET_NEW_PRESENCE = 0.35;
    public static final double ONSET_MAX_FOREIGN = 0.6;

    private static final boolean[] NOTHING = new boolean[12];

    private ChordMatcher() {
    }

    /** Уверенное совпадение — можно засчитывать без подтверждения вторым кадром. */
    public static boolean strongMatch(double[] chroma, boolean[] expectedPitchClasses) {
        return strongMatch(chroma, expectedPitchClasses, NOTHING);
    }

    public static boolean matches(double[] chroma, boolean[] expectedPitchClasses) {
        return matches(chroma, expectedPitchClasses, NOTHING);
    }

    /** То же, но ноты background (звенящий фон) не учитываются. */
    public static boolean strongMatch(double[] chroma, boolean[] expected, boolean[] background) {
        return check(chroma, expected, background, STRONG_PRESENCE, STRONG_FRACTION, STRONG_FOREIGN);
    }

    public static boolean matches(double[] chroma, boolean[] expected, boolean[] background) {
        return check(chroma, expected, background, MIN_PRESENCE, MIN_FRACTION, MAX_FOREIGN);
    }

    /**
     * Удар по новому аккорду поверх звенящего прежнего (background): по росту энергии
     * (onsetChroma) зазвучали все ноты нового аккорда, которых не было в прежнем,
     * и не зазвучало ничего постороннего. Рост нот только прежнего аккорда — тоже
     * постороннее: значит, ударили снова по прежнему. Если новых нот нет — обычная проверка.
     */
    public static boolean onsetMatches(double[] onsetChroma, boolean[] expected, boolean[] background) {
        boolean anyNew = false;
        for (int pc = 0; pc < 12; pc++) {
            boolean isNew = expected[pc] && !background[pc];
            if (isNew) {
                anyNew = true;
                if (onsetChroma[pc] < ONSET_NEW_PRESENCE) return false;
            } else if (!expected[pc] && onsetChroma[pc] > ONSET_MAX_FOREIGN) {
                return false;
            }
        }
        return anyNew ? true : matches(onsetChroma, expected);
    }

    private static boolean check(double[] chroma, boolean[] expected, boolean[] background,
                                 double minPresence, double minFraction, double maxForeign) {
        double total = 0;
        double inChord = 0;
        for (int pc = 0; pc < 12; pc++) {
            if (expected[pc]) {
                if (chroma[pc] < minPresence) return false;
                inChord += chroma[pc];
                total += chroma[pc];
            } else if (background[pc]) {
                // звенящий фон: не мешает и не помогает
            } else {
                if (chroma[pc] > maxForeign) return false;
                total += chroma[pc];
            }
        }
        return total > 0 && inChord / total >= minFraction;
    }
}
