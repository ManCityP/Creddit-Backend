package com.crdt.users;

import com.crdt.Media;

import java.sql.SQLException;
import java.sql.Timestamp;

public class Admin extends Moderator {
    public Admin(int id, String username, String email, String password, Gender gender, String bio, Media pfp, Timestamp timeCreated, Timestamp lastSeen, boolean active) {
        super(id, username, email, password, gender, bio, pfp, timeCreated, lastSeen, active);
    }

    public void DeactivateUser(User user, String reason) throws SQLException {
        //BanMember(user, null, reason);
        user.deactivate();
    }

    public void ActivateUser(User user) throws SQLException {
        //UnbanMember(user, null);
        user.activate();
    }
}
