package com.crdt.users;

public class Moderator {
    private ArrayList<Subcreddit> subcreddits;

    public Moderator(int id, String firstName, String lastName, String email,
                     Gender gender, String bio, Media profileMedia,
                     Timestamp joinDate, ArrayList<Subcreddit> subcreddits) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.gender = gender;
        this.bio = bio;
        this.profileMedia = profileMedia;
        this.joinDate = joinDate;
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
