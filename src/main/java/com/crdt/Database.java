package com.crdt;

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

    public void InsertPost(Post p) throws SQLException {
        String sql = "INSERT INTO posts (id, author_id, subcreddit_id, title, content, create_time, edit_time) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, p.id);
            stmt.setInt(2, p.userID);
            stmt.setInt(3, p.getSubcreddit().getId());
            stmt.setString(4, p.title);
            stmt.setString(5, p.content);
            stmt.setTimestamp(6, p.created);
            stmt.setTimestamp(7, p.edited);
            stmt.executeUpdate();
        }
    }

    public void InsertUser(User user) throws SQLException {
        String password_hash = HashPassword(user.getPassword());

        String sql = "INSERT INTO posts (username, email, password_hash, gender, bio, pfp) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, password_hash);
            stmt.setString(4, user.getGender().toString());
            stmt.setString(5, user.getBio());
            stmt.setString(6, user.getPfp().getMediaURL());
            stmt.executeUpdate();
        }
    }



    public List<Post> GetAllPosts() throws SQLException {
        List<Post> posts = new ArrayList<>();
        String sql = "SELECT * FROM posts ORDER BY id DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Post p = new Post(rs.getInt("id"), rs.getInt("userid"), rs.getString("title"),
                        rs.getString("content"), rs.getString("media_url"), rs.getString("media_type"),
                        rs.getTimestamp("created"), rs.getTimestamp("edited"));
                posts.add(p);
            }
        }
        return posts;
    }
}