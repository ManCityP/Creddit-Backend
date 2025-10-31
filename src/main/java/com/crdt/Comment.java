package com.crdt;

import com.crdt.users.User;
import java.sql.Timestamp;

public class Comment {
    private int id;
    private Post post;
    private User author;
    private Comment parent;
    private String content;
    private String media_url;
    private String media_type;
    private Timestamp created;
    private Timestamp edited;

    public int getID() {return id;}
    public Post getPost() {return post;}
    public User getAuthor() {return author;}
    public Comment getParent() {return parent;}
    public String getContent() {return content;}
    public String getMedia_url() {return media_url;}
    public String getMedia_type() {return media_type;}
}
