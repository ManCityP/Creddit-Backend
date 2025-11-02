package com.crdt.users;

import com.crdt.Database;
import com.crdt.Media;
import com.crdt.Subcreddit;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class Moderator extends User {

    public Moderator(int id, String userName, String email, String password, Gender gender, String bio, Media profileMedia, Timestamp joinDate) {
        super(id,userName,email,password,gender,bio,profileMedia,joinDate);                
    }

    public void BanMember(User user, Subcreddit subcreddit, String reason) {
        if(user.getId() <= 0 || subcreddit.GetSubId() <= 0)
            return;
        String sql = "INSERT INTO bans (user_id, banned_by, subcreddit_id, reason) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = Database.PrepareStatement(sql)) {
            stmt.setInt(1, user.id);
            stmt.setInt(2, this.id);
            stmt.setInt(3, subcreddit.GetSubId());
            stmt.setString(4, reason);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean VerifyModeration(Subcreddit subcreddit) {
        if(subcreddit.GetSubId() <= 0)
            return false;

        String sql = "SELECT * FROM subcreddit_moderators WHERE (subcreddit_id = " + subcreddit.GetSubId() + " AND user_id = " + this.id + ")";

        try (PreparedStatement stmt = Database.PrepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if(rs.next())
                return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}
