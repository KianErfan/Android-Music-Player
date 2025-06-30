package com.example.musicplayer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.MediaMetadataRetriever;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

public class PlayerActivity extends AppCompatActivity {

    private MusicService musicService;
    private boolean serviceBound = false;
    private Intent playIntent;
    private ArrayList<Song> songList;
    private int currentPosition;

    // UI Elements
    private TextView titleTextView, artistTextView, currentTimeTextView, totalTimeTextView;
    private SeekBar seekBar;
    private ImageButton playPauseButton, prevButton, nextButton;
    private ImageView albumArtImageView;
    private VisualizerView visualizerView;

    private Visualizer visualizer;
    private final Handler uiUpdateHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        // Retrieve intent extras
        songList = (ArrayList<Song>) getIntent().getSerializableExtra("songs");
        currentPosition = getIntent().getIntExtra("pos", 0);

        // Initialize UI components
        initViews();
        setupListeners();
    }

    private void initViews() {
        titleTextView = findViewById(R.id.song_title_textview);
        artistTextView = findViewById(R.id.song_artist_textview);
        currentTimeTextView = findViewById(R.id.current_time_textview);
        totalTimeTextView = findViewById(R.id.total_time_textview);
        seekBar = findViewById(R.id.seek_bar);
        playPauseButton = findViewById(R.id.play_pause_button);
        prevButton = findViewById(R.id.prev_button);
        nextButton = findViewById(R.id.next_button);
        albumArtImageView = findViewById(R.id.album_art_imageview);
        visualizerView = findViewById(R.id.visualizer);

        // Make song title scroll if too long
        titleTextView.setSelected(true);
    }

    // --- Service Binding ---
    private final ServiceConnection musicConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            serviceBound = true;
            updateUI(); // Initial UI update once service is connected
            setupVisualizer();
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
            bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private void setupListeners() {
        playPauseButton.setOnClickListener(v -> {
            if (serviceBound) {
                if (musicService.isPlaying()) {
                    musicService.pause();
                } else {
                    musicService.go();
                }
                updatePlayPauseButton();
            }
        });

        nextButton.setOnClickListener(v -> {
            if (serviceBound) musicService.playNext();
        });

        prevButton.setOnClickListener(v -> {
            if (serviceBound) musicService.playPrev();
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && serviceBound) {
                    musicService.seekTo(progress);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private final Runnable updateUIRunnable = new Runnable() {
        @Override
        public void run() {
            if (serviceBound && musicService.isPlaying()) {
                int current = musicService.getCurrentPosition();
                seekBar.setProgress(current);
                currentTimeTextView.setText(formatTime(current));
            }
            // Check if song has changed
            if (serviceBound && musicService.getCurrentSong() != null && !titleTextView.getText().equals(musicService.getCurrentSong().getTitle())) {
                updateUI();
                setupVisualizer(); // Re-setup visualizer for the new song
            }
            uiUpdateHandler.postDelayed(this, 500); // Update twice a second
        }
    };

    private void updateUI() {
        if (!serviceBound || musicService.getCurrentSong() == null) return;
        Song currentSong = musicService.getCurrentSong();
        titleTextView.setText(currentSong.getTitle());
        artistTextView.setText(currentSong.getArtist());
        int duration = musicService.getDuration();
        seekBar.setMax(duration);
        totalTimeTextView.setText(formatTime(duration));
        updatePlayPauseButton();


        loadAlbumArt(currentSong.getUriString());

        // Start updating seekbar and time
        uiUpdateHandler.post(updateUIRunnable);
    }

    private void updatePlayPauseButton() {
        if (serviceBound) {
            if (musicService.isPlaying()) {
                playPauseButton.setImageResource(R.drawable.ic_pause);
            } else {
                playPauseButton.setImageResource(R.drawable.ic_play);
            }
        }
    }

    // Corrected loadAlbumArt to use URI
    private void loadAlbumArt(String uriString) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            Uri uri = Uri.parse(uriString);
            retriever.setDataSource(this, uri);
            byte[] art = retriever.getEmbeddedPicture();
            if (art != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
                albumArtImageView.setImageBitmap(bitmap);
            } else {
                albumArtImageView.setImageResource(R.drawable.ic_music_note);
            }
        } catch (Exception e) {
            albumArtImageView.setImageResource(R.drawable.ic_music_note);
            Log.e("PlayerActivity", "Error loading album art", e);
        } finally {
            try {
                retriever.release();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String formatTime(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / (1000 * 60)) % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    // --- Visualizer ---
    private void setupVisualizer() {
        try {
            int audioSessionId = musicService.getAudioSessionId();
            if (audioSessionId != -1) {
                releaseVisualizer(); // Release any existing instance
                visualizer = new Visualizer(audioSessionId);
                visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
                visualizer.setDataCaptureListener(
                        new Visualizer.OnDataCaptureListener() {
                            public void onWaveFormDataCapture(Visualizer visualizer, byte[] bytes, int samplingRate) {}

                            public void onFftDataCapture(Visualizer visualizer, byte[] bytes, int samplingRate) {
                                if (visualizerView != null) {
                                    visualizerView.updateVisualizer(bytes);
                                }
                            }
                        },
                        Visualizer.getMaxCaptureRate() / 2, false, true);
                visualizer.setEnabled(true);
            }
        } catch (Exception e) {
            Log.e("Visualizer", "Error setting up visualizer", e);
            Toast.makeText(this, "Visualizer not supported on this device.", Toast.LENGTH_SHORT).show();
        }
    }

    private void releaseVisualizer() {
        if (visualizer != null) {
            visualizer.release();
            visualizer = null;
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        uiUpdateHandler.removeCallbacks(updateUIRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (serviceBound) {
            uiUpdateHandler.post(updateUIRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        releaseVisualizer();
        if (serviceBound) {
            unbindService(musicConnection);
            serviceBound = false;
        }
        super.onDestroy();
    }

    // --- Custom Visualizer View (Inner Class) ---
    public static class VisualizerView extends View {
        private byte[] bytes;
        private final Paint paint = new Paint();

        public VisualizerView(Context context, @Nullable AttributeSet attrs) {
            super(context, attrs);
            paint.setColor(0xFFFFFFFF); // White bars
            paint.setStrokeWidth(8f);
        }

        public void updateVisualizer(byte[] bytes) {
            this.bytes = bytes;
            invalidate(); // Redraw the view
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            if (bytes == null) {
                return;
            }

            int width = getWidth();
            int height = getHeight();
            int barCount = bytes.length / 4;
            float barWidth = (float) width / barCount;

            for (int i = 0; i < barCount; i++) {
                int byteIndex = i * 4;
                float real = bytes[byteIndex];
                float imag = bytes[byteIndex + 1];
                float magnitude = (float) Math.hypot(real, imag);
                float barHeight = (magnitude / 128) * height; // Scale to view height

                float x = i * barWidth;
                canvas.drawLine(x, height, x, height - barHeight, paint);
            }
        }
    }
}
