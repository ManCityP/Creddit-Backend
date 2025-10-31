package com.crdt;

import com.crdt.users.User;
import java.sql.Timestamp;

public class Comment {
    private int id;
    private Post post;
    private User author;
    private Comment parent;
    private String content;
    private Media media;
    private int votes;
    private Timestamp timeCreated;
    private Timestamp timeEdited;

    public Comment(int id, Post post, User author, String content, Media media, Comment parent, int votes, Timestamp createTime, Timestamp editTime) {
    }

    public int getID() {return id;}
    public Post getPost() {return post;}
    public User getAuthor() {return author;}
    public Comment getParent() {return parent;}
    public String getContent() {return content;}
    public Media getMedia() {return media;}
}
