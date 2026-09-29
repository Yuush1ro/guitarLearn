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
 */
public final class ChordMatcher {

    public static final double MIN_PRESENCE = 0.15;
    public static final double MIN_FRACTION = 0.55;
    public static final double MAX_FOREIGN = 0.5;

    private ChordMatcher() {
    }

    public static boolean matches(double[] chroma, boolean[] expectedPitchClasses) {
        double total = 0;
        double inChord = 0;
        for (int pc = 0; pc < 12; pc++) {
            total += chroma[pc];
            if (expectedPitchClasses[pc]) {
                if (chroma[pc] < MIN_PRESENCE) return false;
                inChord += chroma[pc];
            } else if (chroma[pc] > MAX_FOREIGN) {
                return false;
            }
        }
        return total > 0 && inChord / total >= MIN_FRACTION;
    }
}
