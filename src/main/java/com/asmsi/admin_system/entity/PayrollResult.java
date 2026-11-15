package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Frozen pay line of one employee, saved when a period is locked, so later rate or rule changes
 * do not alter payroll that has already been released.
 */
@Entity
@Table(name = "hr_payroll_result",
       uniqueConstraints = @UniqueConstraint(columnNames = {"period_id", "employee_id"}))
public class PayrollResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "period_id", nullable = false) private Long periodId;
    @Column(name = "employee_id", nullable = false) private Long employeeId;
    @Column(name = "net_pay", precision = 12, scale = 2) private BigDecimal netPay;
    @Column(name = "line_json", columnDefinition = "text") private String lineJson;

    public Long getId() { return id; }
    public Long getPeriodId() { return periodId; }
    public void setPeriodId(Long v) { periodId = v; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long v) { employeeId = v; }
    public BigDecimal getNetPay() { return netPay; }
    public void setNetPay(BigDecimal v) { netPay = v; }
    public String getLineJson() { return lineJson; }
    public void setLineJson(String v) { lineJson = v; }
}
