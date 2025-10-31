package com.crdt;

public enum MediaType {
    IMAGE("Image"),
    VIDEO("Video"),
    AUDIO("Audio"),
    OTHER("Other");

    private final String mediaType;

    MediaType(String mt) {
        this.mediaType = mt;
    }

    public String getMediaType() {
        return this.mediaType;
    }
}
