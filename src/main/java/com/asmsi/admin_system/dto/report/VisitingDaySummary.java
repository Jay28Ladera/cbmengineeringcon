package com.asmsi.admin_system.dto.report;

import lombok.*;
import java.util.List;
import java.util.Map;

@Getter @Setter @Builder
@AllArgsConstructor @NoArgsConstructor
public class VisitingDaySummary {

    private Integer totalVisitors;
    private Integer totalAdults;
    private Integer totalKids;
    private Integer totalStudentsVisited;

    private Map<String, Integer> visitorsPerFamilySaint;
    private Map<String, Integer> visitorsPerBatchNumber;

    // NEW: what your service & Excel need
    private Map<String, Integer> vehicles; // keys: "FOUR_WHEELED", "MOTORCYCLE"

    private List<SimpleStudent> studentsWithVisitors;
    private List<SimpleStudent> studentsWithoutVisitors;

    @Getter @Setter @Builder
    @AllArgsConstructor @NoArgsConstructor
    public static class SimpleStudent {
        private String idNumber;
        private String name;
        private String familySaint;
        private String batchNumber;

        private String arrivalTime;     // "HH:mm:ss"
        private Integer adultVisitor;
        private Integer kidVisitor;
        private Integer totalVisitors;

        private String contactNumber;
        private String vehicleType;
    }
}
