package com.example.musicplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {

    private final ArrayList<Song> songs;
    private final OnSongListener onSongListener;

    public SongAdapter(ArrayList<Song> songs, OnSongListener onSongListener) {
        this.songs = songs;
        this.onSongListener = onSongListener;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item, parent, false);
        return new SongViewHolder(view, onSongListener);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song currentSong = songs.get(position);
        holder.titleTextView.setText(currentSong.getTitle());
        holder.artistTextView.setText(currentSong.getArtist());
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    public static class SongViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        TextView titleTextView;
        TextView artistTextView;
        OnSongListener onSongListener;

        public SongViewHolder(@NonNull View itemView, OnSongListener onSongListener) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.song_title);
            artistTextView = itemView.findViewById(R.id.song_artist);
            this.onSongListener = onSongListener;
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            onSongListener.onSongClick(getAdapterPosition());
        }
    }

    public interface OnSongListener {
        void onSongClick(int position);
    }
}