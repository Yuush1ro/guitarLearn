package com.example.guitartuner.songs;

import com.example.guitartuner.models.PlayTiming;
import com.example.guitartuner.models.TabNote;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Результат разбора файла Guitar Pro: название и дорожки с нотами и аккордами. */
public class ParsedSong {

    public static final int SUPPORTED_STRING_COUNT = 6;

    public final String title;
    public final String artist;
    public final List<Track> tracks;

    public static class Track {
        public final String name;
        public final boolean percussion;
        // строй от 1-й (тонкой) струны к 6-й, MIDI
        public final int[] tuning;
        public final int capo;
        // шаги: одиночные ноты и аккорды (TabNote.isChord())
        public final List<TabNote> notes;
        // номер такта (с 0) для каждого шага
        public final int[] bars;
        // время шагов и метроном; null — в файле не было данных о времени
        public final PlayTiming timing;

        Track(String name, boolean percussion, int[] tuning, int capo, List<TabNote> notes,
              int[] bars, PlayTiming timing) {
            this.name = name;
            this.percussion = percussion;
            this.tuning = tuning;
            this.capo = capo;
            this.notes = notes;
            this.bars = bars;
            this.timing = timing;
        }

        /** Уроки рассчитаны на 6-струнную гитару. */
        public boolean isPlayable() {
            return !percussion && tuning.length == SUPPORTED_STRING_COUNT && !notes.isEmpty();
        }

        public int chordCount() {
            int count = 0;
            for (TabNote step : notes) if (step.isChord()) count++;
            return count;
        }
    }

    private ParsedSong(String title, String artist, List<Track> tracks) {
        this.title = title;
        this.artist = artist;
        this.tracks = tracks;
    }

    public List<Track> playableTracks() {
        List<Track> result = new ArrayList<>();
        for (Track track : tracks) {
            if (track.isPlayable()) result.add(track);
        }
        return result;
    }

    /** Разбирает JSON из assets/alphatab/parser.html. */
    static ParsedSong fromJson(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        List<Track> tracks = new ArrayList<>();

        // метроном общий для всех дорожек
        double tempo = root.optDouble("tempo", 120);
        JSONArray jsonClicks = root.optJSONArray("clicks");
        double[] clickTimes = new double[jsonClicks == null ? 0 : jsonClicks.length()];
        boolean[] clickAccents = new boolean[clickTimes.length];
        for (int i = 0; i < clickTimes.length; i++) {
            JSONArray click = jsonClicks.getJSONArray(i);
            clickTimes[i] = click.getDouble(0);
            clickAccents[i] = click.getInt(1) == 1;
        }

        JSONArray jsonTracks = root.getJSONArray("tracks");
        for (int t = 0; t < jsonTracks.length(); t++) {
            JSONObject jt = jsonTracks.getJSONObject(t);

            // AlphaTab отдаёт строй от верхней струны к нижней — как у нас
            JSONArray jsonTuning = jt.getJSONArray("tuning");
            int stringCount = jsonTuning.length();
            int[] tuning = new int[stringCount];
            for (int i = 0; i < stringCount; i++) tuning[i] = jsonTuning.getInt(i);

            List<TabNote> steps = new ArrayList<>();
            JSONArray jsonSteps = jt.getJSONArray("steps");
            int[] bars = new int[jsonSteps.length()];
            double[] starts = new double[jsonSteps.length()];
            double[] durations = new double[jsonSteps.length()];
            boolean hasTiming = jsonClicks != null;
            for (int i = 0; i < jsonSteps.length(); i++) {
                JSONObject step = jsonSteps.getJSONObject(i);
                bars[i] = step.optInt("b", 0);
                if (step.has("t")) {
                    starts[i] = step.getDouble("t");
                    durations[i] = step.getDouble("d");
                } else {
                    hasTiming = false;
                }
                JSONArray jsonNotes = step.getJSONArray("n");

                List<TabNote> notes = new ArrayList<>();
                for (int j = 0; j < jsonNotes.length(); j++) {
                    JSONArray n = jsonNotes.getJSONArray(j);
                    // в AlphaTab струна 1 — самая толстая, у нас 1 — самая тонкая
                    int string = stringCount - n.getInt(0) + 1;
                    notes.add(new TabNote(string, n.getInt(1), n.getInt(2)));
                }
                steps.add(TabNote.chord(notes, step.optString("c", "")));
            }

            tracks.add(new Track(
                    jt.optString("name", "Дорожка " + (t + 1)),
                    jt.optBoolean("percussion", false),
                    tuning,
                    jt.optInt("capo", 0),
                    steps,
                    bars,
                    hasTiming ? new PlayTiming(tempo, starts, durations, clickTimes, clickAccents) : null));
        }

        return new ParsedSong(root.optString("title", ""), root.optString("artist", ""), tracks);
    }
}
