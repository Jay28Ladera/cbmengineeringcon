package com.asmsi.admin_system.dto;

public record GraduateVisitRequest(
        String  idNumber,
        Integer adultVisitor,
        Integer kidVisitor,
        Integer totalVisitors,   // optional; server defaults to adult + kid
        String  contactNumber,
        String  vehicleType      // "MOTORCYCLE" | "FOUR_WHEELED"
) {}
