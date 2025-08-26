package com.asmsi.admin_system.dto.report;

public record GradVisitRow(
    String imageUrl,
    String idNumber,
    String batchNumber,
    String address,
    String date,          // "yyyy-MM-dd"
    String time,          // "HH:mm:ss"
    Integer adultVisitor, // <-- add
    Integer kidVisitor,   // <-- add
    Integer totalVisitors,// <-- add
    String vehicleType    // <-- add (e.g., "FOUR_WHEELED", "MOTORCYCLE")
) {}
