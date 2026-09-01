package com.example.guitartuner.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.guitartuner.R;
import com.example.guitartuner.adapters.SongsAdapter;
import com.example.guitartuner.models.Song;

import java.util.ArrayList;
import java.util.List;

public class SongsFragment extends Fragment {

    RecyclerView recyclerView;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(
                R.layout.fragment_songs,
                container,
                false
        );

        recyclerView = view.findViewById(R.id.recyclerSongs);

        recyclerView.setLayoutManager(
                new LinearLayoutManager(getContext())
        );

        List<Song> songs = new ArrayList<>();

        for (int i = 1; i <= 10; i++) {
            songs.add(new Song("Песня " + i));
        }

        SongsAdapter adapter = new SongsAdapter(songs);

        recyclerView.setAdapter(adapter);

        return view;
    }
}