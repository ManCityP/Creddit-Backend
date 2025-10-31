package com.crdt.users;

import com.crdt.Media;

import java.sql.Timestamp;

public class Admin extends User {
    public Admin(int id, String username, String email, String password, Gender gender, String bio, Media pfp, Timestamp timeCreated) {
        super(id, username, email, password, gender, bio, pfp, timeCreated);
    }

   public void BanMember(User user) {

   }
   public void UnbanMember(User user){

    }

}
