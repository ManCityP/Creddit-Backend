package com.crdt.users;

public class Moderator extends User {
    private ArrayList<Subcreddit> subcreddits;

    public Moderator(int id, String userName, String email, String password,Gender gender, String bio, Media profileMedia,Timestamp joinDate, ArrayList<Subcreddit> subcreddits) {
        super(id,userName,email,password,gender,bio,profileMedia,joinDate);                
        this.subcreddits = subcreddits != null ? subcreddits : new ArrayList<>();
    }
    public ArrayList<Subcreddit> GetSubcreddits() {
        return subcreddits;
    }
    public void KickMember(User user) {
    }
    public boolean VerifyModeration(Subcreddit subcreddit) {
    }
    public void addSubcreddit(Subcreddit subcreddit) {
    }
}
