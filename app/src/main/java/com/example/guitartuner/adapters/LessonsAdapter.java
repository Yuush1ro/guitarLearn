package com.example.guitartuner.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.guitartuner.models.Lesson;

import java.util.List;

public class LessonsAdapter
        extends RecyclerView.Adapter<LessonsAdapter.ViewHolder> {

    public interface OnLessonClickListener {
        void onLessonClick(Lesson lesson);
    }

    private final List<Lesson> lessons;
    private final OnLessonClickListener listener;

    public LessonsAdapter(List<Lesson> lessons, OnLessonClickListener listener) {
        this.lessons = lessons;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_2,
                        parent,
                        false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder,
            int position) {

        Lesson lesson = lessons.get(position);

        holder.textView.setText(lesson.getTitle());
        holder.subtitleView.setText(lesson.getDifficulty().label);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onLessonClick(lesson);
        });
    }

    @Override
    public int getItemCount() {
        return lessons.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView textView;
        TextView subtitleView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
            subtitleView = itemView.findViewById(android.R.id.text2);
        }
    }
}