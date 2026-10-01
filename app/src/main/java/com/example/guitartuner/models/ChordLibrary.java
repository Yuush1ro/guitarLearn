package com.example.guitartuner.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Стандартные аккорды для начинающих — в группах, как в песенниках. */
public final class ChordLibrary {

    public static final class Group {
        public final String title;
        public final List<ChordShape> chords;

        Group(String title, ChordShape... chords) {
            this.title = title;
            this.chords = Arrays.asList(chords);
        }
    }

    private static final List<Group> GROUPS = Arrays.asList(
            new Group("Мажорные",
                    new ChordShape("C", "x32010", "032010"),
                    new ChordShape("D", "xx0232", "000132"),
                    new ChordShape("E", "022100", "023100"),
                    new ChordShape("F", "133211", "134211"),
                    new ChordShape("G", "320003", "210003"),
                    new ChordShape("A", "x02220", "001230"),
                    new ChordShape("B", "x24442", "013331")),
            new Group("Минорные",
                    new ChordShape("Am", "x02210", "002310"),
                    new ChordShape("Bm", "x24432", "013421"),
                    new ChordShape("Cm", "x35543", "013421"),
                    new ChordShape("Dm", "xx0231", "000231"),
                    new ChordShape("Em", "022000", "023000"),
                    new ChordShape("Fm", "133111", "134111"),
                    new ChordShape("Gm", "355333", "134111")),
            new Group("Септаккорды",
                    new ChordShape("A7", "x02020", "002030"),
                    new ChordShape("B7", "x21202", "021304"),
                    new ChordShape("C7", "x32310", "032410"),
                    new ChordShape("D7", "xx0212", "000213"),
                    new ChordShape("E7", "020100", "020100"),
                    new ChordShape("G7", "320001", "320001"),
                    new ChordShape("Am7", "x02010", "002010"),
                    new ChordShape("Dm7", "xx0211", "000211"),
                    new ChordShape("Em7", "020000", "020000"),
                    new ChordShape("Cmaj7", "x32000", "032000"),
                    new ChordShape("Fmaj7", "xx3210", "003210"),
                    new ChordShape("Gmaj7", "320002", "320001"),
                    new ChordShape("Amaj7", "x02120", "002130"),
                    new ChordShape("Dmaj7", "xx0222", "000111")),
            new Group("Sus и add",
                    new ChordShape("Asus2", "x02200", "001200"),
                    new ChordShape("Asus4", "x02230", "001230"),
                    new ChordShape("Dsus2", "xx0230", "000130"),
                    new ChordShape("Dsus4", "xx0233", "000134"),
                    new ChordShape("Esus4", "022200", "023400"),
                    new ChordShape("Cadd9", "x32030", "021030")),
            new Group("Пауэр-аккорды",
                    new ChordShape("E5", "022xxx", "013000"),
                    new ChordShape("A5", "x022xx", "001300"),
                    new ChordShape("D5", "xx023x", "000130"),
                    new ChordShape("G5", "355xxx", "134000"))
    );

    private ChordLibrary() {
    }

    public static List<Group> groups() {
        return GROUPS;
    }

    public static List<ChordShape> all() {
        List<ChordShape> result = new ArrayList<>();
        for (Group g : GROUPS) result.addAll(g.chords);
        return result;
    }

    public static ChordShape find(String name) {
        for (Group g : GROUPS) {
            for (ChordShape c : g.chords) if (c.name.equals(name)) return c;
        }
        return null;
    }
}
