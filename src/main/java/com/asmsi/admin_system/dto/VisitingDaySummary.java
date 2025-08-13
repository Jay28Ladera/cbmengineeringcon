package com.asmsi.admin_system.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VisitingDaySummary {
    // Totals
    private int totalVisitors;
    private int totalAdults;
    private int totalKids;
    private int totalStudentsVisited;

    // Groupings
    private Map<String, Integer> visitorsPerFamilySaint;  // familySaint -> sum(adult+kid)
    private Map<String, Integer> visitorsPerBatchNumber;  // batchNumber -> sum(adult+kid)

    // Lists
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SimpleStudent {
        private String idNumber;
        private String name;
        private String familySaint;
        private String batchNumber;
    }

    private List<SimpleStudent> studentsWithVisitors;
    private List<SimpleStudent> studentsWithoutVisitors;
}
