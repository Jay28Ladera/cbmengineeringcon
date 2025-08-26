// src/main/java/com/asmsi/admin_system/dto/report/StudentReportSummaryResponse.java
package com.asmsi.admin_system.dto.report;

import java.util.List;
import java.util.Map;

public class StudentReportSummaryResponse {
    public int totalVisitors;
    public int totalAdults;
    public int totalKids;

    // counts
    public int totalStudentsVisited; // optional; UI derives from list length

    // aggregations for the left cards in Students tab
    public Map<String,Integer> visitorsPerBatchNumber;
    public Map<String,Integer> visitorsPerFamilySaint;

    // vehicles
    public VehicleCounts vehicles;

    // tables
    public List<StudentWithVisitorRow> studentsWithVisitors;
    public List<StudentNoVisitorRow>   studentsWithoutVisitors;

    public static class VehicleCounts {
        public int FOUR_WHEELED;
        public int MOTORCYCLE;
    }
}
