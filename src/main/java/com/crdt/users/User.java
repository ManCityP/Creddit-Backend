package com.crdt.users;

import com.crdt.*;

import java.sql.Timestamp;

public class User {
    protected int id;
    protected String username;
    protected String email;
    protected String password;
    protected Gender gender;
    protected String bio;
    protected Media pfp;
    protected Timestamp timeCreated;

    public User(int id, String username, String email, String password, Gender gender, String bio, Media pfp, Timestamp timeCreated) {
        if (id <= 0)
            return;
        if (username == null || username.isEmpty() || username.length() > 32)
            return;
        if(!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$") || email.length() > 255)
            return;
        if (password == null || password.length() < 8 || password.length() > 16)
            return;
        if (gender == null)
            return;

        this.username = username;
        this.id = id;
        this.email = email;
        this.password = password;
        this.gender = gender;
        this.bio = bio;
        this.pfp = pfp;
        this.timeCreated = timeCreated;
    }

    public void deleteUser(User user) {
        //TODO learn SQL
    }

    public void createPost(Post post) {
        //TODO
    }

    public void deletePost(Post post) {
        //TODO
    }

    public void sharePost(Post post) {
        //TODO
    }

    public void savePost(Post post) {
        //TODO
    }

    public void joinSubcreddit(Subcreddit subcreddit) {
        //TODO
    }

    public void leaveSubcreddit(Subcreddit subcreddit) {
        //TODO
    }

    public void CreateSubcreddit(Subcreddit subcreddit) {
        //TODO
    }

    public void deleteSubcreddit(Subcreddit subcreddit) {
        //TODO
    }

    public void privateMessage(Message message) {
        //TODO
    }

    public void followUser(User user) {
        //TODO
    }

    public void unFollowUser(User user) {
        //TODO
    }

    public void addReport(Report report) {
        //TODO
    }

    public void Upvote(Voteable voteable){
        //TODO
    }

    public void Downvote(Voteable voteable){
        //TODO
    }


    public void setUsername(String username) {
        if (username == null || username.isEmpty() || username.length() > 32)
            return;
        this.username = username;
    }

    public void setPassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 16)
            return;
        this.password = password;
    }

    public void setBio(String bio) {
        if (bio == null)
            return;
        this.bio = bio;
    }

    public void setPFP(Media pfp) {
        this.pfp = pfp;
    }

    public int getId() {return this.id;}
    public String getUsername() {return this.username;}
    public String getEmail() {return this.email;}
    public String getPassword() {return this.password;}
    public Gender getGender() {return this.gender;}
    public String getBio() {return this.bio;}
    public Media getPfp() {return this.pfp;}
    public Timestamp getTimeCreated() {return this.timeCreated;}
}
