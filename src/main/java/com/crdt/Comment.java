package com.crdt;

import com.crdt.users.User;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.PriorityQueue;

public class Comment implements Voteable, Reportable {
    private int id;
    private Post post;
    private User author;
    private int parentID;
    private String content;
    private Media media;
    private int votes;
    private int replyCount;
    private Timestamp timeCreated;
    private Timestamp timeEdited;

    public Comment(int id, Post post, User author, String content, Media media, int parentID, int votes, int replyCount, Timestamp createTime, Timestamp editTime) {
        this.id = id;
        this.post = post;
        this.author = author;
        this.content = content;
        this.media = media;
        this.parentID = parentID;
        this.votes = votes;
        this.replyCount = replyCount;
        this.timeCreated = createTime;
        this.timeEdited = editTime;
    }

    public void create() throws SQLException {
        String sql = "INSERT INTO comments (post_id, author_id, parent_id, content, media_url, media_type) VALUES (?, ?, ?, ?, ?, ?)";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.post.GetID());
        stmt.setInt(2, this.author.getId());
        stmt.setInt(3, this.parentID);
        stmt.setString(4, this.content);
        stmt.setString(5, this.media.GetURL());
        stmt.setString(6, this.media.GetType().toString());
        stmt.executeUpdate();
    }

    //TODO: Make this not delete, but delete contents only (probably a deleted tinyint)
    public void delete() throws SQLException {
        String sql = "DELETE FROM comments WHERE id = ?";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        stmt.executeUpdate();
    }

    public void updateVote(User voter, int voteValue) throws SQLException {
        if (voteValue == 0) {
            String sql = "DELETE FROM votes_comments WHERE (user_id = ? AND comment_id = ?)";
            PreparedStatement stmt = Database.PrepareStatement(sql);
            stmt.setInt(1, voter.getId());
            stmt.setInt(2, this.id);
            stmt.executeUpdate();
            return;
        }
        String sql = "INSERT INTO votes_comments (user_id, comment_id, value) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value)";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, voter.getId());
        stmt.setInt(2, this.id);
        stmt.setInt(3, voteValue);
        stmt.executeUpdate();
    }

    public PriorityQueue<Comment> GetReplies() throws SQLException {
        PriorityQueue<Comment> comments = new PriorityQueue<>(
                (fris, hassan) -> Double.compare(hassan.getVotes(), fris.getVotes())
        );
        String sql = "SELECT * FROM comments WHERE post_id = ? AND parent_id = ? ORDER BY id DESC";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, post.GetID());
        stmt.setInt(2, this.id);
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

            int replies = 0;
            String sql3 = "SELECT COUNT(*) AS count FROM comments WHERE parent_id = ?";
            PreparedStatement stmt3 = Database.PrepareStatement(sql3);
            stmt3.setInt(1, commentID);
            ResultSet rs3 = stmt3.executeQuery();
            if (rs3.next()) {
                replies = rs3.getInt("count");
            }

            comments.add(new Comment(commentID, this.post, Database.GetUser(rs.getInt("author_id")), rs.getString("content"),
                    new Media(MediaType.from(rs.getString("media_type")), rs.getString("media_url")), this.id, votes, replies,
                    rs.getTimestamp("create_time"), rs.getTimestamp("edit_time")));
        }
        return comments;
    }

    public int getID() {return id;}
    public Post getPost() {return post;}
    public User getAuthor() {return author;}
    public int getParent() {return parentID;}
    public String getContent() {return content;}
    public Media getMedia() {return media;}
    public int getVotes() {return votes;}
    public int getReplyCount() {return replyCount;}
    public Timestamp getTimeCreated() {return timeCreated;}
    public Timestamp getTimeEdited() {return timeEdited;}

    public void setReplyCount(int replyCount) {this.replyCount = replyCount;}

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Comment) {
            return this.id == ((Comment) obj).id;
        }
        return false;
    }
}
