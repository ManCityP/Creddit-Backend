package com.crdt;
import java.util.List;


public interface Reportable {
    void report(String reason);
    int getReportCount();
    List<String> getReportReasons();

}
