package com.example.guitartuner.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.guitartuner.R;
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
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_lesson, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Lesson lesson = lessons.get(position);
        holder.titleView.setText(lesson.getTitle());
        holder.descriptionView.setText(lesson.getDescription());
        holder.descriptionView.setVisibility(
                lesson.getDescription().isEmpty() ? View.GONE : View.VISIBLE);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onLessonClick(lesson);
        });
    }

    @Override
    public int getItemCount() {
        return lessons.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView titleView;
        final TextView descriptionView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.textLessonTitle);
            descriptionView = itemView.findViewById(R.id.textLessonDescription);
        }
    }
}
