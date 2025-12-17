package com.crdt;

public enum ReportType {
    SPAM("Spam"), HARASSMENT("Harassment"), HATE_SPEECH("Hate Speech"), MISINFORMATION("Misinformation"),
    NSFW("NSFW"), OTHER("OTHER");

    private final String type;

    ReportType(String type) {
        this.type = type;
    }

    public static ReportType from(String s) {
        if(s.equalsIgnoreCase("Spam"))
            return SPAM;
        if(s.equalsIgnoreCase("Harassment"))
            return HARASSMENT;
        if(s.equalsIgnoreCase("Hate Speech"))
            return HATE_SPEECH;
        if(s.equalsIgnoreCase("Misinformation"))
            return SPAM;
        if(s.equalsIgnoreCase("NSFW"))
            return HARASSMENT;
        else {
            return OTHER;
        }
    }
    public String toString() {
        return this.type;
    }
}