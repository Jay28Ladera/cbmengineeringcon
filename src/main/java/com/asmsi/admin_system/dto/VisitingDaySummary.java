package com.asmsi.admin_system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitingDaySummary {
    private int totalVisitors;
    private int totalAdults;
    private int totalKids;
    private int totalStudentsVisited;

    private Map<String, Integer> visitorsPerFamilySaint;
    private Map<String, Integer> visitorsPerBatchNumber;

    private List<SimpleStudent> studentsWithVisitors;
    private List<SimpleStudent> studentsWithoutVisitors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleStudent {
        private String idNumber;
        private String name;
        private String familySaint;
        private String batchNumber;

        // With visitors only:
        private String arrivalTime;     // earliest timeIn within the date range (e.g. "09:15:23")
        private Integer adultVisitor;   // sum in range
        private Integer kidVisitor;     // sum in range
        private Integer totalVisitors;  // sum in range
        private String contactNumber;   // from most recent record in range
        private String vehicleType;     // from most recent record in range
    }
}
