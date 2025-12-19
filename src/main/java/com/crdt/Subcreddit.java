package com.crdt;

import com.crdt.users.Moderator;
import com.crdt.users.User;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

public class Subcreddit {
    private int id;
    private String name ;
    private String description ;
    private Timestamp timecreated;
    private User creator;
    private Media subLogo;
    private boolean isPrivate;


    public Subcreddit(int id, String name, String description, Timestamp timecreated, User creator,  Media logo, boolean isPrivate){
        this.id = id;
        this.name = name;
        this.description = description;
        this.timecreated = timecreated;
        this.creator = creator;
        this.subLogo = logo;
        this.isPrivate = isPrivate;
    }

    public int create() throws SQLException {
        String sql = "INSERT INTO subcreddits (name, description, creator_id, logo, private) VALUES (?, ?, ?, ?, ?)";
        if(subLogo == null)
            subLogo = new Media(MediaType.NONE, "");
        PreparedStatement stmt = Database.PrepareStatement(sql, true);
        stmt.setString(1, this.name);
        stmt.setString(2, this.description);
        stmt.setInt(3, this.creator.getId());
        stmt.setString(4, this.subLogo.GetURL());
        stmt.setInt(5, this.isPrivate? 1 : 0);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        int genID = -1;
        if(rs.next()) {
            genID = rs.getInt(1);
        }
        if(genID <= 0)
            throw new SQLException("Could not create subcreddit!");
        this.id = genID;
        this.creator.joinSubcreddit(this);
        this.AddModerator(creator);
        return genID;
    }

    public void update() throws SQLException {
        String sql = "UPDATE subcreddits SET name = ?, description = ?, logo = ?, private = ? WHERE id = ?";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setString(1, this.name);
        stmt.setString(2, this.description);
        stmt.setString(3, this.subLogo.GetURL());
        stmt.setInt(4, this.isPrivate? 1 : 0);
        stmt.executeUpdate();
    }

    public void delete() throws SQLException {
        String sql = "DELETE FROM posts WHERE subcreddit_id = ?";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        stmt.executeUpdate();

        sql = "DELETE FROM subcreddits WHERE id = ?";
        stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        stmt.executeUpdate();
    }

    public void AddModerator(User user) throws SQLException {
        String sql = "INSERT INTO subcreddit_moderators (user_id, subcreddit_id) VALUES (?, ?)";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, user.getId());
        stmt.setInt(2, this.id);
        stmt.executeUpdate();
    }

    public ArrayList<User> GetMembers(int lastID) throws SQLException {
        ArrayList<User> members = new ArrayList<>();

        String sql;
        if(lastID > 0)
        sql = "SELECT * FROM subcreddit_members WHERE (accepted = 1 AND subcreddit_id = ?) AND user_id < ? ORDER BY id DESC";
        else
            sql = "SELECT * FROM subcreddit_members WHERE (accepted = 1 AND subcreddit_id = ?) ORDER BY id DESC";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        if(lastID > 0)
            stmt.setInt(2, lastID);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            User member = Database.GetUser(rs.getInt("user_id"));
            if(VerifyModeration(member))
                members.add(new Moderator(member));
            else
                members.add(member);
        }
        return members;
    }

    public User GetFirstModerator() throws SQLException {
        String sql = "SELECT * FROM subcreddit_moderators WHERE subcreddit_id = ? ORDER BY create_time ASC";

        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        ResultSet rs = stmt.executeQuery();
        if(rs.next())
            return Database.GetUser(rs.getInt("user_id"));
        return GetFirstMember();
    }

    public User GetFirstMember() throws SQLException {
        String sql = "SELECT * FROM subcreddit_members WHERE (accepted = 1 AND subcreddit_id = ?) ORDER BY id ASC";

        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        ResultSet rs = stmt.executeQuery();
        if(rs.next())
            return Database.GetUser(rs.getInt("user_id"));
        return null;
    }

    public ArrayList<User> GetBannedMembers() throws SQLException {
        if(this.id <= 0)
            return null;

        ArrayList<User> bannedMembers = new ArrayList<>();
        String sql = "SELECT * FROM bans WHERE (subcreddit_id = ?) ORDER BY id DESC";

        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            bannedMembers.add(Database.GetUser(rs.getInt("user_id")));
        }
        return bannedMembers;
    }

    public boolean VerifyModeration(User user) throws SQLException {
        if(this.GetCreator().equals(user))
            return true;
        String sql = "SELECT * FROM subcreddit_moderators WHERE (subcreddit_id = ? AND user_id = ?)";

        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        stmt.setInt(2, user.getId());
        ResultSet rs = stmt.executeQuery();
        return rs.next();
    }

    public int GetSubId() {
        return id;
    }

    public String GetSubName() {
        return name;
    }

    public String GetDescription() {
        return description;
    }

    public Timestamp GetTimecreated() {
        return timecreated;
    }

    public User GetCreator() {
        return creator;
    }
    public void SetCreator(User creator) {
        this.creator = creator;
    }

     public Media GetLogo() {
        return subLogo;
    }

    public boolean GetPrivate() {
        return isPrivate;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Subcreddit) {
            return this.id == ((Subcreddit) obj).id;
        }
        return false;
    }
}

