package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One rate increase or correction, kept so HR can see an employee's rate history. */
@Entity
@Table(name = "hr_employee_rate_change")
public class EmployeeRateChange {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "employee_id", nullable = false) private Long employeeId;
    @Column(name = "effective_date") private LocalDate effectiveDate;
    @Column(name = "old_daily", precision = 12, scale = 2) private BigDecimal oldDaily;
    @Column(name = "new_daily", precision = 12, scale = 2) private BigDecimal newDaily;
    @Column(name = "old_monthly", precision = 12, scale = 2) private BigDecimal oldMonthly;
    @Column(name = "new_monthly", precision = 12, scale = 2) private BigDecimal newMonthly;
    @Column(length = 300) private String note;
    @Column(name = "changed_by", length = 120) private String changedBy;
    @Column(name = "created_at") private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long v) { employeeId = v; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate v) { effectiveDate = v; }
    public BigDecimal getOldDaily() { return oldDaily; }
    public void setOldDaily(BigDecimal v) { oldDaily = v; }
    public BigDecimal getNewDaily() { return newDaily; }
    public void setNewDaily(BigDecimal v) { newDaily = v; }
    public BigDecimal getOldMonthly() { return oldMonthly; }
    public void setOldMonthly(BigDecimal v) { oldMonthly = v; }
    public BigDecimal getNewMonthly() { return newMonthly; }
    public void setNewMonthly(BigDecimal v) { newMonthly = v; }
    public String getNote() { return note; }
    public void setNote(String v) { note = v; }
    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String v) { changedBy = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
