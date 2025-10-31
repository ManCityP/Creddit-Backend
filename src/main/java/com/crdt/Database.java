package com.crdt;

import com.crdt.users.Gender;
import com.crdt.users.User;
import de.mkammerer.argon2.*;

import java.sql.*;
import java.util.*;

public class Database {
    private final Connection conn;
    private static final Argon2Advanced ARGON2 = Argon2Factory.createAdvanced(Argon2Factory.Argon2Types.ARGON2id);

    public Database(String url, String user, String pass) throws SQLException {
        conn = DriverManager.getConnection(url, user, pass);
    }

    public void CloseConnection() {
        try {
            if (conn != null)
                conn.close();
            System.out.println("Successfully disconnected from database!");
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public ResultSet GetAny(String query) throws Exception {
        Statement stmt = conn.createStatement();
        return stmt.executeQuery(query);
    }

    public void Execute(String sql) throws Exception {
        PreparedStatement insertStatement = conn.prepareStatement(sql);
        insertStatement.executeUpdate();
    }

    private String HashPassword(String password) {
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



    // BOOKMARK: Posts
    public void InsertPost(Post p) throws SQLException {
        String sql = "INSERT INTO posts (author_id, subcreddit_id, title, content) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, p.userID);
            stmt.setInt(2, p.getSubcreddit().getId());
            stmt.setString(3, p.title);
            stmt.setString(4, p.content);
            stmt.executeUpdate();
        }
    }

    public ArrayList<Post> GetAllPosts() throws SQLException {
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

                Post p = new Post(postid, rs.getInt("author_id"), GetSubcreddit(rs.getString("subcreddit_id")),
                        rs.getString("title"), rs.getString("content"), media,
                        rs.getTimestamp("create_time"), rs.getTimestamp("edit_time"), votes);
                posts.add(p);
            }
        }
        return posts;
    }

    public Post GetPost(int postid) throws SQLException {
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

                return new Post(postid, rs.getInt("author_id"), GetSubcreddit(rs.getString("subcreddit_id")),
                        rs.getString("title"), rs.getString("content"), media,
                        rs.getTimestamp("create_time"), rs.getTimestamp("edit_time"), votes);
            }
        }
        return null;
    }





    // BOOKMARK: Users
    public void InsertUser(User user) throws SQLException {
        String password_hash = HashPassword(user.getPassword());

        String sql = "INSERT INTO posts (username, email, password_hash, gender, bio, pfp) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, password_hash);
            stmt.setString(4, user.getGender().toString());
            stmt.setString(5, user.getBio());
            stmt.setString(6, user.getPfp().GetURL());
            stmt.executeUpdate();
        }
    }

    public ArrayList<User> GetAllUsers() throws SQLException {
        ArrayList<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY id DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                User u = new User(rs.getInt("id"), rs.getString("username"), rs.getString("email"), rs.getString("password_hash"),
                        Gender.toGender(rs.getString("gender")), rs.getString("bio"), new Media(MediaType.IMAGE, rs.getString("pfp")),
                        rs.getTimestamp("create_time"));
                users.add(u);
            }
        }
        return users;
    }

    public User GetUser(int id) throws SQLException {
        String sql = "SELECT * FROM users WHERE (id = " + id + ")";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new User(rs.getInt("id"), rs.getString("username"), rs.getString("email"), rs.getString("password_hash"),
                        Gender.toGender(rs.getString("gender")), rs.getString("bio"), new Media(MediaType.IMAGE, rs.getString("pfp")),
                        rs.getTimestamp("create_time"));
            }
        }
        return null;
    }




    // BOOKMARK: Comments
    public void InsertComment(Comment c) throws SQLException {
        String sql = "INSERT INTO comments (post_id, author_id, parent_id, content, media_url, media_type) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, c.getPost().getID());
            stmt.setInt(2, c.getAuthor().getId());
            stmt.setInt(3, c.getParent().getID());
            stmt.setString(4,c.getContent());
            stmt.setString(5,c.getMedia_url());
            stmt.setString(6,c.getMedia_type());
            stmt.executeUpdate();
        }
    }

    public ArrayList<Comment> GetAllComments(int postid) throws SQLException {
        ArrayList<Comment> comments = new ArrayList<>();
        String sql = "SELECT * FROM comments ORDER BY id DESC WHERE (post_id = " + postid + ")";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                int votes = 0;
                String sql2 = "SELECT * FROM votes_comments WHERE (comment_id = " + rs.getInt("id") + ")";
                try (PreparedStatement stmt2 = conn.prepareStatement(sql2);
                     ResultSet rs2 = stmt2.executeQuery()) {
                    while(rs2.next()) {
                        votes += (rs2.getString("value").equalsIgnoreCase("Up")? 1 : -1);
                    }
                }
                Comment p = new Comment(rs.getInt("id"), GetPost(rs.getInt("post_id")), GetUser(rs.getInt("author_id")), rs.getString("content"),
                        new Media(MediaType.toMediaType(rs.getString("media_type")), rs.getString("media_url")), GetComment(rs.getInt("parent_id")), votes,
                        rs.getTimestamp("create_time"), rs.getTimestamp("edit_time"));
                comments.add(p);
            }
        }
        return comments;
    }
    public Comment GetComment(int commentid) throws SQLException {
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
}