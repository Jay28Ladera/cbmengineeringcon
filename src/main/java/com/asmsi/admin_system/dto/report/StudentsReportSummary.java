package com.asmsi.admin_system.dto.report;

import java.util.List;
import java.util.Map;

public record StudentsReportSummary(
        long totalVisitors,
        long totalAdults,
        long totalKids,
        long totalStudentsVisited,
        Map<String, Long> visitorsPerBatchNumber,
        Map<String, Long> visitorsPerFamilySaint,
        List<StudentWithVisitorRow> studentsWithVisitors,
        List<StudentNoVisitorRow>  studentsWithoutVisitors,
        Map<String, Long> vehicles
) {}
