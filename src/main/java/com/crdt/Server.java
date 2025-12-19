package com.crdt;

import static spark.Spark.*;

import com.crdt.users.Admin;
import com.crdt.users.Gender;
import com.crdt.users.Moderator;
import com.crdt.users.User;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import javax.servlet.MultipartConfigElement;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Server {

    private static final String UPLOAD_DIR = System.getProperty("user.dir") + File.separator + "uploads";
    private static Gson gson;
    private static Process ngrokProcess;
    private static final ExecutorService THREAD_POOL = Executors.newFixedThreadPool(10);

    public static void main(String[] args) throws Exception {
        String tunnelURL = System.getenv("server_url");

        Database.Connect(
                System.getenv("db_url"),
                System.getenv("db_user"),
                System.getenv("pass")
        );

        try {
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/k", "start", "tunnel.bat");
            builder.redirectErrorStream(true);
            ngrokProcess = builder.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Database.CloseConnection();
                THREAD_POOL.shutdown();
                try {
                    Runtime.getRuntime().exec("taskkill /F /IM ngrok.exe /T");
                    if (!THREAD_POOL.awaitTermination(5, TimeUnit.SECONDS)) {
                        THREAD_POOL.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    THREAD_POOL.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }));
            Thread.sleep(100);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

        port(7878); // HTTP port
        Files.createDirectories(Paths.get(UPLOAD_DIR));
        //staticFiles.externalLocation(UPLOAD_DIR);
        System.out.println("Serving uploaded files from: " + UPLOAD_DIR);

        Type userListType = new TypeToken<ArrayList<User>>() {}.getType();
        Type commentMapType = new TypeToken<Map<Integer, ArrayList<Comment>>>() {}.getType();
        Type id_vote_type = new TypeToken<Map<Integer, Integer>>() {}.getType();

        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(7 * 60 * 60 * 1000 + 55 * 60 * 1000);
                    System.out.println("Restarting ngrok tunnel...");

                    // Kill old tunnel
                    if (ngrokProcess != null && ngrokProcess.isAlive()) {
                        Runtime.getRuntime().exec("taskkill /F /IM ngrok.exe /T");
                        Thread.sleep(3000); // Wait a bit to ensure shutdown
                    }

                    // Start new tunnel
                    ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/k", "start", "tunnel.bat");
                    builder.redirectErrorStream(true);
                    ngrokProcess = builder.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

        RuntimeTypeAdapterFactory<User> userAdapter =
                RuntimeTypeAdapterFactory.of(User.class, "type")
                        .registerSubtype(User.class, "user")
                        .registerSubtype(Moderator.class, "moderator")
                        .registerSubtype(Admin.class, "admin");
        RuntimeTypeAdapterFactory<Reportable> reportableAdapter =
                RuntimeTypeAdapterFactory.of(Reportable.class, "type")
                        .registerSubtype(User.class, "user")
                        .registerSubtype(Post.class, "post")
                        .registerSubtype(Comment.class, "comment");
        RuntimeTypeAdapterFactory<Voteable> voteableAdapter =
                RuntimeTypeAdapterFactory.of(Voteable.class, "type")
                        .registerSubtype(Post.class, "post")
                        .registerSubtype(Comment.class, "comment");

        gson = new GsonBuilder().registerTypeAdapterFactory(userAdapter).registerTypeAdapterFactory(reportableAdapter).registerTypeAdapterFactory(voteableAdapter).create();

        // Enable CORS (for future frontend use)
        before((req, res) -> {
            res.header("Access-Control-Allow-Origin", "*");
            res.header("Access-Control-Allow-Methods", "GET,POST,DELETE,PUT,OPTIONS");
            res.header("Access-Control-Allow-Headers", "Content-Type");
        });

        get("/ping", (req, res) -> {
            res.type("text/plain");
            return "yes";
        });




        // BOOKMARK: POST
        // Route: Create new post
        post("/post/create", (req, res) -> {
            try {
                Post post = gson.fromJson(req.body(), Post.class);
                int id = post.create();
                res.type("application/json");
                return gson.toJson(id, int.class);
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Edit Post
        post("/post/edit", (req, res) -> {
            try {
                Post post = gson.fromJson(req.body(), Post.class);
                post.update();
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Delete post
        post("/post/delete", (req, res) -> {
            try {
                Post post = gson.fromJson(req.body(), Post.class);
                post.delete();
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Insert Post view
        post("/post/view", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                User user = gson.fromJson(json.get("user"), User.class);
                Post post = gson.fromJson(json.get("post"), Post.class);
                user.viewPost(post);
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        //Route: Post/Comment vote by user
        post("/vote", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                User user = gson.fromJson(json.get("user"), User.class);
                Voteable voteable = gson.fromJson(json.get("voteable"), Voteable.class);
                int value = gson.fromJson(json.get("value"), int.class);

                user.vote(voteable, value);
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Get a specific post
        post("/post", (req, res) -> {
            JsonObject jsonBody = gson.fromJson(req.body(), JsonObject.class);
            User user = gson.fromJson(jsonBody.get("user"), User.class);
            int id = gson.fromJson(jsonBody.get("id"), int.class);
            Post post = Database.GetPost(id);
            int vote = (user == null? 0 : user.CheckVote(post));
            res.type("application/json");
            JsonObject json = new JsonObject();
            json.add("post", gson.toJsonTree(post, Post.class));
            json.addProperty("vote", gson.toJson(vote, int.class));
            return gson.toJson(json);
        });

        // Route: Get all categories
        get("/category/all", (req, res) -> {
            ArrayList<String> categories = Database.GetAllCategories();
            res.type("application/json");
            return gson.toJson(categories);
        });


        // BOOKMARK: USER
        // Route: Verify user
        get("/verify", (req, res) -> {
            String token = req.queryParams("token");
            System.out.println(token);
            if (Database.VerifyToken(token)) {
                return "Email successfully Verified!";
            } else {
                res.status(400);
                return "Invalid or expired token.";
            }
        });

        // Route: Create new user
        post("/user/register", (req, res) -> {
            try {
                User user = gson.fromJson(req.body(), User.class);
                int userID = user.register();

                THREAD_POOL.submit(() -> {
                    try {
                        SetupVerification(userID, user.getEmail());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Update user info
        post("/user/update", (req, res) -> {
            try {
                User user = gson.fromJson(req.body(), User.class);
                user.update();
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Update user info
        /*post("/user/delete", (req, res) -> {
            try {
                User user = gson.fromJson(req.body(), User.class);
                user.deactivate();
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });*/

        //Route: Check if a user is a member
        post("/user/ismember", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                User user = gson.fromJson(json.get("user"), User.class);
                Subcreddit sub = gson.fromJson(json.get("subcreddit"), Subcreddit.class);

                boolean isMember = user.isMember(sub);
                res.type("application/json");
                return gson.toJson(isMember, boolean.class);
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        //Route: Ban a user globally
        post("/user/ban", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                Admin admin = gson.fromJson(json.get("admin"), Admin.class);
                User user = gson.fromJson(json.get("user"), User.class);
                String reason = gson.fromJson(json.get("reason"), String.class);

                admin.DeactivateUser(user, reason);
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        //Route: Unban a user globally
        post("/user/unban", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                Admin admin = gson.fromJson(json.get("admin"), Admin.class);
                User user = gson.fromJson(json.get("user"), User.class);

                admin.ActivateUser(user);
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Send Friend Request
        post("/friends/send", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                User sender = gson.fromJson(json.get("sender"), User.class);
                User receiver = gson.fromJson(json.get("receiver"), User.class);

                sender.sendFriendRequest(receiver);
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Accept Friend Request
        post("/friends/accept", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                User sender = gson.fromJson(json.get("sender"), User.class);
                User receiver = gson.fromJson(json.get("receiver"), User.class);

                receiver.acceptFriend(sender);
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Unfriend
        post("/friends/remove", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                User user1 = gson.fromJson(json.get("user1"), User.class);
                User user2 = gson.fromJson(json.get("user2"), User.class);

                user1.unfriend(user2);
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Get user's friends
        post("/friends", (req, res) -> {
            User user = gson.fromJson(req.body(), User.class);
            ArrayList<User> users = user.GetFriends();
            res.type("application/json");
            return gson.toJson(users, userListType);
        });

        // Route: Get user's sent friend requests
        post("/friends/sent", (req, res) -> {
            User user = gson.fromJson(req.body(), User.class);
            ArrayList<User> users = user.GetSentFriendRequests();
            res.type("application/json");
            return gson.toJson(users, userListType);
        });

        // Route: Get user's received friend requests
        post("/friends/received", (req, res) -> {
            User user = gson.fromJson(req.body(), User.class);
            ArrayList<User> users = user.GetReceivedFriendRequests();
            res.type("application/json");
            return gson.toJson(users, userListType);
        });

        // Route: Get user's post feed
        post("/post/feed", (req, res) -> {
            JsonObject json = gson.fromJson(req.body(), JsonObject.class);
            User user = gson.fromJson(json.get("user"), User.class);
            String prompt = gson.fromJson(json.get("prompt"), String.class);
            int lastPostID = gson.fromJson(json.get("lastID"), int.class);
            ArrayList<Post> posts = new ArrayList<>();
            try {
                posts = User.GetPostFeed(user, prompt, lastPostID);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            ArrayList<Integer> myVotes = new ArrayList<>();
            for(Post post : posts) {
                if(user == null)
                    myVotes.add(0);
                else
                    myVotes.add(user.CheckVote(post));
            }
            JsonObject jsonObj = new JsonObject();
            jsonObj.add("posts", gson.toJsonTree(posts));
            jsonObj.add("votes", gson.toJsonTree(myVotes));
            res.type("application/json");
            return gson.toJson(jsonObj);
        });

        // Route: Get user's post feed - filter by subcreddit
        post("/post/feed/filter-sub", (req, res) -> {
            JsonObject json = gson.fromJson(req.body(), JsonObject.class);
            User user = gson.fromJson(json.get("user"), User.class);
            Subcreddit sub = gson.fromJson(json.get("sub"), Subcreddit.class);
            String prompt = gson.fromJson(json.get("prompt"), String.class);
            int lastPostID = gson.fromJson(json.get("lastID"), int.class);
            ArrayList<Post> posts = Database.GetAllPostsFilterSub(sub, prompt, lastPostID);
            ArrayList<Integer> myVotes = new ArrayList<>();
            for(Post post : posts) {
                if(user == null)
                    myVotes.add(0);
                else
                    myVotes.add(user.CheckVote(post));
            }
            JsonObject jsonObj = new JsonObject();
            jsonObj.add("posts", gson.toJsonTree(posts));
            jsonObj.add("votes", gson.toJsonTree(myVotes));
            res.type("application/json");
            return gson.toJson(jsonObj);
        });

        // Route: Get user's post feed - filter by author
        post("/post/feed/filter-author", (req, res) -> {
            JsonObject json = gson.fromJson(req.body(), JsonObject.class);
            User user = gson.fromJson(json.get("user"), User.class);
            User author = gson.fromJson(json.get("author"), User.class);
            String prompt = gson.fromJson(json.get("prompt"), String.class);
            int lastPostID = gson.fromJson(json.get("lastID"), int.class);
            ArrayList<Post> posts = Database.GetAllPostsFilterUser(author, prompt, lastPostID);
            ArrayList<Integer> myVotes = new ArrayList<>();
            for(Post post : posts) {
                if(user == null)
                    myVotes.add(0);
                else
                    myVotes.add(user.CheckVote(post));
            }
            JsonObject jsonObj = new JsonObject();
            jsonObj.add("posts", gson.toJsonTree(posts));
            jsonObj.add("votes", gson.toJsonTree(myVotes));
            res.type("application/json");
            return gson.toJson(jsonObj);
        });

        // Route: Get user's post feed - filter by upvoted
        post("/post/feed/filter-upvote", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                User user = gson.fromJson(json.get("user"), User.class);
                String prompt = gson.fromJson(json.get("prompt"), String.class);
                int lastPostID = gson.fromJson(json.get("lastID"), int.class);
                ArrayList<Post> posts = user.GetAllPostsFilterVote(prompt, 1, lastPostID);
                res.type("application/json");
                return gson.toJson(posts, Post[].class);
            }
            catch (SQLException e) {
                e.printStackTrace();
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Get user's post feed - filter by downvoted
        post("/post/feed/filter-downvote", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                User user = gson.fromJson(json.get("user"), User.class);
                String prompt = gson.fromJson(json.get("prompt"), String.class);
                int lastPostID = gson.fromJson(json.get("lastID"), int.class);
                ArrayList<Post> posts = user.GetAllPostsFilterVote(prompt, -1, lastPostID);
                res.type("application/json");
                return gson.toJson(posts, Post[].class);
            }
            catch (SQLException e) {
                e.printStackTrace();
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Send Private message
        post("/pm/send", (req, res) -> {
            try {
                Message msg = gson.fromJson(req.body(), Message.class);
                msg.send();
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        //Route: Delete Private Message
        post("/pm/delete", (req, res) -> {
            try {
                Message message = gson.fromJson(req.body(), Message.class);
                message.delete();
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Get all user's subcreddits
        post("/user/subcreddits", (req, res) -> {
            try {
                User user = gson.fromJson(req.body(), User.class);
                ArrayList<Subcreddit> subcreddits = user.GetSubcreddits();
                res.type("application/json");
                return gson.toJson(subcreddits);
            }
            catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Check the user's vote for a post
        post("/user/checkvote", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                User user = gson.fromJson(json.get("user"), User.class);
                Voteable voteable = gson.fromJson(json.get("voteable"), Voteable.class);
                int vote = user.CheckVote(voteable);
                res.type("application/json");
                return gson.toJson(vote);
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Get user's private message feed
        post("/pm/feed", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                User user = gson.fromJson(json.get("user"), User.class);
                User friend = gson.fromJson(json.get("friend"), User.class);
                int lastMessageID = gson.fromJson(json.get("lastID"), int.class);
                ArrayList<Message> messages = user.GetPrivateMessageFeed(friend, lastMessageID);
                res.type("application/json");
                return gson.toJson(messages);
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Get user's unread private message
        post("/pm/unread", (req, res) -> {
            try {
                User user = gson.fromJson(req.body(), User.class);
                ArrayList<Message> messages = user.GetUnreadPrivateMessages();
                res.type("application/json");
                return gson.toJson(messages);
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Get user's private message feed
        post("/pm/update", (req, res) -> {
            JsonObject json = gson.fromJson(req.body(), JsonObject.class);
            User user1 = gson.fromJson(json.get("user1"), User.class);
            User user2 = gson.fromJson(json.get("user2"), User.class);
            int lastMessageID = gson.fromJson(json.get("lastID"), int.class);
            ArrayList<Message> messages = user1.GetLatestPrivateMessages(user2, lastMessageID);
            res.type("application/json");
            return gson.toJson(messages);
        });

        // Route: Set messages to read
        post("/pm/read", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                User user = gson.fromJson(json.get("user"), User.class);
                User friend = gson.fromJson(json.get("friend"), User.class);
                user.ReadMessages(friend);
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            }
            catch (Exception e) {
                e.printStackTrace();
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Keep the user session alive
        post("/user/keepalive", (req, res) -> {
            try {
                User user = gson.fromJson(req.body(), User.class);
                user.KeepAlive();
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Get a specific user
        get("/user", (req, res) -> {
            int id = Integer.parseInt(req.queryParams("id"));
            User user = Database.GetUser(id);
            res.type("application/json");
            return gson.toJson(user, User.class);
        });

        // Route: login user
        get("/user/login", (req, res) -> {
            String usermail = req.queryParams("usermail");
            String password = req.queryParams("password");
            try {
                User user = User.login(usermail, password);
                if(user != null)
                    user.KeepAlive();
                res.type("application/json");
                return gson.toJson(user, User.class);
            }
            catch (SQLException e) {
                if(e.getMessage().equalsIgnoreCase("online"))
                    res.status(233);
                else
                    res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });




        // BOOKMARK: COMMENT
        //Route: Create new comment
        post("/comment/create", (req, res) -> {
            try {
                Comment comment = gson.fromJson(req.body(), Comment.class);
                res.type("application/json");
                return gson.toJson(comment.create(), int.class);
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        //Route: Delete comment
        post("/comment/delete", (req, res) -> {
            try {
                Comment comment = gson.fromJson(req.body(), Comment.class);
                comment.delete();
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        //filter user's comment feed
        post("/comment/feed", (req, res) -> {
            JsonObject json = gson.fromJson(req.body(), JsonObject.class);
            User user = gson.fromJson(json.get("user"), User.class);
            String prompt = gson.fromJson(json.get("prompt"), String.class);
            int lastPostID = gson.fromJson(json.get("lastID"), int.class);
            ArrayList<Comment> comments = new ArrayList<>();
            try {
                comments = User.GetCommentFeed(user, prompt, lastPostID);
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            ArrayList<Integer> myVotes = new ArrayList<>();
            for(Comment comment : comments) {
                if(user == null)
                    myVotes.add(0);
                else
                    myVotes.add(user.CheckVote(comment));
            }

            JsonObject jsonObj = new JsonObject();
            jsonObj.add("comments", gson.toJsonTree(comments));
            jsonObj.add("votes", gson.toJsonTree(myVotes));
            res.type("application/json");
            return gson.toJson(jsonObj);
        });

        //Route: Edit comment
        post("/comment/edit", (req, res) -> {
            try {
                Comment comment = gson.fromJson(req.body(), Comment.class);
                comment.update();
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

//Route: Get Post's comment feed
        post("/comments/feed", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                User user = gson.fromJson(json.get("user"), User.class);
                int commentid = gson.fromJson(json.get("commentid"), int.class);
                Map<Integer, Integer> myVotes = new HashMap<>();
                Comment parent = Database.GetComment(commentid);
                myVotes.put(parent.getID(), user == null? 0 : user.CheckVote(parent));
                PriorityQueue<Comment> lv1 = parent.GetReplies(user, myVotes);
                Map<Integer, ArrayList<Comment>> parentMap = new HashMap<>();
                Map<Integer, ArrayList<Comment>> commentsMap = new HashMap<>();
                ArrayList<Comment> comments = new ArrayList<>();
                while(!lv1.isEmpty()) {
                    ArrayList<Comment> lv2 = new ArrayList<>();
                    Comment comm = lv1.poll();
                    comments.add(comm);
                    PriorityQueue<Comment> l2 = comm.GetReplies(user, myVotes);
                    while(!l2.isEmpty())
                        lv2.add(l2.poll());
                    commentsMap.put(comm.getID(), lv2);
                }
                parentMap.put(parent.getID(), comments);

                JsonObject jsonObj = new JsonObject();
                jsonObj.add("parent", gson.toJsonTree(parent, Comment.class));
                jsonObj.add("lv1", gson.toJsonTree(parentMap, commentMapType));
                jsonObj.add("lv2", gson.toJsonTree(commentsMap, commentMapType));
                jsonObj.add("votes", gson.toJsonTree(myVotes, id_vote_type));
                res.type("application/json");
                return gson.toJson(jsonObj);
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        //Route: Get Post's comment feed
        post("/comment/feed", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                User user = gson.fromJson(json.get("user"), User.class);
                int commentid = gson.fromJson(json.get("commentid"), int.class);
                Map<Integer, Integer> myVotes = new HashMap<>();
                Comment parent = Database.GetComment(commentid);
                myVotes.put(parent.getID(), user == null? 0 : user.CheckVote(parent));
                PriorityQueue<Comment> lv1 = parent.GetReplies(user, myVotes);
                Map<Integer, ArrayList<Comment>> parentMap = new HashMap<>();
                Map<Integer, ArrayList<Comment>> commentsMap = new HashMap<>();
                ArrayList<Comment> comments = new ArrayList<>();
                while(!lv1.isEmpty()) {
                    ArrayList<Comment> lv2 = new ArrayList<>();
                    Comment comm = lv1.poll();
                    comments.add(comm);
                    PriorityQueue<Comment> l2 = comm.GetReplies(user, myVotes);
                    while(!l2.isEmpty())
                        lv2.add(l2.poll());
                    commentsMap.put(comm.getID(), lv2);
                }
                parentMap.put(parent.getID(), comments);

                JsonObject jsonObj = new JsonObject();
                jsonObj.add("parent", gson.toJsonTree(parent, Comment.class));
                jsonObj.add("lv1", gson.toJsonTree(parentMap, commentMapType));
                jsonObj.add("lv2", gson.toJsonTree(commentsMap, commentMapType));
                jsonObj.add("votes", gson.toJsonTree(myVotes, id_vote_type));
                res.type("application/json");
                return gson.toJson(jsonObj);
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Get a specific comment
        get("/comment", (req, res) -> {
            int id = Integer.parseInt(req.queryParams("id"));
            Comment comment = Database.GetComment(id);
            res.type("application/json");
            return gson.toJson(comment);
        });

        // Route: Get all comments
        get("/comments", (req, res) -> {
            int postid = Integer.parseInt(req.queryParams("postid"));
            String prompt = req.queryParams("prompt");
            ArrayList<Comment> comments = Database.GetAllComments(prompt, postid);
            res.type("application/json");
            return gson.toJson(comments);
        });



        // BOOKMARK: Subcreddit
        //Route: Create new subcreddit
        post("/subcreddit/create", (req, res) -> {
            try {
                Subcreddit subcreddit = gson.fromJson(req.body(), Subcreddit.class);
                int id = subcreddit.create();
                res.type("application/json");
                return gson.toJson(id, int.class);
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        //filter user's subcreddit feed
        post("/subcreddit/feed", (req, res) -> {
            JsonObject json = gson.fromJson(req.body(), JsonObject.class);
            User user = gson.fromJson(json.get("user"), User.class);
            String prompt = gson.fromJson(json.get("prompt"), String.class);
            int lastPostID = gson.fromJson(json.get("lastID"), int.class);
            ArrayList<Subcreddit> subs = new ArrayList<>();
            try {
                subs = User.GetSubFeed(user, prompt, lastPostID);
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            JsonObject jsonObj = new JsonObject();
            jsonObj.add("subs", gson.toJsonTree(subs));
            res.type("application/json");
            return gson.toJson(jsonObj);
        });

        // Route: Edit Subcreddit
        post("/subcreddit/edit", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                User user = gson.fromJson(json.get("user"), User.class);
                Subcreddit sub = gson.fromJson(json.get("subcreddit"), Subcreddit.class);
                user.updateSubcreddit(sub);
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        //Route: Delete subcreddit
        post("/subcreddit/delete", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                User user = gson.fromJson(json.get("user"), User.class);
                Subcreddit sub = gson.fromJson(json.get("subcreddit"), Subcreddit.class);
                user.deleteSubcreddit(sub);
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        //Route: join a subcreddit
        post("/subcreddit/join", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                User user = gson.fromJson(json.get("user"), User.class);
                Subcreddit sub = gson.fromJson(json.get("subcreddit"), Subcreddit.class);

                user.joinSubcreddit(sub);
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        //Route: leave a subcreddit
        post("/subcreddit/leave", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                User user = gson.fromJson(json.get("user"), User.class);
                Subcreddit sub = gson.fromJson(json.get("subcreddit"), Subcreddit.class);

                user.leaveSubcreddit(sub);
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        //Route: Ban a subcreddit member
        post("/subcreddit/ban", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                Moderator moderator = gson.fromJson(json.get("moderator"), Moderator.class);
                User user = gson.fromJson(json.get("user"), User.class);
                Subcreddit sub = gson.fromJson(json.get("subcreddit"), Subcreddit.class);
                String reason = gson.fromJson(json.get("reason"), String.class);

                moderator.BanMember(user, sub, reason);
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        //Route: Unban a subcreddit member
        post("/subcreddit/unban", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                Moderator moderator = gson.fromJson(json.get("moderator"), Moderator.class);
                User user = gson.fromJson(json.get("user"), User.class);
                Subcreddit sub = gson.fromJson(json.get("subcreddit"), Subcreddit.class);

                moderator.UnbanMember(user, sub);
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Get subcreddit members
        post("/subcreddit/members", (req, res) -> {
            JsonObject json = gson.fromJson(req.body(), JsonObject.class);
            Subcreddit sub = gson.fromJson(json.get("subcreddit"), Subcreddit.class);
            int lastID = gson.fromJson(json.get("lastID"), int.class);
            ArrayList<User> members = sub.GetMembers(lastID);
            res.type("application/json");
            return gson.toJson(members, userListType);
        });

        // Route: Get subcreddit bans
        post("/subcreddit/bans", (req, res) -> {
            Subcreddit subcreddit = gson.fromJson(req.body(), Subcreddit.class);
            ArrayList<User> bannedMembers = subcreddit.GetBannedMembers();
            res.type("application/json");
            return gson.toJson(bannedMembers, userListType);
        });

        // Route: Add subcreddit moderator
        post("/subcreddit/mod", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                User user = gson.fromJson(json.get("user"), User.class);
                Subcreddit sub = gson.fromJson(json.get("subcreddit"), Subcreddit.class);
                sub.AddModerator(user);
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            }
            catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Get subcreddit bans
        post("/subcreddit/verifymod", (req, res) -> {
            JsonObject json = gson.fromJson(req.body(), JsonObject.class);
            User user = gson.fromJson(json.get("user"), User.class);
            Subcreddit sub = gson.fromJson(json.get("subcreddit"), Subcreddit.class);
            res.type("application/json");
            return gson.toJson(sub.VerifyModeration(user), boolean.class);
        });

        // Route: Get a specific subcreddit
        get("/subcreddit", (req, res) -> {
            int id = Integer.parseInt(req.queryParams("id"));
            Subcreddit subcreddit = Database.GetSubcreddit(id);
            res.type("application/json");
            return gson.toJson(subcreddit);
        });

        // Route: Get all subcreddits
        get("/subcreddit/all", (req, res) -> {
            ArrayList<Subcreddit> subcreddits = Database.GetAllSubcreddits();
            res.type("application/json");
            return gson.toJson(subcreddits);
        });




        // BOOKMARK: Reports
        // Route: Get if a report exists
        post("/report/exist", (req, res) -> {
            try {
                Report report = gson.fromJson(req.body(), Report.class);
                res.type("application/json");
                return gson.toJson(report.exists(), boolean.class);
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Submit a report
        post("/report/submit", (req, res) -> {
            try {
                Report report = gson.fromJson(req.body(), Report.class);
                report.getReporter().addReport(report);
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Report a user
        post("/report/resolve", (req, res) -> {
            try {
                Report report = gson.fromJson(req.body(), Report.class);
                report.Resolve();
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Report a user
        post("/report/dismiss", (req, res) -> {
            try {
                Report report = gson.fromJson(req.body(), Report.class);
                report.Dismiss();
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });
        // Route: Analytics
        post("/analytics", (req, res) -> {
            try {
                User user = gson.fromJson(req.body(), User.class);
                if(user == null)
                    throw new Exception("Access Restriction!");
                int subs = 0, posts = 0, users = 0;
                String sql = "SELECT COUNT(*) FROM subcreddits";
                PreparedStatement stmt = Database.PrepareStatement(sql);
                ResultSet rs = stmt.executeQuery();
                if(rs.next())
                    subs = rs.getInt(1);
                sql = "SELECT COUNT(*) FROM posts";
                stmt = Database.PrepareStatement(sql);
                if(rs.next())
                    posts = rs.getInt(1);
                sql = "SELECT COUNT(*) FROM users";
                stmt = Database.PrepareStatement(sql);
                while(rs.next())
                    users = rs.getInt(1);
                Integer[] arr = {posts, subs, users};
                res.type("application/json");
                return gson.toJson(arr, Integer[].class);
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Get user report feed
        post("/report/feed/users", (req, res) -> {
            JsonObject json = gson.fromJson(req.body(), JsonObject.class);
            Admin admin = gson.fromJson(json.get("admin"), Admin.class);
            int lastID = gson.fromJson(json.get("lastID"), int.class);
            ArrayList<Report> reports = Report.GetUserReportFeed(admin, lastID);
            res.type("application/json");
            return gson.toJson(reports);
        });

        // Route: Get user report feed
        post("/reports/feed", (req, res) -> {
            JsonObject json = gson.fromJson(req.body(), JsonObject.class);
            User user = gson.fromJson(json.get("user"), Admin.class);
            int lastID = gson.fromJson(json.get("lastID"), int.class);
            ArrayList<Report> reports = user.GetAllReports(lastID);
            res.type("application/json");
            return gson.toJson(reports);
        });

        // Route: Get post report feed
        post("/report/feed/posts", (req, res) -> {
            JsonObject json = gson.fromJson(req.body(), JsonObject.class);
            User user = gson.fromJson(json.get("user"), User.class);
            Subcreddit sub = gson.fromJson(json.get("sub"), Subcreddit.class);
            int lastID = gson.fromJson(json.get("lastID"), int.class);
            ArrayList<Report> reports = Report.GetPostReportFeed(user, sub, lastID);
            res.type("application/json");
            return gson.toJson(reports);
        });

        // Route: Get comments report feed
        post("/report/feed/comments", (req, res) -> {
            JsonObject json = gson.fromJson(req.body(), JsonObject.class);
            User user = gson.fromJson(json.get("user"), User.class);
            Subcreddit sub = gson.fromJson(json.get("sub"), Subcreddit.class);
            int lastID = gson.fromJson(json.get("lastID"), int.class);
            ArrayList<Report> reports = Report.GetCommentReportFeed(user, sub, lastID);
            res.type("application/json");
            return gson.toJson(reports);
        });




        // BOOKMARK: File Uploading

        // Route: File upload
        final String finalTunnelURL = tunnelURL;
        post("/upload", (req, res) -> {
            req.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/tmp"));
            Path tempFile = Files.createTempFile(Paths.get(UPLOAD_DIR), "", "");

            try (InputStream is = req.raw().getPart("file").getInputStream()) {
                Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            String original = req.raw().getPart("file").getSubmittedFileName();
            String extension = original.substring(original.lastIndexOf('.'));
            String newName = UUID.randomUUID() + extension;

            Path finalPath = Paths.get(UPLOAD_DIR, newName);
            Files.move(tempFile, finalPath, StandardCopyOption.REPLACE_EXISTING);

            String fileUrl =  finalTunnelURL + "/uploads/" + newName;
            System.out.println("Uploaded\t" + fileUrl);

            res.type("application/json");
            return gson.toJson(Map.of("url", fileUrl));
        });

        head("/uploads/:filename", (req, res) -> {
            String filename = req.params(":filename");
            File file = new File(UPLOAD_DIR, filename);

            if (!file.exists() || file.isDirectory()) {
                halt(404, "File not found");
                return null;
            }

            res.status(200);
            res.header("Accept-Ranges", "bytes");
            res.header("Content-Length", String.valueOf(file.length()));
            res.header("Content-Type", Files.probeContentType(file.toPath()));
            return "";
        });

        // Serve files with range support
        get("/uploads/:filename", (req, res) -> {
            String filename = req.params(":filename");
            File file = new File(UPLOAD_DIR, filename);

            if (!file.exists() || file.isDirectory()) {
                halt(404, "File not found");
                return null;
            }

            String range = req.headers("Range");
            long fileLength = file.length();
            long start = 0, end = fileLength - 1;

            if (range != null && range.startsWith("bytes=")) {
                String[] parts = range.substring(6).split("-");
                try {
                    start = Long.parseLong(parts[0]);
                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        end = Long.parseLong(parts[1]);
                    }
                } catch (NumberFormatException ignored) {}
            }

            if (end >= fileLength) end = fileLength - 1;
            long contentLength = end - start + 1;

            res.status(range == null ? 200 : 206);
            res.header("Content-Type", Files.probeContentType(file.toPath()));
            res.header("Accept-Ranges", "bytes");
            if (range != null)
                res.header("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
            res.header("Content-Length", String.valueOf(contentLength));

            try (RandomAccessFile raf = new RandomAccessFile(file, "r");
                 OutputStream os = res.raw().getOutputStream()) {
                raf.seek(start);
                byte[] buffer = new byte[8192];
                long bytesRemaining = contentLength;

                while (bytesRemaining > 0) {
                    int bytesToRead = (int) Math.min(buffer.length, bytesRemaining);
                    int bytesRead = raf.read(buffer, 0, bytesToRead);
                    if (bytesRead == -1) break;
                    os.write(buffer, 0, bytesRead);
                    bytesRemaining -= bytesRead;
                }

                os.flush();
            }

            // Prevent Spark from writing extra headers/content
            halt();
            return null;
        });

        System.out.println("Server running on port 7878...");
        Scanner scanner = new Scanner(System.in);
        while(true) {
            if(scanner.nextLine().equalsIgnoreCase("quit"))
                System.exit(0);
        }
    }

    static void SetupVerification(int userID, String email) throws MessagingException, SQLException {
        String token = UUID.randomUUID().toString();
        Database.InsertVerificationToken(userID, token);
        String link = System.getenv("server_url") + "/verify?token=" + token;
        String body = "Thank you for registering with CREDDIT. You are just one step away. Click this link to verify your email:\n" + link;
        Server.SendEmail(email, "CREDDIT Account Verification", body);
    }

    private static void SendEmail(String email, String subject, String body) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(System.getenv("email"), System.getenv("mail_pass"));
            }
        });

        jakarta.mail.Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(System.getenv("email")));
        message.setRecipients(jakarta.mail.Message.RecipientType.TO, InternetAddress.parse(email));
        message.setSubject(subject);
        message.setText(body);

        Transport.send(message);
    }
}
