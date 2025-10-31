package com.crdt;

import com.crdt.users.Gender;

public enum MediaType {
    IMAGE("Image"),
    VIDEO("Video"),
    AUDIO("Audio"),
    OTHER("Other");

    private final String mediaType;

    MediaType(String mt) {
        this.mediaType = mt;
    }

    public static MediaType toMediaType(String s) {
        if(s.equalsIgnoreCase("Image"))
            return IMAGE;
        else if(s.equalsIgnoreCase("Video"))
            return VIDEO;
        if(s.equalsIgnoreCase("Audio"))
            return AUDIO;
        else if(s.equalsIgnoreCase("Other"))
            return OTHER;
        else
            return null;
    }
    public String toString() {
        return this.mediaType;
    }
}
