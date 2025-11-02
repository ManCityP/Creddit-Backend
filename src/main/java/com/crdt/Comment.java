package com.crdt;

import com.crdt.users.User;

import java.sql.PreparedStatement;
import java.sql.SQLException;
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
        this.id = id;
        this.post = post;
        this.author = author;
        this.content = content;
        this.media = media;
        this.parent = parent;
        this.votes = votes;
        this.timeCreated = createTime;
        this.timeEdited = editTime;
    }

    public void create() {
        String sql = "INSERT INTO comments (post_id, author_id, parent_id, content, media_url, media_type) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = Database.PrepareStatement(sql)) {
            stmt.setInt(1, this.post.GetID());
            stmt.setInt(2, this.author.getId());
            stmt.setInt(3, this.parent.getID());
            stmt.setString(4, this.content);
            stmt.setString(5, this.media.GetURL());
            stmt.setString(6, this.media.GetType().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int getID() {return id;}
    public Post getPost() {return post;}
    public User getAuthor() {return author;}
    public Comment getParent() {return parent;}
    public String getContent() {return content;}
    public Media getMedia() {return media;}
}
