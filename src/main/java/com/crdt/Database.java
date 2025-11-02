package com.crdt;

import com.crdt.users.Admin;
import com.crdt.users.Gender;
import com.crdt.users.User;
import de.mkammerer.argon2.*;

import java.sql.*;
import java.util.*;

public abstract class Database {
    private static Connection conn;

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


    // TODO: MOVE A LOT OF THESE FUNCTIONS TO THEIR RESPECTIVE CLASSES!!!

    // BOOKMARK: Posts
    public static int InsertCategory(String category) throws SQLException {
        String sql = "INSERT INTO categories (name) VALUES (?)";
        try (PreparedStatement stmt = PrepareStatement(sql)) {
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
        try (PreparedStatement stmt = PrepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {

                int postid = rs.getInt("id");
                ArrayList<Media> media = new ArrayList<>();
                String sql2 = "SELECT * FROM post_media ORDER BY id ASC WHERE (post_id = " + postid + ")";
                try (PreparedStatement stmt2 = PrepareStatement(sql2);
                     ResultSet rs2 = stmt2.executeQuery()) {
                    while (rs.next()) {
                        media.add(new Media(MediaType.toMediaType(rs.getString("media_type")), rs.getString("media_url")));
                    }
                }

                int votes = 0;
                String sql3 = "SELECT * FROM votes_posts WHERE (post_id = " + postid + ")";
                try (PreparedStatement stmt3 = PrepareStatement(sql3);
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
        try (PreparedStatement stmt = PrepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {

                ArrayList<Media> media = new ArrayList<>();
                String sql2 = "SELECT * FROM post_media ORDER BY id ASC WHERE (post_id = " + postid + ")";
                try (PreparedStatement stmt2 = PrepareStatement(sql2);
                     ResultSet rs2 = stmt2.executeQuery()) {
                    while (rs.next()) {
                        media.add(new Media(MediaType.toMediaType(rs.getString("media_type")), rs.getString("media_url")));
                    }
                }

                int votes = 0;
                String sql3 = "SELECT * FROM votes_posts WHERE (post_id = " + postid + ")";
                try (PreparedStatement stmt3 = PrepareStatement(sql3);
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

    private static ArrayList<String> GetPostCategories(int postID) throws SQLException {
        ArrayList<String> categories = new ArrayList<>();
        String sql = "SELECT * FROM post_categories WHERE (post_id = " + postID + ")";
        try (PreparedStatement stmt = PrepareStatement(sql);
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
        try (PreparedStatement stmt = PrepareStatement(sql);
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
        try (PreparedStatement stmt = PrepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getString("name");
            }
        }
        return null;
    }

    public static int CategoryExists(String categoryName) throws SQLException {
        String sql = "SELECT * FROM categories WHERE (name = " + categoryName.toLowerCase() + ")";
        try (PreparedStatement stmt = PrepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("id");
            }
        }
        return 0;
    }




    // BOOKMARK: Users
    public static ArrayList<User> GetAllUsers() throws SQLException {
        ArrayList<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY id DESC";
        try (PreparedStatement stmt = PrepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                if(rs.getInt("admin") == 1)
                    users.add(new Admin(rs.getInt("id"), rs.getString("username"), rs.getString("email"), rs.getString("password_hash"),
                            Gender.toGender(rs.getString("gender")), rs.getString("bio"), new Media(MediaType.IMAGE, rs.getString("pfp")),
                            rs.getTimestamp("create_time")));
                else
                    users.add(new User(rs.getInt("id"), rs.getString("username"), rs.getString("email"), rs.getString("password_hash"),
                            Gender.toGender(rs.getString("gender")), rs.getString("bio"), new Media(MediaType.IMAGE, rs.getString("pfp")),
                            rs.getTimestamp("create_time"), rs.getInt("active") != 0));
            }
        }
        return users;
    }

    public static User GetUser(int id) throws SQLException {
        String sql = "SELECT * FROM users WHERE (id = " + id + ")";
        try (PreparedStatement stmt = PrepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                if(rs.getInt("admin") == 1)
                    return new Admin(rs.getInt("id"), rs.getString("username"), rs.getString("email"), rs.getString("password_hash"),
                            Gender.toGender(rs.getString("gender")), rs.getString("bio"), new Media(MediaType.IMAGE, rs.getString("pfp")),
                            rs.getTimestamp("create_time"));

                return new User(rs.getInt("id"), rs.getString("username"), rs.getString("email"), rs.getString("password_hash"),
                        Gender.toGender(rs.getString("gender")), rs.getString("bio"), new Media(MediaType.IMAGE, rs.getString("pfp")),
                        rs.getTimestamp("create_time"), rs.getInt("active") != 0);
            }
        }
        return null;
    }





    // BOOKMARK: Comments
    public static ArrayList<Comment> GetAllComments(int postid) throws SQLException {
        ArrayList<Comment> comments = new ArrayList<>();
        String sql = "SELECT * FROM comments ORDER BY id DESC WHERE (post_id = " + postid + ")";

        try (PreparedStatement stmt = PrepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                int commentID = rs.getInt("id");
                int votes = 0;
                String sql2 = "SELECT * FROM votes_comments WHERE (comment_id = " + commentID + ")";
                try (PreparedStatement stmt2 = PrepareStatement(sql2);
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

        try (PreparedStatement stmt = PrepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                int votes = 0;
                String sql2 = "SELECT * FROM votes_comments WHERE (comment_id = " + commentid + ")";
                try (PreparedStatement stmt2 = PrepareStatement(sql2);
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
    public static ArrayList<Subcreddit> GetAllSubcreddits() throws SQLException {
        ArrayList<Subcreddit> subcreddits = new ArrayList<>();
        String sql = "SELECT * FROM subcreddits ORDER BY id DESC";

        try (PreparedStatement stmt = PrepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Subcreddit p = new Subcreddit(rs.getInt("id"), rs.getString("name"), rs.getString("description"),
                        rs.getTimestamp("create_time"), GetUser(rs.getInt("creator_id")), new Media(MediaType.IMAGE, rs.getString("logo")),
                        rs.getInt("private") == 1);
                subcreddits.add(p);
            }
        }
        return subcreddits;
    }

    public static Subcreddit GetSubcreddit(int subID) throws SQLException {
        if(subID <= 0)
            return null;

        String sql = "SELECT * FROM subcreddits WHERE (id = " + subID + ")";

        try (PreparedStatement stmt = PrepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return new Subcreddit(subID, rs.getString("name"), rs.getString("description"),
                        rs.getTimestamp("create_time"), GetUser(rs.getInt("creator_id")), new Media(MediaType.IMAGE, rs.getString("logo")),
                        rs.getInt("private") == 1);
            }
        }
        return null;
    }
}