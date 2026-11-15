package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** One payroll cut-off, normally the 26th to the 10th or the 11th to the 25th. */
@Entity
@Table(name = "hr_payroll_period")
public class PayrollPeriod {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    /** OPEN while time and adjustments are being entered; LOCKED once payroll is final. */
    @Column(nullable = false, length = 10) private String status = "OPEN";
    @Column(length = 600) private String notes;
    @Column(name = "locked_at") private LocalDateTime lockedAt;
    @Column(name = "locked_by", length = 120) private String lockedBy;
    @Column(name = "created_at") private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isLocked() { return "LOCKED".equalsIgnoreCase(status); }

    /** First cut-off of the month: the one that ends on or before the 15th (26–10). */
    public boolean isFirstCutoff() { return endDate.getDayOfMonth() <= 15; }

    public String getLabel() {
        DateTimeFormatter full = DateTimeFormatter.ofPattern("MMM d, yyyy");
        String start = startDate.format(startDate.getYear() == endDate.getYear() ? DateTimeFormatter.ofPattern("MMM d") : full);
        return start + " – " + endDate.format(full);
    }

    public Long getId() { return id; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate v) { startDate = v; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate v) { endDate = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { notes = v; }
    public LocalDateTime getLockedAt() { return lockedAt; }
    public void setLockedAt(LocalDateTime v) { lockedAt = v; }
    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String v) { lockedBy = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
