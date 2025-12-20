package com.crdt;

public enum MediaType {
    IMAGE("Image"),
    VIDEO("Video"),
    AUDIO("Audio"),
    OTHER("Other"),
    NONE("None");

    private final String mediaType;

    MediaType(String mt) {
        this.mediaType = mt;
    }

    public static MediaType from(String s) {
        if(s.equalsIgnoreCase("Image"))
            return IMAGE;
        if(s.equalsIgnoreCase("Video"))
            return VIDEO;
        if(s.equalsIgnoreCase("Audio"))
            return AUDIO;
        if(s.equalsIgnoreCase("Other"))
            return OTHER;
        if(s.equalsIgnoreCase("None"))
            return NONE;
        return null;
    }
    public String toString() {
        return this.mediaType;
    }
}
