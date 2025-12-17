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
    protected Timestamp lastSeen;
    protected boolean active;

    private static final Argon2Advanced ARGON2 = Argon2Factory.createAdvanced(Argon2Factory.Argon2Types.ARGON2id);

    public User(int id, String username, String email, String password, Gender gender, String bio, Media pfp, Timestamp timeCreated, Timestamp lastSeen, boolean active) {
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
        this.lastSeen = lastSeen;
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

    public int register() throws SQLException {
        if(pfp == null)
            pfp = new Media(MediaType.IMAGE, "");
        String sql = "INSERT INTO users (username, email, password_hash, gender, bio, pfp) VALUES (?, ?, ?, ?, ?, ?)";
        PreparedStatement stmt = Database.PrepareStatement(sql, true);
        stmt.setString(1, this.username);
        stmt.setString(2, this.email);
        stmt.setString(3, HashPassword(this.password));
        stmt.setString(4, this.gender.toString());
        stmt.setString(5, this.bio);
        stmt.setString(6, this.pfp.GetURL());
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        int genID = -1;
        if(rs.next()) {
            genID = rs.getInt(1);
        }
        if(genID <= 0)
            throw new SQLException("Could not register user!");
        return genID;
    }

    public static User login(String s, String p) throws SQLException {
        String sql = "SELECT * FROM users WHERE (username = ? OR email = ?)";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setString(1, s);
        stmt.setString(2, s);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
            if(rs.getInt("verified") == 0)
                return null;
            String passwordHash = rs.getString("password_hash");
            String pass = User.HashPassword(p, passwordHash.split(":")[0]);
            if(!pass.equals(passwordHash))
                return null;
            if(rs.getTimestamp("last_seen").toInstant().isAfter(Instant.now().minusSeconds(10)))
                throw new SQLException("online");
            if(rs.getInt("admin") == 1)
                return new Admin(rs.getInt("id"), rs.getString("username"), rs.getString("email"), p,
                        Gender.from(rs.getString("gender")), rs.getString("bio"), new Media(MediaType.IMAGE, rs.getString("pfp")),
                        rs.getTimestamp("create_time"), rs.getTimestamp("last_seen"), rs.getInt("active") != 0);
            return new User(rs.getInt("id"), rs.getString("username"), rs.getString("email"), p,
                    Gender.from(rs.getString("gender")), rs.getString("bio"), new Media(MediaType.IMAGE, rs.getString("pfp")),
                    rs.getTimestamp("create_time"), rs.getTimestamp("last_seen"), rs.getInt("active") != 0);
        }
        return null;
    }

    public void KeepAlive() throws SQLException {
        String sql = "UPDATE users SET last_seen = CURRENT_TIMESTAMP WHERE id = ?";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        stmt.executeUpdate();
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

    public void deactivate() throws SQLException {
        if(!this.active)
            return;
        String sql = "UPDATE users SET active = 0 WHERE id = ?";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        stmt.executeUpdate();
        ArrayList<Subcreddit> subcreddits = this.GetOwnedSubcreddits();
        for(Subcreddit sub : subcreddits) {
            User newOwner = sub.GetFirstModerator();
            if(newOwner == null)
                sub.delete();
            else {
                sub.SetCreator(newOwner);
                sub.update();
            }
        }
    }

    public void activate() throws SQLException {
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
        ArrayList<User> friends = new ArrayList<>();
        Map<String, Integer> freq = new HashMap<>();
        if(this.id > 0) {
            friends = this.GetFriends();
            subs = this.GetSubcreddits();
            freq = this.GetFrequentCategories();
        }
        double subcredditWeight = 200.0; double friendWeight = 100.0; double voteWeight = 10.0; double timeWeight = -2.0; double categoryWeight = 1.0;
        boolean subcredditMatch = false, userFollowMatch = false;
        int categoryMatch = 0;
        for(Post post : posts) {
            if(post.GetAuthor().id == this.id && post.GetTimeCreated().toInstant().isAfter(Instant.now().minusSeconds(60))) {
                postScores.put(post.GetID(), Double.MAX_VALUE);
                continue;
            }
            if(post.GetSubcreddit() != null) {
                for (Subcreddit sub : subs) {
                    if (sub.GetSubId() == post.GetSubcreddit().GetSubId()) {
                        subcredditMatch = true;
                        break;
                    }
                }
            }
            for(User user : friends) {
                if(user.id == post.GetAuthor().id || this.id == post.GetAuthor().id) {
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
            double score = (subcredditWeight * (subcredditMatch? 1 : 0)) + (friendWeight * (userFollowMatch? 1 : 0)) + (voteWeight * ((double)post.GetVotes()/1000.0))
                            + (timeWeight * hoursOld) + (categoryWeight * categoryMatch);
            postScores.put(post.GetID(), score);
        }

        PriorityQueue<Post> pq = new PriorityQueue<>(
                (fris, hassan) -> Double.compare(postScores.get(hassan.GetID()), postScores.get(fris.GetID()))
        );
        pq.addAll(posts);
        return pq;
    }

    public static ArrayList<Post> GetPostFeed(User user, String prompt, int lastID) throws SQLException {
        ArrayList<Post> result = new ArrayList<>();
        ArrayList<Post> posts = Database.GetAllPosts(prompt);
        if(user == null)
            user = new User(0, "Default", "default@default.com", "", Gender.MALE, "", new Media(MediaType.IMAGE, ""), null, null, true);
        PriorityQueue<Post> sorted = user.ScorePosts(posts);
        if(lastID > 0) {
            while (!sorted.isEmpty()) {
                if (sorted.poll().GetID() == lastID)
                    break;
            }
        }
        int limit = lastID > 0? 6 : 10;
        for(int i = 0; i < limit && !sorted.isEmpty(); i++) {
            result.add(sorted.poll());
        }
        return result;
    }

    public ArrayList<Post> GetAllPostsFilterVote(String prompt, int voteValue, int lastID) throws SQLException {
        ArrayList<Post> posts = new ArrayList<>();
        String sql;
        if(lastID > 0)
            sql = "SELECT posts.id, posts.author_id, posts.subcreddit_id, posts.title, posts.content, posts.create_time, posts.edit_time, " +
                    "votes_posts.post_id, votes_posts.user_id, votes_posts.value FROM posts " +
                    "JOIN votes_posts ON posts.id = votes_posts.post_id " +
                    "WHERE votes_posts.user_id = ? AND votes_posts.value = ? AND posts.id < ? " +
                    "ORDER BY posts.id DESC LIMIT 6";
        else
            sql = "SELECT posts.id, posts.author_id, posts.subcreddit_id, posts.title, posts.content, posts.create_time, posts.edit_time, " +
                    "votes_posts.post_id, votes_posts.user_id, votes_posts.value FROM posts " +
                    "JOIN votes_posts ON posts.id = votes_posts.post_id " +
                    "WHERE votes_posts.user_id = ? AND votes_posts.value = ? " +
                    "ORDER BY posts.id DESC LIMIT 10";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        stmt.setInt(2, voteValue);
        if(lastID > 0)
            stmt.setInt(3, lastID);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            int postid = rs.getInt("posts.id");
            ArrayList<String> categories = Database.GetPostCategories(postid);

            String title = rs.getString("posts.title");
            String content = rs.getString("posts.content");
            User author = Database.GetUser(rs.getInt("posts.author_id"));
            Subcreddit sub = Database.GetSubcreddit(rs.getInt("posts.subcreddit_id"));
            if(prompt != null && !prompt.isBlank()) {
                prompt = prompt.toLowerCase();
                if (!title.toLowerCase().contains(prompt) && !categories.contains(prompt) && !content.toLowerCase().contains(prompt)
                        && !author.getUsername().toLowerCase().contains(prompt) && (sub == null || !sub.GetSubName().toLowerCase().contains(prompt)))
                {
                    continue;
                }
            }

            ArrayList<Media> media = new ArrayList<>();

            String sql2 = "SELECT * FROM post_media WHERE (post_id = ?) ORDER BY id ASC";
            PreparedStatement stmt2 = Database.PrepareStatement(sql2);
            stmt2.setInt(1, postid);
            ResultSet rs2 = stmt2.executeQuery();
            while (rs2.next()) {
                media.add(new Media(MediaType.from(rs2.getString("media_type")), rs2.getString("media_url")));
            }

            int votes = 0;
            String sql3 = "SELECT * FROM votes_posts WHERE (post_id = ?)";
            PreparedStatement stmt3 = Database.PrepareStatement(sql3);
            stmt3.setInt(1, postid);
            ResultSet rs3 = stmt3.executeQuery();
            while(rs3.next()) {
                votes += rs3.getInt("value");
            }

            int comments = 0;
            String sql4 = "SELECT COUNT(*) AS count FROM comments WHERE post_id = ?";
            PreparedStatement stmt4 = Database.PrepareStatement(sql4);
            stmt4.setInt(1, postid);
            ResultSet rs4 = stmt4.executeQuery();
            if (rs4.next()) {
                comments = rs4.getInt("count");

            }
            posts.add(new Post(postid, author, sub, title, content, media, categories, rs.getTimestamp("posts.create_time"), rs.getTimestamp("posts.edit_time"), votes, comments));
        }
        return posts;
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

    public void bookmarkPost(Post post) {
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
        if(!this.active || subcreddit == null || subcreddit.GetSubId() <= 0 || subcreddit.GetCreator().equals(this))
            return;
        String sql = "DELETE FROM subcreddit_members WHERE (user_id = ? AND subcreddit_id = ?)";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        stmt.setInt(2, subcreddit.GetSubId());
        stmt.executeUpdate();
    }

    public boolean isMember(Subcreddit sub) throws SQLException {
        if(!this.active || sub == null || sub.GetSubId() <= 0)
            return false;
        String sql = "SELECT * FROM subcreddit_members WHERE (user_id = ? AND subcreddit_id = ?)";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        stmt.setInt(2, sub.GetSubId());
        ResultSet rs = stmt.executeQuery();
        return rs.next();
    }


    public void updateSubcreddit(Subcreddit subcreddit) throws SQLException {
        if(subcreddit.GetCreator().equals(this))
            subcreddit.update();
    }

    public void deleteSubcreddit(Subcreddit subcreddit) throws SQLException {
        if(subcreddit.GetCreator().equals(this) || this instanceof Admin)
            subcreddit.delete();
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

    public ArrayList<Subcreddit> GetOwnedSubcreddits() throws SQLException {
        ArrayList<Subcreddit> subcreddits = new ArrayList<>();

        String sql = "SELECT * FROM subcreddits WHERE creator_id = ? ORDER BY create_time DESC";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            subcreddits.add(new Subcreddit(rs.getInt("id"), rs.getString("name"), rs.getString("description"),
                    rs.getTimestamp("create_time"), this, new Media(MediaType.IMAGE, rs.getString("logo")),
                    rs.getInt("private") == 1));
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

    public void acceptFriend(User friend) throws SQLException {
        if(!this.active)
            return;
        String sql = "UPDATE followers SET accepted = 1 WHERE (follower_id = ? AND followed_id = ?)";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, friend.id); stmt.setInt(2, this.id);
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
                    rs.getString("content"), new Media(MediaType.from(rs.getString("media_type")), rs.getString("media_url")),
                    rs.getTimestamp("create_time"), rs.getTimestamp("edit_time"), rs.getInt("read") != 0
            ));
        }
        return messages;
    }

    //TODO: This function might be useless now
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
                    rs.getString("content"), new Media(MediaType.from(rs.getString("media_type")), rs.getString("media_url")),
                    rs.getTimestamp("create_time"), rs.getTimestamp("edit_time"), rs.getInt("read") != 0
            ));
        }
        return messages;
    }

    public ArrayList<Message> GetUnreadPrivateMessages() throws SQLException {
        ArrayList<Message> messages = new ArrayList<>();
        String sql = "SELECT * FROM messages ORDER BY id DESC WHERE receiver_id = ? AND read = 0";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(3, this.id);
        ResultSet rs = stmt.executeQuery();
        while(rs.next()) {
            messages.add(new Message(rs.getInt("id"), Database.GetUser(rs.getInt("sender_id")), this,
                    rs.getString("content"), new Media(MediaType.from(rs.getString("media_type")), rs.getString("media_url")),
                    rs.getTimestamp("create_time"), rs.getTimestamp("edit_time"), rs.getInt("read") != 0
            ));
        }
        return messages;
    }

    public void ReadMessages(User friend) throws SQLException {
        int id1 = this.id;
        int id2 = friend.id;
        String sql = "UPDATE messages SET read = 1 WHERE (sender_id = ? AND receiver_id = ?)";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, id2); stmt.setInt(2, id1);
        stmt.executeUpdate();
    }

    public void addReport(Report report) throws SQLException {
        report.SubmitReport();
    }

    public void vote(Voteable voteable, int voteValue) throws SQLException { // voteValue -> {1: upvote, -1: downvote, 0: remove vote}
        if(!this.active || voteable == null || (voteValue != -1 && voteValue != 1 && voteValue != 0))
            return;
        if (voteable instanceof Post) {
            Post post = (Post) voteable;
            post.updateVotes(this, voteValue);
        }
        else if(voteable instanceof Comment) {
            Comment comment = (Comment) voteable;
            comment.updateVote(this, voteValue);
        }
    }

    public int CheckVote(Voteable voteable) throws SQLException {
        if(voteable instanceof Post) {
            Post post = (Post) voteable;
            String sql = "SELECT * FROM votes_posts WHERE (user_id = ? AND post_id = ?)";
            PreparedStatement stmt = Database.PrepareStatement(sql);
            stmt.setInt(1, this.id);
            stmt.setInt(2, post.GetID());
            ResultSet rs = stmt.executeQuery();
            if(rs.next()) {
                return rs.getInt("value");
            }
        }
        else if(voteable instanceof Comment) {
            Comment comment = (Comment) voteable;
            String sql = "SELECT * FROM votes_comments WHERE (user_id = ? AND comment_id = ?)";
            PreparedStatement stmt = Database.PrepareStatement(sql);
            stmt.setInt(1, this.id);
            stmt.setInt(2, comment.getID());
            ResultSet rs = stmt.executeQuery();
            if(rs.next()) {
                return rs.getInt("value");
            }
        }
        return 0;
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
    public boolean getActive() {return this.active;}

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof User) {
            return this.id == ((User) obj).id;
        }
        return false;
    }
}
