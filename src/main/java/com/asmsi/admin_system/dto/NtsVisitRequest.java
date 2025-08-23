package com.asmsi.admin_system.dto;

public record NtsVisitRequest(
        String  employeeId,
        Integer adultVisitor,
        Integer kidVisitor,
        Integer totalVisitors,   // optional (server falls back to adult + kid)
        String  contactNumber,
        String  vehicleType      // "MOTORCYCLE" | "FOUR_WHEELED"
) {}
