package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** An extra earning or deduction for one employee in one period (RECEIVABLE / DEDUCTIONS sheets). */
@Entity
@Table(name = "hr_pay_adjustment", indexes = @Index(columnList = "period_id"))
public class PayAdjustment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "period_id", nullable = false) private Long periodId;
    @Column(name = "employee_id", nullable = false) private Long employeeId;
    /** A {@link com.asmsi.admin_system.payroll.PayItem} name. */
    @Column(nullable = false, length = 24) private String kind;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(length = 200) private String description;
    /** Set when the line was posted from a loan / cash advance schedule when the period was locked. */
    @Column(name = "plan_id") private Long planId;
    @Column(name = "created_at") private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getPeriodId() { return periodId; }
    public void setPeriodId(Long v) { periodId = v; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long v) { employeeId = v; }
    public String getKind() { return kind; }
    public void setKind(String v) { kind = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { amount = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long v) { planId = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
