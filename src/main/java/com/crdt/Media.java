package com.crdt;

import java.awt.*;

public class Media {
    private PageAttributes.MediaType mediaType;
    private String mediaUrl;

    public Media(PageAttributes.MediaType mediaType, String mediaUrl){
        this.mediaType = mediaType;
        this.mediaUrl = mediaUrl;
    }
    public PageAttributes.MediaType GetType(){
        return this.mediaType;
    }
    public String GetURL(){
        return this.mediaUrl;
    }
    public void SetType(PageAttributes.MediaType mediaType){
        this.mediaType = mediaType;
    }
    public void SetURL(String URL){
        this.mediaUrl= URL;
    }
}
