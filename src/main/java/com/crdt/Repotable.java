package com.crdt;
import java.util.List;


public interface Repotable {
    void report(String reason);
    int getReportCount();
    List<String> getReportReasons();

}
