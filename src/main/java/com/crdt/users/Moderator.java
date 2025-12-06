package com.crdt.users;

import com.crdt.Database;
import com.crdt.Media;
import com.crdt.Subcreddit;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class Moderator extends User {

    public Moderator(int id, String userName, String email, String password, Gender gender, String bio, Media profileMedia, Timestamp joinDate, Timestamp lastSeen, boolean active) {
        super(id, userName, email, password, gender, bio, profileMedia, joinDate, lastSeen, active);
    }

    public void BanMember(User user, Subcreddit subcreddit, String reason) throws SQLException {
        if(user.id <= 0)
            return;
        boolean global = (subcreddit == null);
        if(!global)
            if(!VerifyModeration(subcreddit))
                return;
        String sql = "INSERT INTO bans (user_id, banned_by, subcreddit_id, reason) VALUES (?, ?, ?, ?)";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, user.id);
        stmt.setInt(2, this.id);
        stmt.setInt(3, global? 0 : subcreddit.GetSubId());
        stmt.setString(4, reason);
        stmt.executeUpdate();
        if(!global)
            user.leaveSubcreddit(subcreddit);
    }

    public void UnbanMember(User user, Subcreddit subcreddit) throws SQLException {
        if(user.id <= 0)
            return;
        boolean global = (subcreddit == null);
        if(!global)
            if(!VerifyModeration(subcreddit))
                return;
        String sql = "DELETE FROM bans WHERE (user_id = ? AND subcreddit_id = ?)";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, user.id);
        stmt.setInt(2, global? 0 : subcreddit.GetSubId());
        stmt.executeUpdate();
        if(!global)
            user.joinSubcreddit(subcreddit);
    }

    public boolean VerifyModeration(Subcreddit subcreddit) throws SQLException {
        if(subcreddit.GetSubId() <= 0)
            return false;

        String sql = "SELECT * FROM subcreddit_moderators WHERE (subcreddit_id = ? AND user_id = ?)";

        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, subcreddit.GetSubId());
        stmt.setInt(2, this.id);
        ResultSet rs = stmt.executeQuery();
        if(rs.next())
            return true;
        return false;
    }
}
