package com.crdt.users;

import com.crdt.Media;
import com.crdt.Subcreddit;

import java.sql.Timestamp;
import java.util.ArrayList;

public class Moderator extends User {

    public Moderator(int id, String userName, String email, String password, Gender gender, String bio, Media profileMedia, Timestamp joinDate) {
        super(id,userName,email,password,gender,bio,profileMedia,joinDate);                
    }

    public void BanMember(User user, Subcreddit subcreddit) {

    }

    public boolean VerifyModeration(Subcreddit subcreddit) {

    }
}
