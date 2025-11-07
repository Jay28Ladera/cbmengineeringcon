package com.asmsi.admin_system.dto;

import java.util.List;

public class AttendanceReportResponse {

    public Summary summary;
    public List<DailyCount> dailyCounts;
    public List<TopStudent> topStudents;
    public List<Record> records;

    public static class Summary {
        public long total;
        public long present;
        public long absent;
        public long uniqueStudents;
    }

    public static class DailyCount {
        public String date; // ISO yyyy-MM-dd
        public long count;
    }

    public static class TopStudent {
        public String name; // e.g., "Juan Dela Cruz (2024-0001)"
        public long count;
    }

    public static class Record {
        public String imageUrl;
        public String name;
        public String idNumber;
        public String date;    // yyyy-MM-dd
        public String timeIn;  // HH:mm:ss
        public String timeOut; // HH:mm:ss
        public String status;
        public String account;
    }
}
