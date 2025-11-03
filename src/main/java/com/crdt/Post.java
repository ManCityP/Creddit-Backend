package com.crdt;

import com.crdt.users.User;

import java.sql.PreparedStatement;
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

    public Post(int id, User author, Subcreddit subcreddit, String title, String content, ArrayList<Media> media, ArrayList<String> categories, Timestamp timeCreated, Timestamp timeEdited, int votes) {
        if (id <= 0)
            return;

        if ((title == null || title.isEmpty()) && (content == null || content.isEmpty()) && media == null)
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
    }

    public void create() {
        try {
            for (String category : this.categories) {
                int categoryID = Database.CategoryExists(category);
                if (categoryID == 0)
                    categoryID = Database.InsertCategory(category.toLowerCase());
                String sql = "INSERT INTO post_categories (post_id, category_id) VALUES (?, ?)";
                PreparedStatement stmt = Database.PrepareStatement(sql);
                stmt.setInt(1, this.id);
                stmt.setInt(2, categoryID);
                stmt.executeUpdate();
            }

            String sql = "INSERT INTO posts (author_id, subcreddit_id, title, content) VALUES (?, ?, ?, ?)";
            PreparedStatement stmt = Database.PrepareStatement(sql);
            stmt.setInt(1, this.author.getId());
            stmt.setInt(2, this.subcreddit.GetSubId());
            stmt.setString(3, this.title);
            stmt.setString(4, this.content);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void delete() {
        try {
            String sql = "DELETE FROM posts WHERE id = ?";
            PreparedStatement stmt = Database.PrepareStatement(sql);
            stmt.setInt(1, this.id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
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
}