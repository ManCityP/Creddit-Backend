package com.crdt;

import com.crdt.users.Admin;
import com.crdt.users.Gender;
import com.crdt.users.User;
import de.mkammerer.argon2.*;

import java.sql.*;
import java.util.*;

public abstract class Database {
    private static Connection conn;
    private static final Argon2Advanced ARGON2 = Argon2Factory.createAdvanced(Argon2Factory.Argon2Types.ARGON2id);

    public static void Connect(String url, String user, String pass) throws SQLException {
        conn = DriverManager.getConnection(url, user, pass);
    }

    public static void CloseConnection() {
        try {
            if (conn != null)
                conn.close();
            System.out.println("Successfully disconnected from database!");
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public static PreparedStatement PrepareStatement(String sql) throws SQLException {  return conn.prepareStatement(sql);  }

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



    // TODO: MOVE A LOT OF THESE FUNCTIONS TO THEIR RESPECTIVE CLASSES!!!

    // BOOKMARK: Posts
    public static void InsertPost(Post p) throws SQLException {
        for(String category : p.getCategories()) {
            int categoryID = CategoryExists(category);
            if(categoryID == 0)
                categoryID = InsertCategory(category.toLowerCase());
            String sql = "INSERT INTO post_categories (post_id, category_id) VALUES (?, ?)";
            try(PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, p.GetID());
                stmt.setInt(2, categoryID);
                stmt.executeUpdate();
            }
        }

        String sql = "INSERT INTO posts (author_id, subcreddit_id, title, content) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, p.GetAuthor().GetID());
            stmt.setInt(2, p.GetSubcreddit().GetID());
            stmt.setString(3, p.GetTitle());
            stmt.setString(4, p.GetContent());
            stmt.executeUpdate();
        }
    }

    public static int InsertCategory(String category) throws SQLException {
        String sql = "INSERT INTO categories (name) VALUES (?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, category);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next())
                    return rs.getInt("id");
            }
        }
        return 0;
    }

    public static ArrayList<Post> GetAllPosts() throws SQLException {
        ArrayList<Post> posts = new ArrayList<>();
        String sql = "SELECT * FROM posts ORDER BY id DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {

                int postid = rs.getInt("id");
                ArrayList<Media> media = new ArrayList<>();
                String sql2 = "SELECT * FROM post_media ORDER BY id ASC WHERE (post_id = " + postid + ")";
                try (PreparedStatement stmt2 = conn.prepareStatement(sql2);
                     ResultSet rs2 = stmt2.executeQuery()) {
                    while (rs.next()) {
                        media.add(new Media(MediaType.toMediaType(rs.getString("media_type")), rs.getString("media_url")));
                    }
                }

                int votes = 0;
                String sql3 = "SELECT * FROM votes_posts WHERE (post_id = " + postid + ")";
                try (PreparedStatement stmt3 = conn.prepareStatement(sql3);
                     ResultSet rs3 = stmt3.executeQuery()) {
                    while(rs3.next()) {
                        votes += (rs3.getString("value").equalsIgnoreCase("Up")? 1 : -1);
                    }
                }

                Post p = new Post(postid, rs.getInt("author_id"), GetSubcreddit(rs.getInt("subcreddit_id")),
                        rs.getString("title"), rs.getString("content"), media, GetPostCategories(postid),
                        rs.getTimestamp("create_time"), rs.getTimestamp("edit_time"), votes);
                posts.add(p);
            }
        }
        return posts;
    }

    public static Post GetPost(int postid) throws SQLException {
        if(postid <= 0)
            return null;

        String sql = "SELECT * FROM posts WHERE (id = " + postid + ")";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {

                ArrayList<Media> media = new ArrayList<>();
                String sql2 = "SELECT * FROM post_media ORDER BY id ASC WHERE (post_id = " + postid + ")";
                try (PreparedStatement stmt2 = conn.prepareStatement(sql2);
                     ResultSet rs2 = stmt2.executeQuery()) {
                    while (rs.next()) {
                        media.add(new Media(MediaType.toMediaType(rs.getString("media_type")), rs.getString("media_url")));
                    }
                }

                int votes = 0;
                String sql3 = "SELECT * FROM votes_posts WHERE (post_id = " + postid + ")";
                try (PreparedStatement stmt3 = conn.prepareStatement(sql3);
                     ResultSet rs3 = stmt3.executeQuery()) {
                    while(rs3.next()) {
                        votes += (rs3.getString("value").equalsIgnoreCase("Up")? 1 : -1);
                    }
                }

                return new Post(postid, rs.getInt("author_id"), GetSubcreddit(rs.getInt("subcreddit_id")),
                        rs.getString("title"), rs.getString("content"), media, GetPostCategories(postid),
                        rs.getTimestamp("create_time"), rs.getTimestamp("edit_time"), votes);
            }
        }
        return null;
    }

    public static ArrayList<String> GetPostCategories(int postID) throws SQLException {
        ArrayList<String> categories = new ArrayList<>();
        String sql = "SELECT * FROM post_categories WHERE (post_id = " + postID + ")";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                categories.add(GetCategory(rs.getInt("category_id")));
            }
        }
        return categories;
    }

    public static ArrayList<String> GetAllCategories() throws SQLException {
        ArrayList<String> categories = new ArrayList<>();
        String sql = "SELECT * FROM categories ORDER BY name ASC";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                categories.add(rs.getString("name"));
            }
        }
        return categories;
    }

    public static String GetCategory(int categoryID) throws SQLException {
        if(categoryID <= 0)
            return null;

        String sql = "SELECT * FROM categories WHERE (id = " + categoryID + ")";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getString("name");
            }
        }
        return null;
    }

    public static int CategoryExists(String categoryName) throws SQLException {
        String sql = "SELECT * FROM categories WHERE (name = " + categoryName.toLowerCase() + ")";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("id");
            }
        }
        return 0;
    }




    // BOOKMARK: Users
    public static void InsertUser(User user) throws SQLException {
        String sql;
        if(user instanceof Admin)
            sql = "INSERT INTO posts (username, email, password_hash, gender, bio, pfp, admin) VALUES (?, ?, ?, ?, ?, ?, ?)";
        else
            sql = "INSERT INTO posts (username, email, password_hash, gender, bio, pfp) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, HashPassword(user.getPassword()));
            stmt.setString(4, user.getGender().toString());
            stmt.setString(5, user.getBio());
            stmt.setString(6, user.getPfp().GetURL());
            if(user instanceof Admin)
                stmt.setInt(7, 1);
            stmt.executeUpdate();
        }
    }

    public static void UpdateUser(User user) throws SQLException {
        String sql = "UPDATE users SET username = ?, password_hash = ?, bio = ?, pfp = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getUsername());
            stmt.setString(2, HashPassword(user.getPassword()));
            stmt.setString(3, user.getBio());
            stmt.setString(4, user.getPfp().GetURL());
            stmt.setInt(5, user.getId());
            stmt.executeUpdate();
        }
    }

    public static void SendFriendRequest(User sender, User receiver) throws SQLException {
        int senderId = sender.getId();
        int receiverId = receiver.getId();
        if(GetFriends(sender).contains(receiver) || GetSentFriendRequests(sender).contains(receiver) || GetReceivedFriendRequests(sender).contains(receiver))
            return;

        String sql = "INSERT INTO followers (follower_id, followed_id) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, senderId);
            stmt.setInt(2, receiverId);
            stmt.executeUpdate();
        }
    }

    public static ArrayList<User> GetAllUsers() throws SQLException {
        ArrayList<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY id DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                if(rs.getInt("admin") == 1)
                    users.add(new Admin(rs.getInt("id"), rs.getString("username"), rs.getString("email"), rs.getString("password_hash"),
                            Gender.toGender(rs.getString("gender")), rs.getString("bio"), new Media(MediaType.IMAGE, rs.getString("pfp")),
                            rs.getTimestamp("create_time")));
                else
                    users.add(new User(rs.getInt("id"), rs.getString("username"), rs.getString("email"), rs.getString("password_hash"),
                            Gender.toGender(rs.getString("gender")), rs.getString("bio"), new Media(MediaType.IMAGE, rs.getString("pfp")),
                            rs.getTimestamp("create_time")));
            }
        }
        return users;
    }

    public static User GetUser(int id) throws SQLException {
        String sql = "SELECT * FROM users WHERE (id = " + id + ")";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                if(rs.getInt("admin") == 1)
                    return new Admin(rs.getInt("id"), rs.getString("username"), rs.getString("email"), rs.getString("password_hash"),
                            Gender.toGender(rs.getString("gender")), rs.getString("bio"), new Media(MediaType.IMAGE, rs.getString("pfp")),
                            rs.getTimestamp("create_time"));

                return new User(rs.getInt("id"), rs.getString("username"), rs.getString("email"), rs.getString("password_hash"),
                        Gender.toGender(rs.getString("gender")), rs.getString("bio"), new Media(MediaType.IMAGE, rs.getString("pfp")),
                        rs.getTimestamp("create_time"));
            }
        }
        return null;
    }

    public static ArrayList<User> GetFriends(User user) throws SQLException {
        ArrayList<User> friends = new ArrayList<>();
        int id = user.getId();
        String sql = "SELECT * FROM followers WHERE accepted = 1 AND (follower_id = " + id + " OR followed_id = " + id + ") ORDER BY create_time DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int follower_id = rs.getInt("follower_id");
                friends.add(GetUser(follower_id == id? rs.getInt("followed_id") : follower_id));
            }
        }
        return friends;
    }

    public static ArrayList<User> GetSentFriendRequests(User user) throws SQLException {
        ArrayList<User> friends = new ArrayList<>();
        String sql = "SELECT * FROM followers WHERE accepted = 0 AND (follower_id = " + user.getId() + ") ORDER BY create_time DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                friends.add(GetUser(rs.getInt("followed_id")));
            }
        }
        return friends;
    }

    public static ArrayList<User> GetReceivedFriendRequests(User user) throws SQLException {
        ArrayList<User> friends = new ArrayList<>();
        String sql = "SELECT * FROM followers WHERE accepted = 0 AND (followed_id = " + user.getId() + ") ORDER BY create_time DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                friends.add(GetUser(rs.getInt("follower_id")));
            }
        }
        return friends;
    }

    public static void InsertMessage(Message msg) throws SQLException {
        String sql = "INSERT INTO messages (sender_id, receiver_id, content, media_url, media_type) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, msg.GetSender().getID());
            stmt.setInt(2, msg.GetReceiver().getID());
            stmt.setString(3, msg.GetText());
            stmt.setString(4, msg.GetMedia().GetURL());
            stmt.setString(5, msg.GetMedia().GetType().toString());
            stmt.executeUpdate();
        }
    }

    public static void EditMessage(Message msg) throws SQLException {
        String sql = "UPDATE messages SET content = ?, media_url = ?, media_type = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, msg.GetText());
            stmt.setString(2, msg.GetMedia().GetURL());
            stmt.setString(3, msg.GetMedia().GetType().toString());
            stmt.setInt(4, msg.GetID());
            stmt.executeUpdate();
        }
    }

    public static ArrayList<Message> GetPrivateMessageFeed(User u1, User u2, int lastMessageID) throws SQLException {
        ArrayList<Message> messages = new ArrayList<>();
        int id1 = u1.getId();
        int id2 = u2.getId();
        String sql;
        if(lastMessageID > 0)
            sql = "SELECT * FROM messages ORDER BY id DESC WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?) AND id < ? LIMIT 20";
        else
            sql = "SELECT * FROM messages ORDER BY id DESC WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?) LIMIT 20";
        try(PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id1); stmt.setInt(2, id2);
            stmt.setInt(3, id2); stmt.setInt(4, id1);
            if(lastMessageID > 0)
                stmt.setInt(5, lastMessageID);
            ResultSet rs = stmt.executeQuery();
            while(rs.next()) {
                int sender_id = rs.getInt("sender_id");
                messages.add(new Message(rs.getInt("id"), sender_id == id1? u1 : u2, sender_id == id1? u2 : u1,
                        rs.getString("content"), new Media(MediaType.toMediaType(rs.getString("media_type")), rs.getString("media_url")),
                        rs.getTimestamp("create_time"), rs.getTimestamp("edit_time"), rs.getInt("read") == 0? false : true
                ));
            }
        }
        return messages;
    }

    public static ArrayList<Message> GetLatestPrivateMessages(User u1, User u2, int lastMessageID) throws SQLException {
        ArrayList<Message> messages = new ArrayList<>();
        int id1 = u1.getId();
        int id2 = u2.getId();
        String sql = "SELECT * FROM messages ORDER BY id ASC WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?) AND id > ?";
        try(PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id1); stmt.setInt(2, id2);
            stmt.setInt(3, id2); stmt.setInt(4, id1);
            stmt.setInt(5, lastMessageID);
            ResultSet rs = stmt.executeQuery();
            while(rs.next()) {
                int sender_id = rs.getInt("sender_id");
                messages.add(new Message(rs.getInt("id"), sender_id == id1? u1 : u2, sender_id == id1? u2 : u1,
                        rs.getString("content"), new Media(MediaType.toMediaType(rs.getString("media_type")), rs.getString("media_url")),
                        rs.getTimestamp("create_time"), rs.getTimestamp("edit_time"), rs.getInt("read") == 0? false : true
                ));
            }
        }
        return messages;
    }





    // BOOKMARK: Comments
    public static void InsertComment(Comment c) throws SQLException {
        String sql = "INSERT INTO comments (post_id, author_id, parent_id, content, media_url, media_type) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, c.getPost().getID());
            stmt.setInt(2, c.getAuthor().getId());
            stmt.setInt(3, c.getParent().getID());
            stmt.setString(4,c.getContent());
            stmt.setString(5,c.getMedia().GetURL());
            stmt.setString(6,c.getMedia().GetType().toString());
            stmt.executeUpdate();
        }
    }

    public static ArrayList<Comment> GetAllComments(int postid) throws SQLException {
        ArrayList<Comment> comments = new ArrayList<>();
        String sql = "SELECT * FROM comments ORDER BY id DESC WHERE (post_id = " + postid + ")";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                int commentID = rs.getInt("id");
                int votes = 0;
                String sql2 = "SELECT * FROM votes_comments WHERE (comment_id = " + commentID + ")";
                try (PreparedStatement stmt2 = conn.prepareStatement(sql2);
                     ResultSet rs2 = stmt2.executeQuery()) {
                    while(rs2.next()) {
                        votes += (rs2.getString("value").equalsIgnoreCase("Up")? 1 : -1);
                    }
                }
                Comment p = new Comment(commentID, GetPost(rs.getInt("post_id")), GetUser(rs.getInt("author_id")), rs.getString("content"),
                        new Media(MediaType.toMediaType(rs.getString("media_type")), rs.getString("media_url")), GetComment(rs.getInt("parent_id")), votes,
                        rs.getTimestamp("create_time"), rs.getTimestamp("edit_time"));
                comments.add(p);
            }
        }
        return comments;
    }
    public static Comment GetComment(int commentid) throws SQLException {
        if(commentid <= 0)
            return null;

        String sql = "SELECT * FROM comments WHERE (id = " + commentid + ")";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                int votes = 0;
                String sql2 = "SELECT * FROM votes_comments WHERE (comment_id = " + commentid + ")";
                try (PreparedStatement stmt2 = conn.prepareStatement(sql2);
                     ResultSet rs2 = stmt2.executeQuery()) {
                    while(rs2.next()) {
                        votes += (rs2.getString("value").equalsIgnoreCase("Up")? 1 : -1);
                    }
                }
                return new Comment(commentid, GetPost(rs.getInt("post_id")), GetUser(rs.getInt("author_id")), rs.getString("content"),
                        new Media(MediaType.toMediaType(rs.getString("media_type")), rs.getString("media_url")), GetComment(rs.getInt("parent_id")), votes,
                        rs.getTimestamp("create_time"), rs.getTimestamp("edit_time"));
            }
        }
        return null;
    }



    // BOOKMARK: Subcreddits
    public static void InsertSubcreddit(Subcreddit sub) throws SQLException {
        String sql = "INSERT INTO subcreddits (name, description, creator_id, logo, private) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sub.getName());
            stmt.setString(2, sub.getDescription());
            stmt.setInt(3, sub.getCreator().getID());
            stmt.setInt(4, sub.getLogo().getURL());
            stmt.setInt(5, sub.getPrivate()? 1 : 0);
            stmt.executeUpdate();
        }
    }

    public static ArrayList<Subcreddit> GetAllSubcreddits() throws SQLException {
        ArrayList<Subcreddit> subcreddits = new ArrayList<>();
        String sql = "SELECT * FROM subcreddits ORDER BY id DESC";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                int subID = rs.getInt("id");
                ArrayList<User> members = new ArrayList<>();
                String sql2 = "SELECT * FROM subcreddit_members ORDER BY create_time ASC WHERE (subcreddit_id = " + subID + ")";
                try (PreparedStatement stmt2 = conn.prepareStatement(sql2);
                     ResultSet rs2 = stmt2.executeQuery()) {
                    while(rs2.next()) {
                        if(rs2.getInt("accepted") == 1) {
                            members.add(GetUser(rs2.getInt("user_id")));
                        }
                    }
                }
                Subcreddit p = new Subcreddit(subID, members, rs.getString("name"), rs.getString("description"),
                        rs.getTimestamp("create_time"), GetUser(rs.getInt("creator_id")), new Media(MediaType.IMAGE, rs.getString("logo")),
                        rs.getInt("private") == 1? true : false);
                subcreddits.add(p);
            }
        }
        return subcreddits;
    }

    public static Subcreddit GetSubcreddit(int subID) throws SQLException {
        if(subID <= 0)
            return null;

        String sql = "SELECT * FROM subcreddits WHERE (id = " + subID + ")";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                ArrayList<User> members = new ArrayList<>();
                String sql2 = "SELECT * FROM subcreddit_members ORDER BY create_time ASC WHERE (subcreddit_id = " + subID + ")";
                try (PreparedStatement stmt2 = conn.prepareStatement(sql2);
                     ResultSet rs2 = stmt2.executeQuery()) {
                    while(rs2.next()) {
                        if(rs2.getInt("accepted") == 1) {
                            members.add(GetUser(rs2.getInt("user_id")));
                        }
                    }
                }
                return new Subcreddit(subID, members, rs.getString("name"), rs.getString("description"),
                        rs.getTimestamp("create_time"), GetUser(rs.getInt("creator_id")), new Media(MediaType.IMAGE, rs.getString("logo")),
                        rs.getInt("private") == 1? true : false);
            }
        }
        return null;
    }

    public static ArrayList<User> GetSubcredditBannedMembers(int subID) throws SQLException {
        if(subID <= 0)
            return null;

        ArrayList<User> bannedMembers = new ArrayList<>();
        String sql = "SELECT * FROM bans ORDER BY id DESC WHERE (subcreddit_id = " + subID + ")";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                bannedMembers.add(GetUser(rs.getInt("user_id")));
            }
        }
        return bannedMembers;
    }
}