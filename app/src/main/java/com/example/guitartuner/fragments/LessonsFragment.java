package com.example.guitartuner.fragments;

import android.os.Bundle;
import android.view.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.guitartuner.R;
import com.example.guitartuner.adapters.LessonsAdapter;
import com.example.guitartuner.models.Lesson;

import java.util.ArrayList;
import java.util.List;

public class LessonsFragment extends Fragment {

    RecyclerView recyclerView;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_lessons,
                container,
                false);

        recyclerView = view.findViewById(R.id.recyclerLessons);

        recyclerView.setLayoutManager(
                new LinearLayoutManager(getContext())
        );

        List<Lesson> lessons = new ArrayList<>();

        for (int i = 1; i <= 10; i++) {
            lessons.add(new Lesson("Урок " + i));
        }

        LessonsAdapter adapter = new LessonsAdapter(lessons);

        recyclerView.setAdapter(adapter);

        return view;
    }
}