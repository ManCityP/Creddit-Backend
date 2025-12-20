package com.crdt;

public enum ReportStatus {
    PENDING("Pending"), RESOLVED("Resolved"), DISMISSED("Dismissed");

    private final String status;

    ReportStatus(String status) {
        this.status = status;
    }

    public static ReportStatus from(String s) {
        if(s.equalsIgnoreCase("Pending"))
            return PENDING;
        if(s.equalsIgnoreCase("Resolved"))
            return RESOLVED;
        if(s.equalsIgnoreCase("Dismissed"))
            return DISMISSED;
        return null;
    }
    public String toString() {
        return this.status;
    }
}