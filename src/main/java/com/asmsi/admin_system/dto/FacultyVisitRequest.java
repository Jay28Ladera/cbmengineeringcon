package com.asmsi.admin_system.dto;

public record FacultyVisitRequest(
        String  employeeId,
        Integer adultVisitor,
        Integer kidVisitor,
        Integer totalVisitors,  // optional; server will default to adult+kid
        String  contactNumber,
        String  vehicleType     // "MOTORCYCLE" | "FOUR_WHEELED"
) {}
