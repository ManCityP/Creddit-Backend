package com.crdt;

import static spark.Spark.*;

import com.crdt.users.Admin;
import com.crdt.users.Moderator;
import com.crdt.users.User;
import com.google.gson.*;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;

import javax.servlet.MultipartConfigElement;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Server {

    private static final String UPLOAD_DIR = System.getProperty("user.dir") + File.separator + "uploads";
    private static Gson gson; //WORK
    private static Process ngrokProcess;

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
                //if(ngrokProcess.isAlive()) {
                    try {
                        Runtime.getRuntime().exec("taskkill /F /IM ngrok.exe /T");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                //}
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
                        .registerSubtype(Admin.class, "admin");

        Gson gson = new GsonBuilder().registerTypeAdapterFactory(userAdapter).create();

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
                post.create();
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

        //Route: Post vote by user
        post("/post/vote", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                User user = gson.fromJson(json.get("user"), User.class);
                Post post = gson.fromJson(json.get("post"), Post.class);
                int value = gson.fromJson(json.get("value"), int.class);

                user.vote(post, value);
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Get a specific post
        get("/post", (req, res) -> {
            int id = Integer.parseInt(req.queryParams("id"));
            Post post = Database.GetPost(id);
            res.type("application/json");
            return gson.toJson(post);
        });

        // Route: Get all posts
        get("/post/all", (req, res) -> {
            ArrayList<Post> posts = Database.GetAllPosts();
            res.type("application/json");
            return gson.toJson(posts);
        });

        // Route: Get all categories
        get("/category/all", (req, res) -> {
            ArrayList<String> categories = Database.GetAllCategories();
            res.type("application/json");
            return gson.toJson(categories);
        });


        // BOOKMARK: USER
        // Route: Create new user
        post("/user/register", (req, res) -> {
            try {
                User user = gson.fromJson(req.body(), User.class);
                user.register();
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
        post("/user/delete", (req, res) -> {
            try {
                User user = gson.fromJson(req.body(), User.class);
                user.delete();
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
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

                admin.BanUser(user, reason);
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

                admin.UnbanUser(user);
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

        // Route: Edit Private message
        post("/pm/edit", (req, res) -> {
            try {
                Message msg = gson.fromJson(req.body(), Message.class);
                msg.update();
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
            return gson.toJson(user);
        });

        // Route: login user
        get("/user/login", (req, res) -> {
            String usermail = req.queryParams("usermail");
            String password = req.queryParams("password");
            User user = User.login(usermail, password);
            res.type("application/json");
            return gson.toJson(user);
        });

        // Route: Get all users
        get("/user/all", (req, res) -> {
            ArrayList<User> users = Database.GetAllUsers();
            res.type("application/json");
            return gson.toJson(users);
        });

        // Route: Get all user's subcreddits
        get("/user/subcreddits", (req, res) -> {
            User user = gson.fromJson(req.body(), User.class);
            ArrayList<Subcreddit> subcreddits = user.GetSubcreddits();
            res.type("application/json");
            return gson.toJson(subcreddits);
        });

        // Route: Get user's friends
        get("/friends", (req, res) -> {
            User user = gson.fromJson(req.body(), User.class);
            ArrayList<User> users = user.GetFriends();
            res.type("application/json");
            return gson.toJson(users);
        });

        // Route: Get user's sent friend requests
        get("/friends/sent", (req, res) -> {
            User user = gson.fromJson(req.body(), User.class);
            ArrayList<User> users = user.GetSentFriendRequests();
            res.type("application/json");
            return gson.toJson(users);
        });

        // Route: Get user's received friend requests
        get("/friends/received", (req, res) -> {
            User user = gson.fromJson(req.body(), User.class);
            ArrayList<User> users = user.GetReceivedFriendRequests();
            res.type("application/json");
            return gson.toJson(users);
        });

        // Route: Get user's private message feed
        get("/pm/feed", (req, res) -> {
            JsonObject json = gson.fromJson(req.body(), JsonObject.class);
            User user1 = gson.fromJson(json.get("user1"), User.class);
            User user2 = gson.fromJson(json.get("user2"), User.class);
            int lastMessageID = gson.fromJson(json.get("lastID"), int.class);
            ArrayList<Message> messages = user1.GetPrivateMessageFeed(user2, lastMessageID);
            res.type("application/json");
            return gson.toJson(messages);
        });

        // Route: Get user's private message feed
        get("/pm/update", (req, res) -> {
            JsonObject json = gson.fromJson(req.body(), JsonObject.class);
            User user1 = gson.fromJson(json.get("user1"), User.class);
            User user2 = gson.fromJson(json.get("user2"), User.class);
            int lastMessageID = gson.fromJson(json.get("lastID"), int.class);
            ArrayList<Message> messages = user1.GetLatestPrivateMessages(user2, lastMessageID);
            res.type("application/json");
            return gson.toJson(messages);
        });




        // BOOKMARK: COMMENT
        //Route: Create new comment
        post("/comment/create", (req, res) -> {
            try {
                Comment comment = gson.fromJson(req.body(), Comment.class);
                comment.create();
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        //Route: Comment vote by user
        post("/comment/vote", (req, res) -> {
            try {
                JsonObject json = gson.fromJson(req.body(), JsonObject.class);
                User user = gson.fromJson(json.get("user"), User.class);
                Comment comment = gson.fromJson(json.get("comment"), Comment.class);
                int value = gson.fromJson(json.get("value"), int.class);

                user.vote(comment, value);
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
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
            ArrayList<Comment> comments = Database.GetAllComments(postid);
            res.type("application/json");
            return gson.toJson(comments);
        });



        // BOOKMARK: Subcreddit
        //Route: Create new subcreddit
        post("/subcreddit/create", (req, res) -> {
            try {
                Subcreddit subcreddit = gson.fromJson(req.body(), Subcreddit.class);
                subcreddit.create();
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
                Subcreddit subcreddit = gson.fromJson(req.body(), Subcreddit.class);
                subcreddit.delete();
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

        // Route: Get subcreddit bans
        get("/subcreddit/bans", (req, res) -> {
            Subcreddit subcreddit = gson.fromJson(req.body(), Subcreddit.class);
            ArrayList<User> bannedMembers = subcreddit.GetBannedMembers();
            res.type("application/json");
            return gson.toJson(bannedMembers);
        });

        // Route: Get subcreddit bans
        get("/subcreddit/verifymod", (req, res) -> {
            JsonObject json = gson.fromJson(req.body(), JsonObject.class);
            Moderator moderator = gson.fromJson(json.get("moderator"), Moderator.class);
            Subcreddit sub = gson.fromJson(json.get("subcreddit"), Subcreddit.class);
            res.type("application/json");
            return gson.toJson(moderator.VerifyModeration(sub));
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
}
