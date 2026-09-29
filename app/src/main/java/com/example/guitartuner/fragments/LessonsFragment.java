package com.example.guitartuner.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.guitartuner.R;
import com.example.guitartuner.adapters.LessonsAdapter;
import com.example.guitartuner.models.LessonLibrary;

public class LessonsFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_lessons, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.recyclerLessons);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        LessonsAdapter adapter = new LessonsAdapter(LessonLibrary.all(), lesson -> {
            LessonDetailFragment detailFragment = LessonDetailFragment.newInstance(lesson);
            requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, detailFragment)
                    .addToBackStack(null)
                    .commit();
        });

        recyclerView.setAdapter(adapter);

        return view;
    }
}
