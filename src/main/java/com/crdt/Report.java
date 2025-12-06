package com.crdt;

import com.crdt.users.Admin;
import com.crdt.users.Moderator;
import com.crdt.users.User;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

public class Report {

    private int id;
    private User reporter;
    private Reportable target;
    private String reason;
    private ReportType type;
    private ReportStatus status;
    private Timestamp timeReported;

    public Report(int id, User reporter, Reportable target, String reason, ReportType type, ReportStatus status, Timestamp timeReported) {

        if(reporter == null) {
            throw new IllegalArgumentException("Reporter cannot be null.");
        }
        if (target == null) {
            throw new IllegalArgumentException("Report target cannot be null.");
        }
        if (reason == null) {
            throw new IllegalArgumentException("Reason cannot be empty.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Report type cannot be null.");
        }
        if (status == null) {
            throw new IllegalArgumentException("Report status cannot be null.");
        }

        this.id = id;
        this.reporter = reporter;
        this.target = target;
        this.reason = reason;
        this.type = type;
        this.status = status;
        this.timeReported = timeReported;
    }


    public void SubmitReport() throws SQLException {
        if(target instanceof User) {
            String sql = "INSERT INTO reports_users (reporter_id, reported_user_id, reason, type) VALUES (?, ?, ?, ?)";
            PreparedStatement stmt = Database.PrepareStatement(sql);
            stmt.setInt(1, reporter.getId());
            stmt.setInt(2, ((User)target).getId());
            stmt.setString(3, reason);
            stmt.setString(4, type.toString());
            stmt.executeUpdate();
            return;
        }
        if(target instanceof Post) {
            String sql = "INSERT INTO reports_posts (reporter_id, reported_post_id, reason, type) VALUES (?, ?, ?, ?)";
            PreparedStatement stmt = Database.PrepareStatement(sql);
            stmt.setInt(1, reporter.getId());
            stmt.setInt(2, ((Post)target).GetID());
            stmt.setString(3, reason);
            stmt.setString(4, type.toString());
            stmt.executeUpdate();
            return;
        }
        if(target instanceof Comment) {
            String sql = "INSERT INTO reports_comments (reporter_id, reported_comment_id, reason, type) VALUES (?, ?, ?, ?)";
            PreparedStatement stmt = Database.PrepareStatement(sql);
            stmt.setInt(1, reporter.getId());
            stmt.setInt(2, ((Comment)target).getID());
            stmt.setString(3, reason);
            stmt.setString(4, type.toString());
            stmt.executeUpdate();
            return;
        }
    }

    public static ArrayList<Report> GetUserReportFeed(Admin admin, int lastID) throws SQLException {
        if(admin == null)
            return null;
        ArrayList<Report> reportsFeed = new ArrayList<>();

        String sql;
        if(lastID > 0)
            sql = "SELECT * FROM reports_users ORDER BY id DESC WHERE id < ? AND status = 'Pending' LIMIT 10";
        else
            sql = "SELECT * FROM reports_users WHERE status = 'Pending' ORDER BY id DESC LIMIT 10";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        if(lastID > 0)
            stmt.setInt(1, lastID);
        ResultSet rs = stmt.executeQuery();
        while(rs.next()) {
            reportsFeed.add(new Report(rs.getInt("id"), Database.GetUser(rs.getInt("reporter_id")),
                    Database.GetUser(rs.getInt("reported_user_id")), rs.getString("reason"),
                    ReportType.from(rs.getString("type")), ReportStatus.from(rs.getString("status")),
                    rs.getTimestamp("create_time")));
        }
        return reportsFeed;
    }

    public static ArrayList<Report> GetPostReportFeed(User user, Subcreddit subcreddit, int lastID) throws SQLException {
        if(user == null)
            return null;
        ArrayList<Report> reportsFeed = new ArrayList<>();

        String sql;
        PreparedStatement stmt = null;
        if(user instanceof Admin) {
            if (lastID > 0)
                sql = "SELECT * FROM reports_posts ORDER BY id DESC WHERE id < ? AND status = 'Pending' LIMIT 10";
            else
                sql = "SELECT * FROM reports_posts WHERE status = 'Pending' ORDER BY id DESC LIMIT 10";
            stmt = Database.PrepareStatement(sql);
            if (lastID > 0)
                stmt.setInt(1, lastID);
        }
        else if(user instanceof Moderator) {
            if (lastID > 0)
                sql = "SELECT reports_posts.reported_post_id, posts.subcreddit_id, reports_posts.id, reports_posts.status FROM reports_posts " +
                        "JOIN posts ON reports_posts.reported_post_id = posts.id WHERE posts.subcreddit_id = ? AND reports_posts.id < ? AND status = 'Pending' " +
                        "ORDER BY reports_posts.id DESC LIMIT 10";
            else
                sql = "SELECT reports_posts.reported_post_id, posts.subcreddit_id, reports_posts.id, reports_posts.status FROM reports_posts " +
                        "JOIN posts ON reports_posts.reported_post_id = posts.id WHERE posts.subcreddit_id = ? AND status = 'Pending' " +
                        "ORDER BY reports_posts.id DESC LIMIT 10";
            stmt = Database.PrepareStatement(sql);
            stmt.setInt(1, subcreddit.GetSubId());
            if (lastID > 0)
                stmt.setInt(2, lastID);
        }
        if(stmt == null)
            return null;
        ResultSet rs = stmt.executeQuery();
        while(rs.next()) {
            reportsFeed.add(new Report(rs.getInt("id"), Database.GetUser(rs.getInt("reporter_id")),
                    Database.GetPost(rs.getInt("reported_post_id")), rs.getString("reason"),
                    ReportType.from(rs.getString("type")), ReportStatus.from(rs.getString("status")),
                    rs.getTimestamp("create_time")));
        }
        return reportsFeed;
    }

    public static ArrayList<Report> GetCommentReportFeed(User user, Subcreddit subcreddit, int lastID) throws SQLException {
        if(user == null)
            return null;
        ArrayList<Report> reportsFeed = new ArrayList<>();

        String sql;
        PreparedStatement stmt = null;
        if(user instanceof Admin) {
            if (lastID > 0)
                sql = "SELECT * FROM reports_comments ORDER BY id DESC WHERE id < ? AND status = 'Pending' LIMIT 10";
            else
                sql = "SELECT * FROM reports_comments WHERE status = 'Pending' ORDER BY id DESC LIMIT 10";
            stmt = Database.PrepareStatement(sql);
            if (lastID > 0)
                stmt.setInt(1, lastID);
        }
        else if(user instanceof Moderator) {
            if (lastID > 0)
                sql = "SELECT reports_comments.reported_comment_id, comments.post_id, posts.subcreddit_id, reports_comments.id, reports_comments.status FROM reports_comments " +
                        "JOIN comments ON reports_comments.reported_comment_id = comments.id JOIN posts ON comments.post_id = posts.id" +
                        "WHERE posts.subcreddit_id = ? AND reports_posts.id < ? AND status = 'Pending' " +
                        "ORDER BY reports_posts.id DESC LIMIT 10";
            else
                sql = "SELECT reports_comments.reported_comment_id, comments.post_id, posts.subcreddit_id, reports_comments.id, reports_comments.status FROM reports_comments " +
                        "JOIN comments ON reports_comments.reported_comment_id = comments.id JOIN posts ON comments.post_id = posts.id" +
                        "WHERE posts.subcreddit_id = ? AND status = 'Pending' " +
                        "ORDER BY reports_posts.id DESC LIMIT 10";
            stmt = Database.PrepareStatement(sql);
            stmt.setInt(1, subcreddit.GetSubId());
            if (lastID > 0)
                stmt.setInt(2, lastID);
        }
        if(stmt == null)
            return null;
        ResultSet rs = stmt.executeQuery();
        while(rs.next()) {
            reportsFeed.add(new Report(rs.getInt("id"), Database.GetUser(rs.getInt("reporter_id")),
                    Database.GetComment(rs.getInt("reported_comment_id")), rs.getString("reason"),
                    ReportType.from(rs.getString("type")), ReportStatus.from(rs.getString("status")),
                    rs.getTimestamp("create_time")));
        }
        return reportsFeed;
    }

    public void Resolve() throws SQLException {
        String tableName = "";
        if(target instanceof User) {
            tableName = "reports_users";
        }
        if(target instanceof Post) {
            tableName = "reports_posts";
        }
        if(target instanceof Comment) {
            tableName = "reports_comments";
        }
        String sql = "UPDATE " + tableName + " SET status = 'Dismissed' WHERE id = ?";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        stmt.executeUpdate();
    }

    public void Dismiss() throws SQLException {
        String tableName = "";
        if(target instanceof User) {
            tableName = "reports_users";
        }
        if(target instanceof Post) {
            tableName = "reports_posts";
        }
        if(target instanceof Comment) {
            tableName = "reports_comments";
        }
        String sql = "UPDATE " + tableName + " SET status = 'Dismissed' WHERE id = ?";
        PreparedStatement stmt = Database.PrepareStatement(sql);
        stmt.setInt(1, this.id);
        stmt.executeUpdate();
    }


    public int getId() {
        return id;
    }


    public Reportable getTarget() {
        return target;
    }

    public String getReason() {
        return reason;
    }


    public ReportType getType() {
        return type;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public Timestamp getTimeReported() {
        return timeReported;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Report) {
            return this.id == ((Report) obj).id;
        }
        return false;
    }

}