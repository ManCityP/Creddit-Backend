package com.crdt;
import com.crdt.users.Admin;
import com.crdt.users.Gender;
import com.crdt.users.User;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws IOException {
        RuntimeTypeAdapterFactory<User> userAdapter =
                RuntimeTypeAdapterFactory.of(User.class, "type")
                        .registerSubtype(User.class, "user")
                        .registerSubtype(Admin.class, "admin");
        Gson gson = new GsonBuilder().registerTypeAdapterFactory(userAdapter).create();

        while (true) {
            Scanner input = new Scanner(System.in);
            System.out.println("1.Register");
            System.out.println("2.login");
            System.out.print("Enter operation: ");
            int in = input.nextInt();
            input.nextLine();
            if(in == 1) {
                System.out.print("Enter username: ");
                String username  = input.nextLine();
                System.out.print("Enter email: ");
                String email = input.nextLine();
                System.out.print("Enter password: ");
                String password =  input.nextLine();
                System.out.print("Enter gender: ");
                String gender = input.nextLine();

                // Now send user JSON
                User user = new User(1, username, email, password, Gender.toGender(gender), "", new Media(MediaType.IMAGE,"") , null, true);
                String jsonBody = gson.toJson(user);

                URL url = new URL(System.getenv("Base_URL") + "/user/register");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.getBytes());
                }

                if (conn.getResponseCode() == 200) {
                    System.out.println("User registered successfully");
                } else {
                    System.out.println("User failed to register");
                }
            }
            else if (in == 2) {
                System.out.print("Enter username or email: ");
                String usermail  = input.nextLine();
                System.out.print("Enter password: ");
                String password = input.nextLine();

                URL url = new URL(System.getenv("Base_URL") + String.format("/user/login?usermail=%s&password=%s", java.net.URLEncoder.encode(usermail, "UTF-8"), java.net.URLEncoder.encode(password, "UTF-8")));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                User user = gson.fromJson(sb.toString(), User.class);

                if (user != null) {
                    System.out.println("Logged in successfully");
                    System.out.println("Username: " + user.getUsername());
                    System.out.println("Email: " + user.getEmail());
                    System.out.println("Time Created: " + user.getTimeCreated());
                    System.out.println();
                }else  {
                    System.out.println("Username or password is incorrect");
                    System.out.println();
                }
            }else {
                System.out.println("Invalid input");
                System.out.println();
            }
        }
    }
}
