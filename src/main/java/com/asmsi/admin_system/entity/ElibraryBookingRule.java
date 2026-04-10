package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "elibrary_booking_rule")
public class ElibraryBookingRule {

    @Id
    private Long id = 1L;

    @Column(name = "max_advance_days", nullable = false)
    private Integer maxAdvanceDays = 30;

    @Column(name = "min_booking_minutes", nullable = false)
    private Integer minBookingMinutes = 60;

    @Column(name = "max_booking_minutes", nullable = false)
    private Integer maxBookingMinutes = 180;

    @Column(name = "max_computers_per_booking", nullable = false)
    private Integer maxComputersPerBooking = 40;

    @Column(name = "approval_required", nullable = false)
    private boolean approvalRequired = true;

    @Column(name = "allow_same_day_booking", nullable = false)
    private boolean allowSameDayBooking = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getMaxAdvanceDays() {
        return maxAdvanceDays;
    }

    public void setMaxAdvanceDays(Integer maxAdvanceDays) {
        this.maxAdvanceDays = maxAdvanceDays;
    }

    public Integer getMinBookingMinutes() {
        return minBookingMinutes;
    }

    public void setMinBookingMinutes(Integer minBookingMinutes) {
        this.minBookingMinutes = minBookingMinutes;
    }

    public Integer getMaxBookingMinutes() {
        return maxBookingMinutes;
    }

    public void setMaxBookingMinutes(Integer maxBookingMinutes) {
        this.maxBookingMinutes = maxBookingMinutes;
    }

    public Integer getMaxComputersPerBooking() {
        return maxComputersPerBooking;
    }

    public void setMaxComputersPerBooking(Integer maxComputersPerBooking) {
        this.maxComputersPerBooking = maxComputersPerBooking;
    }

    public boolean isApprovalRequired() {
        return approvalRequired;
    }

    public void setApprovalRequired(boolean approvalRequired) {
        this.approvalRequired = approvalRequired;
    }

    public boolean isAllowSameDayBooking() {
        return allowSameDayBooking;
    }

    public void setAllowSameDayBooking(boolean allowSameDayBooking) {
        this.allowSameDayBooking = allowSameDayBooking;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}