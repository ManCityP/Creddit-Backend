package com.crdt;

import com.crdt.users.User;

import java.sql.PreparedStatement;
import java.sql.SQLException;
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

    //Constructor
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

    //getters
    public int GetID(){return id;}
    public User GetSender(){return sender;}
    public User GetReceiver() {return receiver;}
    public String GetText() {return text;}
    public Media GetMedia() {return media;}
    public Timestamp GetCreate_time() {return create_time;}
    public Timestamp GetEdit_time() {return edit_time;}
    public boolean GetRead(){return read;}

    public void send() throws SQLException {
        if(this.media == null)
            this.media = new Media(MediaType.NONE, "");
        String sql = "INSERT INTO messages (sender_id, receiver_id, content, media_url, media_type) VALUES (?, ?, ?, ?, ?)";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.sender.getId());
        stmt.setInt(2, this.receiver.getId());
        stmt.setString(3, this.text);
        stmt.setString(4, this.media.GetURL());
        stmt.setString(5, this.media.GetType().toString());
        stmt.executeUpdate();
    }

    //TODO: Make it not delete, but add deleted TINYINT
    public void delete() throws SQLException {
        String sql = "DELETE FROM messages WHERE id = ?";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        stmt.executeUpdate();
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Message) {
            return this.id == ((Message) obj).id;
        }
        return false;
    }
}
