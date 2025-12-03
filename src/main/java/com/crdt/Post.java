package com.crdt;

import com.crdt.users.User;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

public class Post implements Voteable, Reportable {
    private int id;
    private User author;
    private Subcreddit subcreddit;
    private String title;
    private String content;
    private ArrayList<Media> media;
    private ArrayList<String> categories;
    private Timestamp timeCreated;
    private Timestamp timeEdited;
    private int votes;
    private int comments;

    public Post(int id, User author, Subcreddit subcreddit, String title, String content, ArrayList<Media> media, ArrayList<String> categories, Timestamp timeCreated, Timestamp timeEdited, int votes, int comments) {
        if (id <= 0)
            return;

        if (title == null || title.isEmpty() || title.length() > 255)
            return;
        if(content == null || content.isEmpty())
            return;

        this.id = id;
        this.author = author;
        this.subcreddit = subcreddit;
        this.title = title;
        this.content = content;
        this.media = media;
        this.categories = categories;
        this.timeCreated = timeCreated;
        this.timeEdited = timeEdited;
        this.votes = votes;
        this.comments = comments;
    }

    public void create() throws SQLException {
        String sql = "INSERT INTO posts (author_id, subcreddit_id, title, content) VALUES (?, ?, ?, ?)";
        PreparedStatement stmt = Database.PrepareStatement(sql, true);
        stmt.setInt(1, this.author.getId());
        stmt.setInt(2, this.subcreddit == null? 0 : this.subcreddit.GetSubId());
        stmt.setString(3, this.title);
        stmt.setString(4, this.content);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        int genID = -1;
        if(rs.next()) {
            genID = rs.getInt(1);
        }
        if(genID <= 0)
            throw new SQLException("Could not insert post!");

        if(this.categories != null) {
            for (String category : this.categories) {
                int categoryID = Database.CategoryExists(category);
                if (categoryID == 0)
                    categoryID = Database.InsertCategory(category.toLowerCase());
                if(categoryID == 0)
                    throw new SQLException("Could not insert category");
                String sql2 = "INSERT INTO post_categories (post_id, category_id) VALUES (?, ?)";
                PreparedStatement stmt2 = Database.PrepareStatement(sql2);
                stmt2.setInt(1, genID);
                stmt2.setInt(2, categoryID);
                stmt2.executeUpdate();
            }
        }

        if(this.media != null) {
            for(Media md : media) {
                if(md.GetURL() != null && !md.GetURL().isEmpty()) {
                    String sql2 = "INSERT INTO post_media (post_id, media_url, media_type) VALUES (?, ?, ?)";
                    PreparedStatement stmt2 = Database.PrepareStatement(sql2);
                    stmt2.setInt(1, genID);
                    stmt2.setString(2, md.GetURL());
                    stmt2.setString(3, md.GetType().toString());
                    stmt2.executeUpdate();
                }
            }
        }
    }

    public void delete() throws SQLException {
        String sql = "DELETE FROM posts WHERE id = ?";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        stmt.executeUpdate();
    }

    public int GetID() {
        return id;
    }

    public User GetAuthor() {
        return author;
    }

    public Subcreddit GetSubcreddit() {
        return subcreddit;
    }

    public String GetTitle() {
        return title;
    }

    public String GetContent() {
        return content;
    }

    public ArrayList<Media> GetMedia() {
        return media;
    }

    public ArrayList<String> GetCategories() {
        return categories;
    }

    public Timestamp GetTimeCreated() {
        return timeCreated;
    }

    public Timestamp GetTimeEdited() {
        return timeEdited;
    }

    public int GetVotes() {
        return votes;
    }
    public int GetComments() {
        return comments;
    }
}