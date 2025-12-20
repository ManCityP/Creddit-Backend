package com.crdt;

import com.crdt.users.User;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Map;
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
    private boolean deleted;

    public Comment(int id, Post post, User author, String content, Media media, int parentID, int votes, int replyCount, Timestamp createTime, Timestamp editTime, boolean deleted) {
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
        this.deleted = deleted;
    }

    public int create() throws SQLException {
        if(media == null)
            media = new Media(MediaType.NONE, "");
        String sql = "INSERT INTO comments (post_id, author_id, parent_id, content, media_url, media_type) VALUES (?, ?, ?, ?, ?, ?)";
        PreparedStatement stmt = Database.PrepareStatement(sql, true);
        stmt.setInt(1, this.post.GetID());
        stmt.setInt(2, this.author.getId());
        stmt.setInt(3, this.parentID);
        stmt.setString(4, this.content);
        stmt.setString(5, this.media.GetURL());
        stmt.setString(6, this.media.GetType().toString());
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        int genID = -1;
        if(rs.next()) {
            genID = rs.getInt(1);
        }
        if(genID <= 0)
            throw new SQLException("Could not insert post!");
        return genID;
    }

    public void delete() throws SQLException {
        String sql = "UPDATE comments SET deleted = 1, content = ?, media_url = ?  WHERE id = ?";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setString(1, "This comment has been deleted");
        stmt.setString(2, "");
        stmt.setInt(3, this.id);
        stmt.executeUpdate();
    }

    public void update() throws SQLException {
        String sql = "UPDATE comments SET content = ?, media_url = ?, media_type = ? WHERE id = ?";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setString(1, this.content);
        stmt.setString(2, this.media.GetURL());
        stmt.setString(3, this.media.GetType().toString());
        stmt.setInt(4, this.id);
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

    public PriorityQueue<Comment> GetReplies(User user, Map<Integer, Integer> myVotes) throws SQLException {
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
            boolean found = false;
            while(rs2.next()) {
                int val = rs2.getInt("value");
                votes += val;
                if(user != null && rs2.getInt("user_id") == user.getId()) {
                    found = true;
                    myVotes.put(commentID, val);
                }
            }
            if(!found)
                myVotes.put(commentID, 0);

            int replies = 0;
            String sql3 = "SELECT COUNT(*) AS count FROM comments WHERE parent_id = ?";
            PreparedStatement stmt3 = Database.PrepareStatement(sql3);
            stmt3.setInt(1, commentID);
            ResultSet rs3 = stmt3.executeQuery();
            if (rs3.next()) {
                replies = rs3.getInt("count");
            }

            Media media = null;
            MediaType mediaType = MediaType.from(rs.getString("media_type"));
            if(mediaType != MediaType.NONE)
                media = new Media(mediaType, rs.getString("media_url"));

            comments.add(new Comment(commentID, this.post, Database.GetUser(rs.getInt("author_id")), rs.getString("content"),
                    media, this.id, votes, replies, rs.getTimestamp("create_time"), rs.getTimestamp("edit_time"), rs.getInt("deleted") != 0));
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
    public boolean getDeleted() {return deleted;}

    public void setReplyCount(int replyCount) {this.replyCount = replyCount;}

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Comment) {
            return this.id == ((Comment) obj).id;
        }
        return false;
    }
}
