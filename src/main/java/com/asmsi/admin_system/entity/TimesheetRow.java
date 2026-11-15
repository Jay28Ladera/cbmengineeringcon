package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** Per-employee totals and notes for a period that are not tied to one day (night diff, remarks). */
@Entity
@Table(name = "hr_timesheet_row",
       uniqueConstraints = @UniqueConstraint(columnNames = {"period_id", "employee_id"}))
public class TimesheetRow {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "period_id", nullable = false) private Long periodId;
    @Column(name = "employee_id", nullable = false) private Long employeeId;
    @Column(name = "nd_hours", precision = 6, scale = 2) private BigDecimal ndHours;
    @Column(length = 600) private String remarks;

    public Long getId() { return id; }
    public Long getPeriodId() { return periodId; }
    public void setPeriodId(Long v) { periodId = v; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long v) { employeeId = v; }
    public BigDecimal getNdHours() { return ndHours; }
    public void setNdHours(BigDecimal v) { ndHours = v; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { remarks = v; }
}
