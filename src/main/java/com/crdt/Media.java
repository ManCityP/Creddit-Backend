package com.crdt;

public class Media {
    private MediaType mediaType;
    private String mediaUrl;

    public Media(MediaType mediaType, String mediaUrl){
        this.mediaType = mediaType;
        this.mediaUrl = mediaUrl;
    }
    public MediaType GetType(){
        return this.mediaType;
    }
    public String GetURL(){
        return this.mediaUrl;
    }
    public void SetType(MediaType mediaType){
        this.mediaType = mediaType;
    }
    public void SetURL(String URL){
        this.mediaUrl= URL;
    }
}
