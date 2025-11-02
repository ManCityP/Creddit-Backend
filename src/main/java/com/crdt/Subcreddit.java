package com.crdt;

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
        this.timecreated = timecreated;
        this.isPrivate = isPrivate;
        this.creator = creator;
        this.subLogo = logo;
    }
    public ArrayList<User> GetMembers() {
        ArrayList<User> members = new ArrayList<>();
        String sql = "SELECT * FROM subcreddit_members ORDER BY id DESC WHERE (accepted = 1 AND subcreddit_id = " + this.id + ")";

        try (PreparedStatement stmt = Database.PrepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                members.add(Database.GetUser(rs.getInt("user_id")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return members;
    }

    public ArrayList<User> GetBannedMembers() {
        if(this.id <= 0)
            return null;

        ArrayList<User> bannedMembers = new ArrayList<>();
        String sql = "SELECT * FROM bans ORDER BY id DESC WHERE (subcreddit_id = " + this.id + ")";

        try (PreparedStatement stmt = Database.PrepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                bannedMembers.add(Database.GetUser(rs.getInt("user_id")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return bannedMembers;
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

     public Media GetLogo() {
        return subLogo;
    }

    public boolean GetPrivate() {
        return isPrivate;
    }

    void UpdateDescription (String description){
        this.description = description;
    }

     void UpdateLogo (Media logo){
        this.subLogo = logo;
    }
}

