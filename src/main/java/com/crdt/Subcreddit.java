package com.crdt;

import com.crdt.users.User;

import java.sql.Timestamp;
import java.util.ArrayList;

public class Subcreddit {
    private int id;
    private ArrayList members;
    private String name ;
    private String description ;
    private Timestamp timecreated;
    private User creator;
    private Media subLogo;
    private boolean isPrivate;
    private ArrayList moderators;
    private String rules;
    private ArrayList posts;



    public Subcreddit(int id, ArrayList members, String name, String description, Timestamp timecreated, User creator,  Media logo, boolean isPrivate, ArrayList moderators, String rules ){
        this.id = id;
        this.members = members;
        this.name = name;
        this.timecreated = timecreated;
        this.isPrivate = isPrivate;
        this.moderators = moderators;
        this.rules = rules;
        this.creator = creator;
        this.subLogo = logo;
        this.posts = null;
    }
    public ArrayList GetMembers(){
        return members;
    }

    public ArrayList GetModerators(){
        return members;
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

    public String GetRules() {
        return rules;
    }

    public User GetCreator() {
        return creator;
    }

     public Media GetLogo() {
        return subLogo;
    }


    void AddPost (Post post){
        posts.add(post);
    }
    void RemovePost (Post post){
        posts.remove(post);
    }

    void AddMembers (User user){
        members.add(user);
    }
    void RemoveMembers (User user){
        members.remove(user);
    }

    void AssignModerator (User user){
        moderators.add(user);
    }
    void RemoveModerator (User user){
        moderators.remove(user);
    }

    void UpdateDescription (String description){
        this.description = description;
    }

     void UpdateLogo (Media logo){
        this.subLogo = logo;
    }


    void UpdateRules (String rules){
        this.rules = rules;
    }
}

