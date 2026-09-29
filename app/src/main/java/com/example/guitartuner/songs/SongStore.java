package com.example.guitartuner.songs;

import android.content.Context;

import com.example.guitartuner.models.TabNote;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Файлы песен в памяти приложения:
 *   songs/&lt;имя&gt;               — исходный файл Guitar Pro;
 *   songs_converted/&lt;имя&gt;.json — выбранная дорожка в формате урока.
 */
public final class SongStore {

    // версия 2: шаги могут быть аккордами; версия 3: у шагов есть номер такта.
    // Версию 1 (без аккордов) конвертируем заново, версия 2 читается, но без тактов.
    private static final int FORMAT_VERSION = 3;
    private static final int MIN_SUPPORTED_VERSION = 2;

    /** Сконвертированная песня: одна дорожка как последовательность нот и аккордов. */
    public static class ConvertedSong {
        public final String title;
        public final String trackName;
        // строй от 1-й (тонкой) струны к 6-й, MIDI
        public final int[] tuning;
        public final List<TabNote> notes;
        // номер такта (с 0) для каждого шага; null — неизвестно (старый формат)
        public final int[] bars;

        public ConvertedSong(String title, String trackName, int[] tuning, List<TabNote> notes, int[] bars) {
            this.title = title;
            this.trackName = trackName;
            this.tuning = tuning;
            this.notes = notes;
            this.bars = bars;
        }

        public int chordCount() {
            int count = 0;
            for (TabNote step : notes) if (step.isChord()) count++;
            return count;
        }
    }

    private SongStore() {
    }

    public static File sourcesDir(Context context) {
        File dir = new File(context.getFilesDir(), "songs");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File convertedDir(Context context) {
        File dir = new File(context.getFilesDir(), "songs_converted");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File convertedFile(Context context, File source) {
        return new File(convertedDir(context), source.getName() + ".json");
    }

    public static List<File> listSources(Context context) {
        List<File> result = new ArrayList<>();
        File[] files = sourcesDir(context).listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) result.add(f);
            }
        }
        result.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return result;
    }

    public static void save(File file, ConvertedSong song) throws IOException {
        try {
            JSONArray tuning = new JSONArray();
            for (int t : song.tuning) tuning.put(t);

            JSONArray steps = new JSONArray();
            for (int s = 0; s < song.notes.size(); s++) {
                TabNote step = song.notes.get(s);
                JSONArray notes = new JSONArray();
                for (TabNote n : step.getNotes()) {
                    notes.put(new JSONArray()
                            .put(n.getStringNumber())
                            .put(n.getFret())
                            .put(n.getMidi()));
                }
                JSONObject jsonStep = new JSONObject().put("n", notes);
                String chordName = step.isChord() ? step.getChordName() : null;
                if (chordName != null) jsonStep.put("c", chordName);
                if (song.bars != null) jsonStep.put("b", song.bars[s]);
                steps.put(jsonStep);
            }

            JSONObject root = new JSONObject()
                    .put("version", FORMAT_VERSION)
                    .put("title", song.title)
                    .put("track", song.trackName)
                    .put("tuning", tuning)
                    .put("steps", steps);

            try (OutputStream out = new FileOutputStream(file)) {
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    /** null, если файла нет, он повреждён или сохранён старой версией (без аккордов). */
    public static ConvertedSong load(File file) {
        if (!file.exists()) return null;
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) bytes.write(buffer, 0, read);

            JSONObject root = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            int version = root.optInt("version", 1);
            if (version < MIN_SUPPORTED_VERSION || version > FORMAT_VERSION) return null;

            JSONArray jsonTuning = root.getJSONArray("tuning");
            int[] tuning = new int[jsonTuning.length()];
            for (int i = 0; i < tuning.length; i++) tuning[i] = jsonTuning.getInt(i);

            JSONArray jsonSteps = root.getJSONArray("steps");
            List<TabNote> steps = new ArrayList<>(jsonSteps.length());
            int[] bars = new int[jsonSteps.length()];
            boolean hasBars = true;
            for (int i = 0; i < jsonSteps.length(); i++) {
                JSONObject jsonStep = jsonSteps.getJSONObject(i);
                if (jsonStep.has("b")) bars[i] = jsonStep.getInt("b");
                else hasBars = false;
                JSONArray jsonNotes = jsonStep.getJSONArray("n");
                List<TabNote> notes = new ArrayList<>(jsonNotes.length());
                for (int j = 0; j < jsonNotes.length(); j++) {
                    JSONArray n = jsonNotes.getJSONArray(j);
                    notes.add(new TabNote(n.getInt(0), n.getInt(1), n.getInt(2)));
                }
                steps.add(TabNote.chord(notes, jsonStep.optString("c", "")));
            }

            return new ConvertedSong(root.optString("title"), root.optString("track"), tuning, steps,
                    hasBars ? bars : null);
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    public static void delete(Context context, File source) {
        //noinspection ResultOfMethodCallIgnored
        convertedFile(context, source).delete();
        //noinspection ResultOfMethodCallIgnored
        source.delete();
    }
}
