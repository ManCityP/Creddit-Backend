package com.crdt;

import java.sql.Timestamp;

public class Post 
{
    
    private int id;
    private User author;
    private Subcreddit subcreddit;
    private String title;
    private String content;
    private ArrayList<Media> media;
    private ArrayList<String> categories;
    private TimeStamp timeCreated;
    private TimeStamp timeEdited;
    private int votes;

    public Post(int id, User author, Subcreddit subcreddit, String title, String content, ArrayList<Media> media, ArrayList <String> categories, TimeStamp timeCreated, TimeStamp timeEdited, int votes)
    {
     if (id < 0)
        throw new IllegalArgumentException("ID cannot be negative.");

    if (author == null)
        throw new IllegalArgumentException("Author cannot be null.");

    if (subcreddit == null)
        throw new IllegalArgumentException("Subcreddit cannot be null.");

    if (title == null || title.isEmpty())
        throw new IllegalArgumentException("Title cannot be null or empty.");

    if (timeCreated == null)
        throw new IllegalArgumentException("TimeCreated cannot be null.");

    if (content == null)
        content = "";

    if (media == null)
        media = new ArrayList<>();

    if (categories == null)
        categories = new ArrayList<>();

    if (timeEdited == null)
        timeEdited = timeCreated;
    
        this.id = id;
        this.author = author;
        this.subcreddit = subcreddit;
        this.title = title;
        this.content = content;
        this.media = media;
        this.categories = categories;
        this.timeCreated = timeCreated;
        this.timeEdited = timeEdited;
        this.votes = votes;
    }

    public int GetID() 
    {
        return id;
    }

    public User GetAuthor() 
    {
        return author;
    }

    public Subcreddit GetSubcreddit() 
    {
        return subcreddit;
    }

    public String GetTitle() 
    {
        return title;
    }

    public String GetContent()
    {
        return content;
    }

    public ArrayList<Media> GetMedia() 
    {
        return media;
    }

    public ArrayList<String> GetCategories() 
    {
        return categories;
    }

    public TimeStamp GetTimeCreated() 
    {
        return timeCreated;
    }

    public TimeStamp GetTimeEdited() 
    {
        return timeEdited;
    }

    public int GetVotes() 
    {
        return votes;
    }
    
    public ArrayList<Comment> GetComments()
    {
        return comments;
    }
}