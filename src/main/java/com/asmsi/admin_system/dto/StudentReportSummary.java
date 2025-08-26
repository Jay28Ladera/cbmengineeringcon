package com.asmsi.admin_system.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter @Setter @Builder
@AllArgsConstructor @NoArgsConstructor
public class StudentReportSummary {
    private int totalVisitors;
    private int totalAdults;
    private int totalKids;

    private int totalStudentsVisited; // count of unique students with any visit in range

    private Map<String, Integer> visitorsPerBatchNumber;
    private Map<String, Integer> visitorsPerFamilySaint;

    // rows for the Students tab
    private List<RowWithVisitors> studentsWithVisitors;
    private List<RowWithoutVisitors> studentsWithoutVisitors; // can be empty if you don't have a master list

    private Map<String, Integer> vehicles; // {"FOUR_WHEELED":x,"MOTORCYCLE":y}

    @Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
    public static class RowWithVisitors {
        private String idNumber;
        private String name;
        private String familySaint;
        private String batchNumber;
        private String arrivalTime;   // "HH:mm:ss"
        private Integer totalVisitors;
    }

    @Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
    public static class RowWithoutVisitors {
        private String idNumber;
        private String name;
        private String familySaint;
        private String batchNumber;
    }
}
