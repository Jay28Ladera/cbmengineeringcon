package com.asmsi.admin_system.dto;

import lombok.*;

import java.time.LocalDate;

/**
 * Matches the payload sent by the page.
 * No jakarta.validation annotations — works with your current POM.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VisitingDayRequest {
    // Student context
    private String idNumber;
    private String lrn;
    private String name;
    private String campus;
    private String familySaint;
    private String batchNumber;
    private String imageUrl;
    private String address;

    // Visit info
    private String timeIn;          // "HH:mm:ss" from frontend (optional)
    private LocalDate date;         // optional; defaults to today
    

    private Integer adultVisitor;   // optional; defaults to 0
    private Integer kidVisitor;     // optional; defaults to 0
    private Integer totalVisitors;  // optional; computed if null
    private String contactNumber; // NEW
  private String vehicleType;   // NEW

    // Audit
    private String account;
}
