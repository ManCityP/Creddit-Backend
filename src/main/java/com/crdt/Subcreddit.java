package com.crdt;

import com.crdt.users.User;

import java.sql.Timestamp;
import java.util.ArrayList;

public class Subcreddit {
    private int id;
    private ArrayList<User> members;
    private String name ;
    private String description ;
    private Timestamp timecreated;
    private User creator;
    private Media subLogo;
    private boolean isPrivate;



    public Subcreddit(int id, ArrayList members, String name, String description, Timestamp timecreated, User creator,  Media logo, boolean isPrivate){
        this.id = id;
        this.members = members;
        this.name = name;
        this.timecreated = timecreated;
        this.isPrivate = isPrivate;
        this.creator = creator;
        this.subLogo = logo;
    }
    public ArrayList GetMembers(){
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

    public User GetCreator() {
        return creator;
    }

     public Media GetLogo() {
        return subLogo;
    }

    public boolean GetPrivate() {
        return isPrivate;
    }

    void AddMembers (User user){
        members.add(user);
    }
    void RemoveMembers (User user){
        members.remove(user);
    }

    void UpdateDescription (String description){
        this.description = description;
    }

     void UpdateLogo (Media logo){
        this.subLogo = logo;
    }
}

