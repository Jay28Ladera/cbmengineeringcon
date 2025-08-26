package com.asmsi.admin_system.dto.report;

public record StudentWithVisitorRow(
        String idNumber,
        String name,
        String familySaint,
        String batchNumber,
        String arrivalTime,   // earliest time-in as string
        int    totalVisitors  // sum across the day
) {}
