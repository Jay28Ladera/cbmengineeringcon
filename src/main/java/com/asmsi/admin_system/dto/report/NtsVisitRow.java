package com.asmsi.admin_system.dto.report;

public record NtsVisitRow(
    String imageUrl,
    String name,
    String employeeId,
    String department,
    String position,
    String date,
    String time,
    Integer adultVisitor,
    Integer kidVisitor,
    Integer totalVisitors,
    String vehicleType
) {}
