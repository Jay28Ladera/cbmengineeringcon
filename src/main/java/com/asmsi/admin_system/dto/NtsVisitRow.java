// src/main/java/com/asmsi/admin_system/dto/NtsVisitRow.java
package com.asmsi.admin_system.dto;

public record NtsVisitRow(
        String imageUrl,
        String name,
        String employeeId,
        String date,
        String time,
        String position,
        String department,
        Integer adultVisitor,
        Integer kidVisitor,
        Integer totalVisitors,
        String contactNumber,
        String vehicleType
) {}
