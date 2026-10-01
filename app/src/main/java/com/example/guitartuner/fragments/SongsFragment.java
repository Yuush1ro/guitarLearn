package com.example.guitartuner.fragments;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.guitartuner.R;
import com.example.guitartuner.adapters.SongsAdapter;
import com.example.guitartuner.models.Song;
import com.example.guitartuner.songs.ParsedSong;
import com.example.guitartuner.songs.SongConverter;
import com.example.guitartuner.songs.SongStore;
import com.example.guitartuner.ui.Anim;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Песни из файлов Guitar Pro. После импорта выбранная дорожка конвертируется
 * в последовательность нот и играется так же, как уроки.
 */
public class SongsFragment extends Fragment {

    private final List<Song> songs = new ArrayList<>();
    private SongsAdapter adapter;
    private TextView textEmpty;

    private final SongConverter converter = new SongConverter();
    private AlertDialog progressDialog;

    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onFilePicked);

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_songs, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.recyclerSongs);
        FloatingActionButton fabImport = view.findViewById(R.id.fabImportSong);
        textEmpty = view.findViewById(R.id.textSongsEmpty);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SongsAdapter(songs, this::openSong, this::showSongMenu);
        recyclerView.setAdapter(adapter);

        // у файлов Guitar Pro нет общего MIME-типа, поэтому фильтруем по расширению после выбора
        fabImport.setOnClickListener(v -> filePickerLauncher.launch(new String[]{"*/*"}));

        reloadSongs();
        return view;
    }

    // ---------- список ----------

    private void reloadSongs() {
        songs.clear();
        for (File file : SongStore.listSources(requireContext())) {
            SongStore.ConvertedSong converted =
                    SongStore.load(SongStore.convertedFile(requireContext(), file));

            String title = converted != null && !converted.title.isEmpty()
                    ? converted.title
                    : stripExtension(file.getName());

            String subtitle;
            if (converted != null) {
                subtitle = "Партия: " + converted.trackName + " · " + describeSteps(
                        converted.notes.size(), converted.chordCount());
            } else if (SongConverter.isSupportedFile(file.getName())) {
                subtitle = "Нажмите, чтобы подготовить к игре";
            } else {
                subtitle = "Формат не поддерживается";
            }

            songs.add(new Song(title, file.getAbsolutePath(), subtitle));
        }
        adapter.notifyDataSetChanged();
        textEmpty.setVisibility(songs.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static String describeSteps(int steps, int chords) {
        return chords == 0 ? steps + " нот" : steps + " шагов, из них " + chords + " аккордов";
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // ---------- импорт ----------

    private void onFilePicked(@Nullable Uri uri) {
        if (uri == null) return;

        String displayName = queryDisplayName(uri);
        if (displayName == null) displayName = "song_" + System.currentTimeMillis();

        if (!SongConverter.isSupportedFile(displayName)) {
            showError("Поддерживаются файлы Guitar Pro: .gp, .gpx, .gp5, .gp4, .gp3");
            return;
        }

        File destFile = new File(SongStore.sourcesDir(requireContext()), displayName);

        try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(destFile)) {

            if (in == null) throw new IOException("Не удалось открыть файл");

            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

        } catch (IOException e) {
            showError("Ошибка импорта: " + e.getMessage());
            return;
        }

        // файл с тем же именем мог быть импортирован раньше — старая конвертация больше не актуальна
        //noinspection ResultOfMethodCallIgnored
        SongStore.convertedFile(requireContext(), destFile).delete();

        reloadSongs();
        convert(destFile);
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = requireContext().getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ---------- конвертация ----------

    private void convert(File source) {
        if (!SongConverter.isSupportedFile(source.getName())) {
            showError("Формат не поддерживается. Нужен файл Guitar Pro");
            return;
        }

        progressDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(stripExtension(source.getName()))
                .setMessage("Читаю ноты из файла…")
                .setCancelable(false)
                .setNegativeButton("Отмена", (d, w) -> converter.cancel())
                .show();

        converter.convert(requireContext(), source, new SongConverter.Callback() {
            @Override
            public void onSuccess(ParsedSong song) {
                dismissProgress();
                chooseTrack(source, song);
            }

            @Override
            public void onError(String message) {
                dismissProgress();
                showError(message);
            }
        });
    }

    private void chooseTrack(File source, ParsedSong song) {
        List<ParsedSong.Track> playable = song.playableTracks();

        if (playable.isEmpty()) {
            showError("В файле нет гитарных партий для 6-струнной гитары");
            return;
        }
        if (playable.size() == 1) {
            saveAndOpen(source, song, playable.get(0));
            return;
        }

        String[] items = new String[playable.size()];
        for (int i = 0; i < items.length; i++) {
            ParsedSong.Track track = playable.get(i);
            items[i] = track.name + " · " + describeSteps(track.notes.size(), track.chordCount());
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Какую партию играть?")
                .setItems(items, (dialog, which) -> saveAndOpen(source, song, playable.get(which)))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void saveAndOpen(File source, ParsedSong song, ParsedSong.Track track) {
        String title = song.title.isEmpty() ? stripExtension(source.getName()) : song.title;
        SongStore.ConvertedSong converted =
                new SongStore.ConvertedSong(title, track.name, track.tuning, track.notes, track.bars, track.timing);

        File convertedFile = SongStore.convertedFile(requireContext(), source);
        try {
            SongStore.save(convertedFile, converted);
        } catch (IOException e) {
            showError("Не удалось сохранить песню: " + e.getMessage());
            return;
        }

        reloadSongs();
        openPlayer(convertedFile);
    }

    // ---------- открытие и меню песни ----------

    private void openSong(Song song) {
        File source = new File(song.getFilePath());
        File convertedFile = SongStore.convertedFile(requireContext(), source);
        // песни, сконвертированные старой версией (без аккордов), конвертируем заново
        if (SongStore.load(convertedFile) != null) {
            openPlayer(convertedFile);
        } else {
            convert(source);
        }
    }

    private void openPlayer(File convertedFile) {
        Anim.openDetail(requireActivity(), LessonDetailFragment.newInstanceForSong(convertedFile));
    }

    private void showSongMenu(Song song) {
        File source = new File(song.getFilePath());
        String[] items = {"Выбрать другую партию", "Показать ноты (партитура)", "Удалить"};

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(song.getTitle())
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        convert(source);
                    } else if (which == 1) {
                        Anim.openDetail(requireActivity(),
                                SongDetailFragment.newInstance(song.getFilePath(), song.getTitle()));
                    } else {
                        confirmDelete(song, source);
                    }
                })
                .show();
    }

    private void confirmDelete(Song song, File source) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Удалить песню?")
                .setMessage(song.getTitle())
                .setPositiveButton("Удалить", (d, w) -> {
                    SongStore.delete(requireContext(), source);
                    reloadSongs();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // ---------- прочее ----------

    private void showError(String message) {
        if (!isAdded()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void dismissProgress() {
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        converter.cancel();
        dismissProgress();
    }
}
