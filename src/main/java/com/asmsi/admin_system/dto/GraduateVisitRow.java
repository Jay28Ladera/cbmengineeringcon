package com.asmsi.admin_system.dto;

public record GraduateVisitRow(
        String imageUrl,
        String idNumber,
        String date,
        String time,
        String address,
        String batchNumber
) {}
