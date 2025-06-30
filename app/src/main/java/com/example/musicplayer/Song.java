// In Song.java
package com.example.musicplayer;

import java.io.Serializable;

public class Song implements Serializable {
    private final long id;
    private final String title;
    private final String artist;
    private final String uriString;

    public Song(long id, String title, String artist, String uriString) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.uriString = uriString;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getUriString() { return uriString; }
}
