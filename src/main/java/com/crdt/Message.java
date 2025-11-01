package com.crdt;

import com.crdt.users.User;
import java.sql.Timestamp;

public class Message {
    private int id;
    private User sender;
    private User receiver;
    private String text;
    private Media media;
    private Timestamp create_time;
    private Timestamp edit_time;
    private boolean read;

    public Message(int id, User sender, User receiver, String text, Media media, Timestamp create_time, Timestamp edit_time, boolean read){

        this.id=id;
        this.sender=sender;
        this.receiver=receiver;
        this.text=text;
        this.media=media;
        this.create_time=create_time;
        this.edit_time=edit_time;
        this.read=read;

    }

    public int GetID(){return id;}
    public User GetSender(){return sender;}
    public User GetReceiver() {return receiver;}
    public String GetText() {return text;}
    public Media GetMedia() {return media;}
    public Timestamp GetCreate_time() {return create_time;}
    public Timestamp GetEdit_time() {return edit_time;}
    public boolean GetRead(){return read;}

}
