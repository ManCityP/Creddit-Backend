package com.crdt.users;

import com.crdt.*;
import de.mkammerer.argon2.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

public class User implements Reportable {
    protected int id;
    protected String username;
    protected String email;
    protected String password;
    protected Gender gender;
    protected String bio;
    protected Media pfp;
    protected Timestamp timeCreated;
    protected  boolean active;

    private static final Argon2Advanced ARGON2 = Argon2Factory.createAdvanced(Argon2Factory.Argon2Types.ARGON2id);

    public User(int id, String username, String email, String password, Gender gender, String bio, Media pfp, Timestamp timeCreated, boolean active) {
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
        this.active = active;
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

    public void create() {
        String sql;
        if(this instanceof Admin)
            sql = "INSERT INTO posts (username, email, password_hash, gender, bio, pfp, admin) VALUES (?, ?, ?, ?, ?, ?, ?)";
        else
            sql = "INSERT INTO posts (username, email, password_hash, gender, bio, pfp) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = Database.PrepareStatement(sql)) {
            stmt.setString(1, this.username);
            stmt.setString(2, this.email);
            stmt.setString(3, HashPassword(this.password));
            stmt.setString(4, this.gender.toString());
            stmt.setString(5, this.bio);
            stmt.setString(6, this.pfp.GetURL());
            if(this instanceof Admin)
                stmt.setInt(7, 1);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void update() {
        if(!this.active)
            return;
        String sql = "UPDATE users SET username = ?, password_hash = ?, bio = ?, pfp = ? WHERE id = ?";
        try (PreparedStatement stmt = Database.PrepareStatement(sql)) {
            stmt.setString(1, this.username);
            stmt.setString(2, HashPassword(this.password));
            stmt.setString(3, this.bio);
            stmt.setString(4, this.pfp.GetURL());
            stmt.setInt(5, this.id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void delete() {
        if(!this.active)
            return;
        String sql = "UPDATE users SET active = 0 WHERE id = " + this.id;
        try (PreparedStatement stmt = Database.PrepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    //TODO: Move this to Post class
    public void createPost(Post post) {
        if(!this.active)
            return;
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
        //TODO wainting for Meho (Meho here, this will move to the Post class, also "wainting" ;) )
    }

    public void sharePost(Post post) {
        //TODO wainting for Meho (Meho here, this can wait for next update, also "wainting" ;) )
    }

    public void savePost(Post post) {
        //TODO wainting for Meho (Meho here, this can wait for next update, also "wainting" ;) )
    }

    public void joinSubcreddit(Subcreddit subcreddit) {
        if(!this.active)
            return;
        String sql = "INSERT INTO subcreddit_members (user_id, subcreddit_id, accepted) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = Database.PrepareStatement(sql)) {
            stmt.setInt(1, this.id);
            stmt.setInt(2, subcreddit.GetSubId());
            stmt.setInt(3, subcreddit.GetPrivate()? 0 : 1);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void leaveSubcreddit(Subcreddit subcreddit) {
        if(!this.active)
            return;
        String sql = "DELETE FROM subcreddit_members WHERE (user_id = ? AND subcreddit_id = ?)";
        try (PreparedStatement stmt = Database.PrepareStatement(sql)) {
            stmt.setInt(1, this.id);
            stmt.setInt(2, subcreddit.GetSubId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    //TODO: Move this to subcreddit class
    public void createSubcreddit(Subcreddit subcreddit) {
        if(!this.active)
            return;
        String sql = "INSERT INTO subcreddits (name, description, creator_id, logo, private) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = Database.PrepareStatement(sql)) {
            stmt.setString(1, subcreddit.GetSubName());
            stmt.setString(2, subcreddit.GetDescription());
            stmt.setInt(3, subcreddit.GetCreator().id);
            stmt.setString(4, subcreddit.GetLogo().GetURL());
            stmt.setInt(5, subcreddit.GetPrivate()? 1 : 0);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteSubcreddit(Subcreddit subcreddit) {
        //TODO wainting for Meho (Meho here, this will move to the subcreddit class, also "wainting" ;) )
    }

    public ArrayList<Subcreddit> GetSubcreddits() {
        ArrayList<Subcreddit> subcreddits = new ArrayList<>();
        String sql = "SELECT * FROM subcreddit_members ORDER BY id DESC WHERE (accepted = 1 AND user_id = " + this.id + ")";

        try (PreparedStatement stmt = Database.PrepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                subcreddits.add(Database.GetSubcreddit(rs.getInt("subcreddit_id")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return subcreddits;
    }

    public void sendFriendRequest(User user) {
        if(!this.active)
            return;
        try {
            int senderId = this.id;
            int receiverId = user.id;
            if (this.GetFriends().contains(user) || this.GetSentFriendRequests().contains(user) || this.GetReceivedFriendRequests().contains(user))
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

    public void unfriend(User friend) {
        if(!this.active)
            return;
        String sql = "DELETE FROM followers WHERE (follower_id = ? AND followed_id = ?) OR (follower_id = ? AND followed_id = ?)";
        try (PreparedStatement stmt = Database.PrepareStatement(sql)) {
            stmt.setInt(1, this.id); stmt.setInt(2, friend.id);
            stmt.setInt(3, friend.id); stmt.setInt(4, this.id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public ArrayList<User> GetFriends() {
        ArrayList<User> friends = new ArrayList<>();
        String sql = "SELECT * FROM followers WHERE accepted = 1 AND (follower_id = " + this.id + " OR followed_id = " + this.id + ") ORDER BY create_time DESC";
        try (PreparedStatement stmt = Database.PrepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int follower_id = rs.getInt("follower_id");
                User user = Database.GetUser(follower_id == this.id? rs.getInt("followed_id") : follower_id);
                if(!user.active)
                    continue;
                friends.add(user);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return friends;
    }

    public ArrayList<User> GetSentFriendRequests() {
        ArrayList<User> friends = new ArrayList<>();
        String sql = "SELECT * FROM followers WHERE accepted = 0 AND (follower_id = " + this.id + ") ORDER BY create_time DESC";
        try (PreparedStatement stmt = Database.PrepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                User user = Database.GetUser(rs.getInt("followed_id"));
                if(!user.active)
                    continue;
                friends.add(user);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return friends;
    }

    public ArrayList<User> GetReceivedFriendRequests() {
        ArrayList<User> friends = new ArrayList<>();
        String sql = "SELECT * FROM followers WHERE accepted = 0 AND (followed_id = " + this.id + ") ORDER BY create_time DESC";
        try (PreparedStatement stmt = Database.PrepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                User user = Database.GetUser(rs.getInt("follower_id"));
                if(!user.active)
                    continue;
                friends.add(user);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return friends;
    }

    public ArrayList<Message> GetPrivateMessageFeed(User friend, int lastMessageID) {
        ArrayList<Message> messages = new ArrayList<>();
        int id1 = this.id;
        int id2 = friend.id;
        String sql;
        if(lastMessageID > 0)
            sql = "SELECT * FROM messages ORDER BY id DESC WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?) AND id < ? LIMIT 20";
        else
            sql = "SELECT * FROM messages ORDER BY id DESC WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?) LIMIT 20";
        try(PreparedStatement stmt = Database.PrepareStatement(sql)) {
            stmt.setInt(1, id1); stmt.setInt(2, id2);
            stmt.setInt(3, id2); stmt.setInt(4, id1);
            if(lastMessageID > 0)
                stmt.setInt(5, lastMessageID);
            ResultSet rs = stmt.executeQuery();
            while(rs.next()) {
                int sender_id = rs.getInt("sender_id");
                messages.add(new Message(rs.getInt("id"), sender_id == id1? this : friend, sender_id == id1? friend : this,
                        rs.getString("content"), new Media(MediaType.toMediaType(rs.getString("media_type")), rs.getString("media_url")),
                        rs.getTimestamp("create_time"), rs.getTimestamp("edit_time"), rs.getInt("read") == 0? false : true
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }

    public ArrayList<Message> GetLatestPrivateMessages(User friend, int lastMessageID) {
        ArrayList<Message> messages = new ArrayList<>();
        int id1 = this.id;
        int id2 = friend.id;
        String sql = "SELECT * FROM messages ORDER BY id ASC WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?) AND id > ?";
        try(PreparedStatement stmt = Database.PrepareStatement(sql)) {
            stmt.setInt(1, id1); stmt.setInt(2, id2);
            stmt.setInt(3, id2); stmt.setInt(4, id1);
            stmt.setInt(5, lastMessageID);
            ResultSet rs = stmt.executeQuery();
            while(rs.next()) {
                int sender_id = rs.getInt("sender_id");
                messages.add(new Message(rs.getInt("id"), sender_id == id1? this : friend, sender_id == id1? friend : this,
                        rs.getString("content"), new Media(MediaType.toMediaType(rs.getString("media_type")), rs.getString("media_url")),
                        rs.getTimestamp("create_time"), rs.getTimestamp("edit_time"), rs.getInt("read") == 0? false : true
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
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
    public boolean getActive() {return this.active;}
}
