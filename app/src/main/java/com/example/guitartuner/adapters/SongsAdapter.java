package com.example.guitartuner.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.guitartuner.R;
import com.example.guitartuner.models.Song;

import java.util.List;

public class SongsAdapter extends RecyclerView.Adapter<SongsAdapter.ViewHolder> {

    public interface OnSongClickListener {
        void onSongClick(Song song);
    }

    private final List<Song> songs;
    private final OnSongClickListener clickListener;
    private final OnSongClickListener longClickListener;

    public SongsAdapter(List<Song> songs, OnSongClickListener clickListener,
                        OnSongClickListener longClickListener) {
        this.songs = songs;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_song, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Song song = songs.get(position);
        holder.titleView.setText(song.getTitle());
        holder.subtitleView.setText(song.getSubtitle());
        holder.subtitleView.setVisibility(song.getSubtitle().isEmpty() ? View.GONE : View.VISIBLE);

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onSongClick(song);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener == null) return false;
            longClickListener.onSongClick(song);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView titleView;
        final TextView subtitleView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.textSongTitle);
            subtitleView = itemView.findViewById(R.id.textSongSubtitle);
        }
    }
}
