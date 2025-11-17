package com.crdt;

import com.crdt.users.Admin;
import com.crdt.users.Moderator;

import java.sql.Timestamp;

public class Report {

    private int id;
    private Reportable target;
    private String reason;
    private ReportType type;
    private ReportStatus status;
    private Timestamp timeReported;

    public Report(int id, Reportable target, String reason, ReportType type, ReportStatus status, Timestamp timeReported) {

        if (target == null) {
            throw new IllegalArgumentException("Report target cannot be null.");
        }
        if (reason == null || reason.isEmpty()) {
            throw new IllegalArgumentException("Reason cannot be empty.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Report type cannot be null.");
        }
        if (status == null) {
            throw new IllegalArgumentException("Report status cannot be null.");
        }


        this.id = id;
        this.target = target;
        this.reason = reason;
        this.type = type;
        this.status = status;
        this.timeReported = timeReported;
    }


    public void SubmitReport() {
        if (this.status != ReportStatus.PENDING) {
            this.status = ReportStatus.PENDING;
            this.timeReported = new Timestamp(System.currentTimeMillis());
        } else {
            throw new IllegalStateException("Report is already submitted.");
        }

    }


    public void ReviewReport(Moderator moderator) {
        if (moderator == null) {
            throw new IllegalArgumentException("Moderator cannot be null.");
        }
        //Logic from Moderator class
    }


    public void ResolveReport(Admin admin) {

        if (admin == null) {
            throw new IllegalArgumentException("Admin cannot be null.");
        }

        if (this.status == ReportStatus.RESOLVED) {
            throw new IllegalStateException("Report is already resolved.");
        }

        this.status = ReportStatus.RESOLVED;
        System.out.println("Admin " + admin.getUsername() + " resolved report " + id);
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

}