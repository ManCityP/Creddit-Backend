package com.crdt.users;

import com.crdt.*;
import de.mkammerer.argon2.*;

import java.security.SecureRandom;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

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
        if (id < 0)
            return;
        if (username == null || username.isEmpty() || username.length() > 32)
            return;
        if(!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$") || email.length() > 255)
            return;
        /*if (password == null || password.length() < 8 || password.length() > 32)
            return;*/
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

    public void register() throws SQLException {
        if(pfp == null)
            pfp = new Media(MediaType.IMAGE, "");
        String sql = "INSERT INTO users (username, email, password_hash, gender, bio, pfp) VALUES (?, ?, ?, ?, ?, ?)";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setString(1, this.username);
        stmt.setString(2, this.email);
        stmt.setString(3, HashPassword(this.password));
        stmt.setString(4, this.gender.toString());
        stmt.setString(5, this.bio);
        stmt.setString(6, this.pfp.GetURL());
        stmt.executeUpdate();
    }

    public static User login(String s, String p) throws SQLException {
        String sql = "SELECT * FROM users WHERE (username = ? OR email = ?)";
        PreparedStatement stmt = Database.PrepareStatement(sql);
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
        return null;
    }

    public void update() throws SQLException {
        if(!this.active)
            return;
        String sql = "UPDATE users SET username = ?, password_hash = ?, bio = ?, pfp = ? WHERE id = ?";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setString(1, this.username);
        stmt.setString(2, HashPassword(this.password));
        stmt.setString(3, this.bio);
        stmt.setString(4, this.pfp.GetURL());
        stmt.setInt(5, this.id);
        stmt.executeUpdate();
    }

    public void delete() throws SQLException {
        if(!this.active)
            return;
        String sql = "UPDATE users SET active = 0 WHERE id = ?";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        stmt.executeUpdate();
    }

    public void activate() throws SQLException { //todo put in admin
        if(this.active)
            return;
        String sql = "UPDATE users SET active = 1 WHERE id = ?";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        stmt.executeUpdate();
    }

    public void viewPost(Post post) throws SQLException {
        if(!this.active || post == null || post.GetID() <= 0)
            return;
        String sql = "INSERT INTO posts_views (post_id, user_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE view_time = VALUES(?)";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, post.GetID());
        stmt.setInt(2, this.id);
        stmt.setTimestamp(3, Timestamp.from(Instant.now()));
        stmt.executeUpdate();
    }

    private PriorityQueue<Post> ScorePosts(ArrayList<Post> posts) throws SQLException {
        Map<Integer, Double> postScores = new LinkedHashMap<>();
        ArrayList<Subcreddit> subs = new ArrayList<>();
        ArrayList<User> followers = new ArrayList<>();
        Map<String, Integer> freq = new HashMap<>();
        if(this.id > 0) {
            followers = this.GetFriends();
            subs = this.GetSubcreddits();
            freq = this.GetFrequentCategories();
        }
        double subcredditWeight = 200.0; double followerWeight = 100.0; double voteWeight = 10.0; double timeWeight = -5.0; double categoryWeight = 1.0;
        boolean subcredditMatch = false, userFollowMatch = false;
        int categoryMatch = 0;
        for(Post post : posts) {
            for(Subcreddit sub : subs) {
                if (sub.GetSubId() == post.GetID()) {
                    subcredditMatch = true;
                    break;
                }
            }
            for(User user : followers) {
                if(user.id == post.GetAuthor().id) {
                    userFollowMatch = true;
                    break;
                }
            }
            ArrayList<String> categories = post.GetCategories();
            for(String category : categories) {
                if(freq.containsKey(category))
                    categoryMatch += Math.min(freq.get(category), 5);
            }
            long hoursOld = Duration.between(post.GetTimeCreated().toInstant(), Instant.now()).toHours();
            double score = (subcredditWeight * (subcredditMatch? 1 : 0)) + (followerWeight * (userFollowMatch? 1 : 0)) + (voteWeight * ((double)post.GetVotes()/1000.0))
                            + (timeWeight * hoursOld) + (categoryWeight * categoryMatch);
            postScores.put(post.GetID(), score);
        }

        PriorityQueue<Post> pq = new PriorityQueue<>(
                (a, b) -> Double.compare(postScores.get(b.GetID()), postScores.get(a.GetID()))
        );
        pq.addAll(posts);
        return pq;
    }

    public static ArrayList<Post> GetPostFeed(User user, int lastID) {
        ArrayList<Post> result = new ArrayList<>();
        try {
            ArrayList<Post> posts = Database.GetAllPosts();
            if(user == null)
                user = new User(0, "Default", "default@default.com", "", Gender.MALE, "", new Media(MediaType.IMAGE, ""), null, true);
            PriorityQueue<Post> sorted = user.ScorePosts(posts);
            if(lastID > 0) {
                while (!sorted.isEmpty()) {
                    if (sorted.poll().GetID() == lastID)
                        break;
                }
            }
            for(int i = 0; i < 10 && !sorted.isEmpty(); i++) {
                result.add(sorted.poll());
            }
            return result;
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Map<String, Integer> GetFrequentCategories() throws SQLException {
        Map<String, Integer> freq = new HashMap<>();
        String sql = "SELECT * FROM posts_views WHERE user_id = ?";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        ResultSet rs = stmt.executeQuery();
        while(rs.next()) {
            Post post = Database.GetPost(rs.getInt("post_id"));
            ArrayList<String> categories = post.GetCategories();
            for(String category : categories)
                freq.put(category, freq.containsKey(category)? freq.get(category) + 1 : 1);
        }
        return freq;
    }

    public void sharePost(Post post) {
        //TODO wainting for Meho (Meho here, this can wait for next update, also "wainting" ;) )
    }

    public void savePost(Post post) {
        //TODO wainting for Meho (Meho here, this can wait for next update, also "wainting" ;) )
    }

    public void joinSubcreddit(Subcreddit subcreddit) throws SQLException {
        //TODO: Check if you are banned from the subcreddit
        if(!this.active || subcreddit == null || subcreddit.GetSubId() <= 0)
            return;
        String sql = "INSERT INTO subcreddit_members (user_id, subcreddit_id, accepted) VALUES (?, ?, ?)";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        stmt.setInt(2, subcreddit.GetSubId());
        stmt.setInt(3, subcreddit.GetPrivate()? 0 : 1);
        stmt.executeUpdate();
    }

    public void leaveSubcreddit(Subcreddit subcreddit) throws SQLException {
        if(!this.active || subcreddit == null || subcreddit.GetSubId() <= 0)
            return;
        String sql = "DELETE FROM subcreddit_members WHERE (user_id = ? AND subcreddit_id = ?)";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        stmt.setInt(2, subcreddit.GetSubId());
        stmt.executeUpdate();
    }

    public ArrayList<Subcreddit> GetSubcreddits() throws SQLException {
        ArrayList<Subcreddit> subcreddits = new ArrayList<>();

        String sql = "SELECT * FROM subcreddit_members WHERE (accepted = 1 AND user_id = ?) ORDER BY create_time DESC";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            subcreddits.add(Database.GetSubcreddit(rs.getInt("subcreddit_id")));
        }
        return subcreddits;
    }

    public void sendFriendRequest(User user) throws SQLException {
        if(!this.active)
            return;
        int senderId = this.id;
        int receiverId = user.id;
        if (this.GetFriends().contains(user) || this.GetSentFriendRequests().contains(user) || this.GetReceivedFriendRequests().contains(user))
            return;
        String sql = "INSERT INTO followers (follower_id, followed_id) VALUES (?, ?)";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, senderId);
        stmt.setInt(2, receiverId);
        stmt.executeUpdate();
    }

    public void unfriend(User friend) throws SQLException {
        if(!this.active)
            return;
        String sql = "DELETE FROM followers WHERE (follower_id = ? AND followed_id = ?) OR (follower_id = ? AND followed_id = ?)";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id); stmt.setInt(2, friend.id);
        stmt.setInt(3, friend.id); stmt.setInt(4, this.id);
        stmt.executeUpdate();
    }

    public ArrayList<User> GetFriends() throws SQLException {
        ArrayList<User> friends = new ArrayList<>();
        String sql = "SELECT * FROM followers WHERE accepted = 1 AND (follower_id = ? OR followed_id = ?) ORDER BY create_time DESC";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        stmt.setInt(2, this.id);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            int follower_id = rs.getInt("follower_id");
            User user = Database.GetUser(follower_id == this.id? rs.getInt("followed_id") : follower_id);
            if(!user.active)
                continue;
            friends.add(user);
        }
        return friends;
    }

    public ArrayList<User> GetSentFriendRequests() throws SQLException {
        ArrayList<User> friends = new ArrayList<>();
        String sql = "SELECT * FROM followers WHERE accepted = 0 AND (follower_id = ?) ORDER BY create_time DESC";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            User user = Database.GetUser(rs.getInt("followed_id"));
            if(!user.active)
                continue;
            friends.add(user);
        }
        return friends;
    }

    public ArrayList<User> GetReceivedFriendRequests() throws SQLException {
        ArrayList<User> friends = new ArrayList<>();
        String sql = "SELECT * FROM followers WHERE accepted = 0 AND (followed_id = ?) ORDER BY create_time DESC";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            User user = Database.GetUser(rs.getInt("follower_id"));
            if(!user.active)
                continue;
            friends.add(user);
        }
        return friends;
    }

    public ArrayList<Message> GetPrivateMessageFeed(User friend, int lastMessageID) throws SQLException {
        ArrayList<Message> messages = new ArrayList<>();
        int id1 = this.id;
        int id2 = friend.id;
        String sql;
        if(lastMessageID > 0)
            sql = "SELECT * FROM messages ORDER BY id DESC WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?) AND id < ? LIMIT 20";
        else
            sql = "SELECT * FROM messages ORDER BY id DESC WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?) LIMIT 20";
        PreparedStatement stmt = Database.PrepareStatement(sql);
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
        return messages;
    }

    public ArrayList<Message> GetLatestPrivateMessages(User friend, int lastMessageID) throws SQLException {
        ArrayList<Message> messages = new ArrayList<>();
        int id1 = this.id;
        int id2 = friend.id;
        String sql = "SELECT * FROM messages ORDER BY id ASC WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?) AND id > ?";
        PreparedStatement stmt = Database.PrepareStatement(sql);
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
        return messages;
    }

    public void addReport(Report report) {
        //TODO wainting for Meho
    }

    public void vote(Voteable voteable, int voteValue) throws SQLException { // voteValue -> {1: upvote, -1: downvote, 0: remove vote}
        if(!this.active || voteable == null || (voteValue != -1 && voteValue != 1 && voteValue != 0))
            return;
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
            String sql = "INSERT INTO votes_posts (user_id, post_id, value) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value)";
            PreparedStatement stmt = Database.PrepareStatement(sql);
            stmt.setInt(1, this.id);
            stmt.setInt(2, post.GetID());
            stmt.setInt(3, voteValue);
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
            String sql = "INSERT INTO votes_comments (user_id, comment_id, value) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value)";
            PreparedStatement stmt = Database.PrepareStatement(sql);
            stmt.setInt(1, this.id);
            stmt.setInt(2, comment.getID());
            stmt.setInt(3, voteValue);
            stmt.executeUpdate();
        }
    }

    public int CheckVote(Post post) throws SQLException {
        String sql = "SELECT * FROM votes_posts WHERE (user_id = ? AND post_id = ?)";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        stmt.setInt(2, post.GetID());
        ResultSet rs = stmt.executeQuery();
        if(rs.next()) {
            return rs.getInt("value");
        }
        return 0;
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
