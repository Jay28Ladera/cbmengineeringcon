package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** One day on the time sheet: regular hours, overtime hours and minutes late (REG / OT / L). */
@Entity
@Table(name = "hr_time_entry",
       uniqueConstraints = @UniqueConstraint(columnNames = {"period_id", "employee_id", "work_date"}),
       indexes = @Index(columnList = "period_id"))
public class TimeEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "period_id", nullable = false) private Long periodId;
    @Column(name = "employee_id", nullable = false) private Long employeeId;
    @Column(name = "work_date", nullable = false) private LocalDate workDate;
    @Column(name = "reg_hours", precision = 5, scale = 2) private BigDecimal regHours;
    @Column(name = "ot_hours", precision = 5, scale = 2) private BigDecimal otHours;
    @Column(name = "late_minutes") private Integer lateMinutes;

    public boolean isEmpty() {
        return (regHours == null || regHours.signum() == 0) && (otHours == null || otHours.signum() == 0)
                && (lateMinutes == null || lateMinutes == 0);
    }

    public Long getId() { return id; }
    public Long getPeriodId() { return periodId; }
    public void setPeriodId(Long v) { periodId = v; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long v) { employeeId = v; }
    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate v) { workDate = v; }
    public BigDecimal getRegHours() { return regHours; }
    public void setRegHours(BigDecimal v) { regHours = v; }
    public BigDecimal getOtHours() { return otHours; }
    public void setOtHours(BigDecimal v) { otHours = v; }
    public Integer getLateMinutes() { return lateMinutes; }
    public void setLateMinutes(Integer v) { lateMinutes = v; }
}
