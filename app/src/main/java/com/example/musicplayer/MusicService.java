package com.example.musicplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.io.IOException;
import java.util.ArrayList;

public class MusicService extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {

    private MediaPlayer mediaPlayer;
    private ArrayList<Song> songs;
    private int currentSongPosition;
    private final IBinder musicBind = new MusicBinder();

    private static final String CHANNEL_ID = "MUSIC_PLAYER_CHANNEL";
    private static final int NOTIFICATION_ID = 1;

    public class MusicBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mediaPlayer = new MediaPlayer();
        initMusicPlayer();
    }

    private void initMusicPlayer() {
        mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
    }

    public void setSongs(ArrayList<Song> songs) {
        this.songs = songs;
    }

    public void playSong(int songIndex) {
        if (mediaPlayer == null) {
            initMusicPlayer();
        }
        mediaPlayer.reset();
        currentSongPosition = songIndex;
        Song songToPlay = songs.get(currentSongPosition);

        // ** THIS IS THE CORRECTED LINE **
        Uri songUri = Uri.parse(songToPlay.getUriString());

        try {
            mediaPlayer.setDataSource(getApplicationContext(), songUri);
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            Log.e("MUSIC SERVICE", "Error setting data source", e);
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mp.start();
        showNotification();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (songs != null && !songs.isEmpty()) {
            playNext();
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e("MUSIC SERVICE", "MediaPlayer error: " + what);
        mp.reset();
        return true;
    }

    // --- Playback Controls ---
    public void go() {
        mediaPlayer.start();
        showNotification();
    }

    public void pause() {
        mediaPlayer.pause();
        showNotification();
    }

    public void playNext() {
        currentSongPosition++;
        if (currentSongPosition >= songs.size()) {
            currentSongPosition = 0;
        }
        playSong(currentSongPosition);
    }

    public void playPrev() {
        currentSongPosition--;
        if (currentSongPosition < 0) {
            currentSongPosition = songs.size() - 1;
        }
        playSong(currentSongPosition);
    }

    // --- Getters for UI updates ---
    public int getCurrentPosition() {
        return mediaPlayer.getCurrentPosition();
    }

    public int getDuration() {
        return mediaPlayer.getDuration();
    }


    public boolean isPlaying() {
        try {
            return mediaPlayer.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public int getAudioSessionId() {
        return mediaPlayer.getAudioSessionId();
    }

    public void seekTo(int position) {
        mediaPlayer.seekTo(position);
    }

    public Song getCurrentSong() {
        if (songs != null && !songs.isEmpty()){
            return songs.get(currentSongPosition);
        }
        return null;
    }

    // --- Service Lifecycle & Binding ---
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return musicBind;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return false;
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }

    // --- Foreground Service Notification ---
    private void showNotification() {
        createNotificationChannel();
        Song currentSong = songs.get(currentSongPosition);

        Intent notificationIntent = new Intent(this, PlayerActivity.class);
        notificationIntent.putExtra("songs", songs);
        notificationIntent.putExtra("pos", currentSongPosition);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(currentSong.getTitle())
                .setContentText(currentSong.getArtist())
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Music Player Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}