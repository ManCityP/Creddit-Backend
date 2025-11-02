package com.crdt.users;

import com.crdt.*;
import de.mkammerer.argon2.*;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

public class User implements Reportable {
    protected int id;
    protected String username;
    protected String email;
    protected String password;
    protected Gender gender;
    protected String bio;
    protected Media pfp;
    protected Timestamp timeCreated;

    private static final Argon2Advanced ARGON2 = Argon2Factory.createAdvanced(Argon2Factory.Argon2Types.ARGON2id);

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

    private static String HashPassword(String password) {
        int iterations = 3;
        int memory = 1 << 15;
        int parallelism = 2;
        byte[] salt = new byte[16];
        new java.security.SecureRandom().nextBytes(salt);
        byte[] hash = ARGON2.rawHash(iterations, memory, parallelism, password.toCharArray(), salt);

        String str_salt = java.util.Base64.getEncoder().encodeToString(salt);
        String str_hash = java.util.Base64.getEncoder().encodeToString(hash);

        return str_salt + ":" + str_hash;
    }

    public void createUser() {
        String sql;
        if(this instanceof Admin)
            sql = "INSERT INTO posts (username, email, password_hash, gender, bio, pfp, admin) VALUES (?, ?, ?, ?, ?, ?, ?)";
        else
            sql = "INSERT INTO posts (username, email, password_hash, gender, bio, pfp) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = Database.PrepareStatement(sql)) {
            stmt.setString(1, this.getUsername());
            stmt.setString(2, this.getEmail());
            stmt.setString(3, HashPassword(this.getPassword()));
            stmt.setString(4, this.getGender().toString());
            stmt.setString(5, this.getBio());
            stmt.setString(6, this.getPfp().GetURL());
            if(this instanceof Admin)
                stmt.setInt(7, 1);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateProfile() {
        String sql = "UPDATE users SET username = ?, password_hash = ?, bio = ?, pfp = ? WHERE id = ?";
        try (PreparedStatement stmt = Database.PrepareStatement(sql)) {
            stmt.setString(1, this.getUsername());
            stmt.setString(2, HashPassword(this.getPassword()));
            stmt.setString(3, this.getBio());
            stmt.setString(4, this.getPfp().GetURL());
            stmt.setInt(5, this.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteUser(User user) {
        //TODO wainting for Meho
    }

    public void createPost(Post post) {
        try {
            for (String category : post.GetCategories()) {
                int categoryID = Database.CategoryExists(category);
                if (categoryID == 0)
                    categoryID = Database.InsertCategory(category.toLowerCase());
                String sql = "INSERT INTO post_categories (post_id, category_id) VALUES (?, ?)";
                PreparedStatement stmt = Database.PrepareStatement(sql);
                stmt.setInt(1, post.GetID());
                stmt.setInt(2, categoryID);
                stmt.executeUpdate();
            }

            String sql = "INSERT INTO posts (author_id, subcreddit_id, title, content) VALUES (?, ?, ?, ?)";
            PreparedStatement stmt = Database.PrepareStatement(sql);
            stmt.setInt(1, post.GetAuthor().GetID());
            stmt.setInt(2, post.GetSubcreddit().GetID());
            stmt.setString(3, post.GetTitle());
            stmt.setString(4, post.GetContent());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deletePost(Post post) {
        //TODO wainting for Meho
    }

    public void sharePost(Post post) {
        //TODO wainting for Meho
    }

    public void savePost(Post post) {
        //TODO wainting for Meho
    }

    public void joinSubcreddit(Subcreddit subcreddit) {
        //TODO wainting for Meho
    }

    public void leaveSubcreddit(Subcreddit subcreddit) {
        //TODO wainting for Meho
    }

    public void createSubcreddit(Subcreddit subcreddit) {
        String sql = "INSERT INTO subcreddits (name, description, creator_id, logo, private) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = Database.PrepareStatement(sql)) {
            stmt.setString(1, subcreddit.getName());
            stmt.setString(2, subcreddit.getDescription());
            stmt.setInt(3, subcreddit.getCreator().getID());
            stmt.setInt(4, subcreddit.getLogo().getURL());
            stmt.setInt(5, subcreddit.getPrivate()? 1 : 0);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteSubcreddit(Subcreddit subcreddit) {
        //TODO wainting for Meho
    }

    public void privateMessage(Message message) {
        String sql = "INSERT INTO messages (sender_id, receiver_id, content, media_url, media_type) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = Database.PrepareStatement(sql)) {
            stmt.setInt(1, message.GetSender().getId());
            stmt.setInt(2, message.GetReceiver().getId());
            stmt.setString(3, message.GetText());
            stmt.setString(4, message.GetMedia().GetURL());
            stmt.setString(5, message.GetMedia().GetType().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void sendFriendRequest(User user) {
        try {
            int senderId = this.getId();
            int receiverId = user.getId();
            if (Database.GetFriends(this).contains(user) || Database.GetSentFriendRequests(this).contains(user) || Database.GetReceivedFriendRequests(this).contains(user))
                return;

            String sql = "INSERT INTO followers (follower_id, followed_id) VALUES (?, ?)";
            PreparedStatement stmt = Database.PrepareStatement(sql);
            stmt.setInt(1, senderId);
            stmt.setInt(2, receiverId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void unfriend(User user) {
        //TODO wainting for Meho
    }

    public void addReport(Report report) {
        //TODO wainting for Meho
    }

    public void Upvote(Voteable voteable){
        //TODO wainting for Meho
    }

    public void Downvote(Voteable voteable){
        //TODO wainting for Meho
    }


    //TODO: Setters will probably be useless, waiting to be removed.
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
