package com.example.guitartuner.fragments;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.guitartuner.R;
import com.example.guitartuner.adapters.SongsAdapter;
import com.example.guitartuner.models.Song;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class SongsFragment extends Fragment {

    RecyclerView recyclerView;
    FloatingActionButton fabImport;
    SongsAdapter adapter;
    List<Song> songs;

    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onFilePicked);

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_songs, container, false);

        recyclerView = view.findViewById(R.id.recyclerSongs);
        fabImport = view.findViewById(R.id.fabImportSong);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        songs = loadImportedSongs();

        adapter = new SongsAdapter(songs, song -> {
            SongDetailFragment detail = SongDetailFragment.newInstance(song.getFilePath(), song.getTitle());
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, detail)
                    .addToBackStack(null)
                    .commit();
        });

        recyclerView.setAdapter(adapter);
        fabImport.setOnClickListener(v -> filePickerLauncher.launch(new String[]{"*/*"}));

        return view;
    }

    private void onFilePicked(@Nullable Uri uri) {
        if (uri == null) return;

        String displayName = queryDisplayName(uri);
        if (displayName == null) displayName = "song_" + System.currentTimeMillis();

        File songsDir = new File(requireContext().getFilesDir(), "songs");
        if (!songsDir.exists()) songsDir.mkdirs();

        File destFile = new File(songsDir, displayName);

        try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(destFile)) {

            if (in == null) throw new java.io.IOException("Не удалось открыть файл");

            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

        } catch (Exception e) {
            Toast.makeText(getContext(), "Ошибка импорта: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        songs.add(new Song(destFile.getName(), destFile.getAbsolutePath()));
        adapter.notifyItemInserted(songs.size() - 1);
    }

    private String queryDisplayName(Uri uri) {
        String name = null;
        try (android.database.Cursor cursor = requireContext().getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = cursor.getString(idx);
            }
        } catch (Exception ignored) {
        }
        return name;
    }

    private List<Song> loadImportedSongs() {
        List<Song> result = new ArrayList<>();
        File songsDir = new File(requireContext().getFilesDir(), "songs");
        File[] files = songsDir.listFiles();
        if (files != null) {
            for (File f : files) {
                result.add(new Song(f.getName(), f.getAbsolutePath()));
            }
        }
        return result;
    }
}