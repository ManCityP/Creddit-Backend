package com.crdt;

import com.crdt.users.User;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;

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
    private int replyCount;

    public Post(int id, User author, Subcreddit subcreddit, String title, String content, ArrayList<Media> media, ArrayList<String> categories, Timestamp timeCreated, Timestamp timeEdited, int votes, int replyCount) {
        if (id <= 0)
            return;

        if (title == null || title.isEmpty() || title.length() > 255)
            return;
        /*if(content == null || content.isEmpty())
            return;*/

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
        this.replyCount = replyCount;
    }

    public int create() throws SQLException {
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
        return genID;
    }

    // TODO: Do this like a normal human being
    public void update() throws SQLException {
        this.delete();
        this.create();
    }

    public void delete() throws SQLException {
        String sql = "DELETE FROM posts WHERE id = ?";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        stmt.executeUpdate();
    }

    public void updateVotes(User voter, int voteValue) throws SQLException {
        if (voteValue == 0) {
            String sql = "DELETE FROM votes_posts WHERE (user_id = ? AND post_id = ?)";
            PreparedStatement stmt = Database.PrepareStatement(sql);
            stmt.setInt(1, voter.getId());
            stmt.setInt(2, this.id);
            stmt.executeUpdate();
            return;
        }
        String sql = "INSERT INTO votes_posts (user_id, post_id, value) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value)";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, voter.getId());
        stmt.setInt(2, this.id);
        stmt.setInt(3, voteValue);
        stmt.executeUpdate();
    }

    public Map<Comment, Map<Comment, PriorityQueue<Comment>>> GetCommentFeed(int parentID, int lastID) throws SQLException {
        Map<Comment, Map<Comment, PriorityQueue<Comment>>> commentMapMap = new LinkedHashMap<>();
        PriorityQueue<Comment> comments = new PriorityQueue<>(
                (fris, hassan) -> Double.compare(hassan.getVotes(), fris.getVotes())
        );
        String sql;
        if(lastID > 0)
            sql = "SELECT * FROM comments WHERE post_id = ? AND parent_id = ? AND id < ? ORDER BY id DESC";
        else
            sql = "SELECT * FROM comments WHERE post_id = ? AND parent_id = ? ORDER BY id DESC";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        stmt.setInt(2, parentID);
        if(lastID > 0)
            stmt.setInt(3, lastID);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            int commentID = rs.getInt("id");
            int votes = 0;
            String sql2 = "SELECT * FROM votes_comments WHERE comment_id = ?";
            PreparedStatement stmt2 = Database.PrepareStatement(sql2);
            stmt2.setInt(1, commentID);
            ResultSet rs2 = stmt2.executeQuery();
            while(rs2.next()) {
                votes += (rs2.getInt("value"));
            }

            comments.add(new Comment(commentID, this, Database.GetUser(rs.getInt("author_id")), rs.getString("content"),
                    new Media(MediaType.from(rs.getString("media_type")), rs.getString("media_url")), parentID, votes, 0,
                    rs.getTimestamp("create_time"), rs.getTimestamp("edit_time")));
        }
        while(!comments.isEmpty()) {
            Comment comm = comments.poll();
            PriorityQueue<Comment> lvl2_replies = comm.GetReplies();
            comm.setReplyCount(lvl2_replies.size());
            Map<Comment, PriorityQueue<Comment>> commentMap = new LinkedHashMap<>();
            while(!lvl2_replies.isEmpty()) {
                Comment reply = lvl2_replies.poll();
                PriorityQueue<Comment> lvl3_replies = reply.GetReplies();
                commentMap.put(reply, lvl3_replies);
            }
            commentMapMap.put(comm, commentMap);
        }
        return commentMapMap;
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
    public int GetReplyCount() {
        return replyCount;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Post) {
            return this.id == ((Post) obj).id;
        }
        return false;
    }
}