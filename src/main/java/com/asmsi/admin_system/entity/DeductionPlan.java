package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A loan, cash advance, sinking fund or other deduction taken a fixed amount per cut-off until
 * the balance reaches zero. With no total amount (e.g. sinking fund) it runs until stopped.
 */
@Entity
@Table(name = "hr_deduction_plan")
public class DeductionPlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "employee_id", nullable = false) private Long employeeId;
    /** LOAN, CASH_ADVANCE, SINKING or OTHER_DEDUCTION. */
    @Column(nullable = false, length = 24) private String kind;
    @Column(length = 200) private String description;
    @Column(name = "total_amount", precision = 12, scale = 2) private BigDecimal totalAmount;
    @Column(name = "per_cutoff", nullable = false, precision = 12, scale = 2) private BigDecimal perCutoff;
    @Column(precision = 12, scale = 2) private BigDecimal balance;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_at") private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isOpenEnded() { return totalAmount == null; }

    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long v) { employeeId = v; }
    public String getKind() { return kind; }
    public void setKind(String v) { kind = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal v) { totalAmount = v; }
    public BigDecimal getPerCutoff() { return perCutoff; }
    public void setPerCutoff(BigDecimal v) { perCutoff = v; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal v) { balance = v; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate v) { startDate = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { active = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
