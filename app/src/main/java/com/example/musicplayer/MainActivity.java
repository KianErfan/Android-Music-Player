package com.example.musicplayer;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.widget.Toast;
import android.content.ContentUris;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements SongAdapter.OnSongListener {

    private ArrayList<Song> songList;
    private RecyclerView recyclerView;
    private MusicService musicService;
    private boolean serviceBound = false;
    private Intent playIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        songList = new ArrayList<>();

        requestPermissions();
    }

    // --- Service Connection ---
    private final ServiceConnection musicConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            musicService.setSongs(songList);
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        if (playIntent == null) {
            playIntent = new Intent(this, MusicService.class);
            bindService(playIntent, musicConnection, BIND_AUTO_CREATE);
            startService(playIntent);
        }
    }

    // --- Music Loading & UI Setup ---

    private void loadSongs() {
        ContentResolver contentResolver = getContentResolver();
        Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;


        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST
        };

        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";
        Cursor cursor = contentResolver.query(musicUri, projection, selection, null, MediaStore.Audio.Media.TITLE + " ASC");

        songList.clear();

        if (cursor != null && cursor.moveToFirst()) {
            int idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
            int titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);

            do {
                long thisId = cursor.getLong(idColumn);
                String thisTitle = cursor.getString(titleColumn);
                String thisArtist = cursor.getString(artistColumn);
                if (thisArtist == null || thisArtist.equals("<unknown>")) {
                    thisArtist = "Unknown Artist";
                }

                // Construct the proper content URI for this song.
                Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, thisId);

                // Add the new Song object with the URI as a string
                songList.add(new Song(thisId, thisTitle, thisArtist, contentUri.toString()));

            } while (cursor.moveToNext());
            cursor.close();
        } else {
            Toast.makeText(this, "No music files found on device.", Toast.LENGTH_LONG).show();
        }
        setupRecyclerView();
    }

    private void setupRecyclerView() {
        SongAdapter songAdapter = new SongAdapter(songList, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(songAdapter);
    }

    @Override
    public void onSongClick(int position) {
        if (serviceBound) {
            musicService.playSong(position);
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("songs", songList);
            intent.putExtra("pos", position);
            startActivity(intent);
        }
    }

    // --- Permission Handling ---
    private void requestPermissions() {
        String permission = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadSongs();
        } else {
            permissionLauncher.launch(permission);
        }
    }

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    loadSongs();
                } else {
                    Toast.makeText(this, "Permission denied. Cannot load songs.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onDestroy() {
        if (serviceBound) {
            unbindService(musicConnection);
        }
        // If the app is fully closing, stop the service.
        if (isFinishing()) {
            stopService(playIntent);
        }
        super.onDestroy();
    }
}
