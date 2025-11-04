package com.crdt.users;

import com.crdt.*;
import de.mkammerer.argon2.*;

import java.security.SecureRandom;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;

public class User implements Reportable {
    protected int id;
    protected String username;
    protected String email;
    protected String password;
    protected Gender gender;
    protected String bio;
    protected Media pfp;
    protected Timestamp timeCreated;
    protected boolean active;

    private static final Argon2Advanced ARGON2 = Argon2Factory.createAdvanced(Argon2Factory.Argon2Types.ARGON2id);

    public User(int id, String username, String email, String password, Gender gender, String bio, Media pfp, Timestamp timeCreated, boolean active) {
        if (id <= 0)
            return;
        if (username == null || username.isEmpty() || username.length() > 32)
            return;
        if(!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$") || email.length() > 255)
            return;
        if (password == null || password.length() < 8 || password.length() > 32)
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
        new SecureRandom().nextBytes(salt);
        byte[] hash = ARGON2.rawHash(iterations, memory, parallelism, password.toCharArray(), salt);

        String str_salt = Base64.getEncoder().encodeToString(salt);
        String str_hash = Base64.getEncoder().encodeToString(hash);

        return str_salt + ":" + str_hash;
    }

    private static String HashPassword(String password, String str_salt) {
        int iterations = 3;
        int memory = 1 << 15;
        int parallelism = 2;
        byte[] salt = Base64.getDecoder().decode(str_salt);
        byte[] hash = ARGON2.rawHash(iterations, memory, parallelism, password.toCharArray(), salt);

        String str_hash = Base64.getEncoder().encodeToString(hash);

        return str_salt + ":" + str_hash;
    }

    public void register() {
        String sql;
        sql = "INSERT INTO users (username, email, password_hash, gender, bio, pfp) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = Database.PrepareStatement(sql)) {
            stmt.setString(1, this.username);
            stmt.setString(2, this.email);
            stmt.setString(3, HashPassword(this.password));
            stmt.setString(4, this.gender.toString());
            stmt.setString(5, this.bio);
            stmt.setString(6, this.pfp.GetURL());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static User login(String s, String p) {
        String sql = "SELECT * FROM users WHERE (username = ? OR email = ?)";
        try (PreparedStatement stmt = Database.PrepareStatement(sql)) {
            stmt.setString(1, s);
            stmt.setString(2, s);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String passwordHash = rs.getString("password_hash");
                String pass = User.HashPassword(p, passwordHash.split(":")[0]);
                if(!pass.equals(passwordHash))
                    return null;
                if(rs.getInt("admin") == 1)
                    return new Admin(rs.getInt("id"), rs.getString("username"), rs.getString("email"), p,
                            Gender.toGender(rs.getString("gender")), rs.getString("bio"), new Media(MediaType.IMAGE, rs.getString("pfp")),
                            rs.getTimestamp("create_time"), rs.getInt("active") != 0);

                return new User(rs.getInt("id"), rs.getString("username"), rs.getString("email"), p,
                        Gender.toGender(rs.getString("gender")), rs.getString("bio"), new Media(MediaType.IMAGE, rs.getString("pfp")),
                        rs.getTimestamp("create_time"), rs.getInt("active") != 0);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
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

    public void activate() { //todo put in admin
        if(this.active)
            return;
        String sql = "UPDATE users SET active = 1 WHERE id = " + this.id;
        try (PreparedStatement stmt = Database.PrepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void viewPost(Post post) {
        if(!this.active || post == null || post.GetID() <= 0)
            return;
        String sql = "INSERT INTO posts_views (post_id, user_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE view_time = VALUES(?)";
        try (PreparedStatement stmt = Database.PrepareStatement(sql)) {
            stmt.setInt(1, post.GetID());
            stmt.setInt(2, this.id);
            stmt.setTimestamp(3, Timestamp.from(Instant.now()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void sharePost(Post post) {
        //TODO wainting for Meho (Meho here, this can wait for next update, also "wainting" ;) )
    }

    public void savePost(Post post) {
        //TODO wainting for Meho (Meho here, this can wait for next update, also "wainting" ;) )
    }

    public void joinSubcreddit(Subcreddit subcreddit) {
        //TODO: Check if you are banned from the subcreddit
        if(!this.active || subcreddit == null || subcreddit.GetSubId() <= 0)
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
        if(!this.active || subcreddit == null || subcreddit.GetSubId() <= 0)
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
                        rs.getTimestamp("create_time"), rs.getTimestamp("edit_time"), rs.getInt("read") != 0
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
                        rs.getTimestamp("create_time"), rs.getTimestamp("edit_time"), rs.getInt("read") != 0
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

    public void vote(Voteable voteable, int voteValue) { // voteValue -> {1: upvote, -1: downvote, 0: remove vote}
        if(!this.active || voteable == null || (voteValue != -1 && voteValue != 1 && voteValue != 0))
            return;
        try {
            if (voteable instanceof Post) {
                Post post = (Post) voteable;
                if (post.GetID() <= 0)
                    return;
                if (voteValue == 0) {
                    String sql = "DELETE FROM votes_posts WHERE (user_id = ? AND post_id = ?)";
                    PreparedStatement stmt = Database.PrepareStatement(sql);
                    stmt.setInt(1, this.id);
                    stmt.setInt(2, post.GetID());
                    stmt.executeUpdate();
                    return;
                }
                String sql = "INSERT INTO votes_posts (user_id, post_id, value) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(?)";
                PreparedStatement stmt = Database.PrepareStatement(sql);
                stmt.setInt(1, this.id);
                stmt.setInt(2, post.GetID());
                stmt.setInt(3, voteValue);
                stmt.setInt(4, voteValue);
                stmt.executeUpdate();
            }
            else if(voteable instanceof Comment) {
                Comment comment = (Comment) voteable;
                if (comment.getID() <= 0)
                    return;
                if (voteValue == 0) {
                    String sql = "DELETE FROM votes_comments WHERE (user_id = ? AND comment_id = ?)";
                    PreparedStatement stmt = Database.PrepareStatement(sql);
                    stmt.setInt(1, this.id);
                    stmt.setInt(2, comment.getID());
                    stmt.executeUpdate();
                    return;
                }
                String sql = "INSERT INTO votes_comments (user_id, comment_id, value) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(?)";
                PreparedStatement stmt = Database.PrepareStatement(sql);
                stmt.setInt(1, this.id);
                stmt.setInt(2, comment.getID());
                stmt.setInt(3, voteValue);
                stmt.setInt(4, voteValue);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
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
