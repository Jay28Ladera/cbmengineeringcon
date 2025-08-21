package com.asmsi.admin_system.dto;

public record FacultyVisitRow(
        String imageUrl,
        String name,
        String employeeId,
        String date,
        String time,
        String position,
        String department
) {}
