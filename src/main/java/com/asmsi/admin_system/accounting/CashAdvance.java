package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Cash given to an employee for a purpose (site purchases, travel), later liquidated with receipts; the unspent, unreturned part becomes a payroll deduction. */
@Entity
@Table(name = "acc_cash_advance")
public class CashAdvance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 30) public String number;
    @Column(name = "employee_id", nullable = false) public Long employeeId;
    @Column(name = "employee_name", nullable = false, length = 160) public String employeeName;
    @Column(name = "source_id", nullable = false) public Long sourceId;
    @Column(name = "advance_date", nullable = false) public LocalDate advanceDate;
    @Column(nullable = false, precision = 14, scale = 2) public BigDecimal amount = BigDecimal.ZERO;
    @Column(nullable = false, length = 300) public String purpose;
    @Column(name = "job_order_id") public Long jobOrderId;
    @Column(name = "due_date") public LocalDate dueDate;
    /** OPEN · LIQUIDATED · VOID */
    @Column(nullable = false, length = 10) public String status = "OPEN";
    @Column(nullable = false, precision = 14, scale = 2) public BigDecimal spent = BigDecimal.ZERO;
    @Column(nullable = false, precision = 14, scale = 2) public BigDecimal returned = BigDecimal.ZERO;
    @Column(name = "to_payroll", nullable = false, precision = 14, scale = 2) public BigDecimal toPayroll = BigDecimal.ZERO;
    @Column(name = "plan_id") public Long planId;
    @Column(name = "liquidated_at") public LocalDateTime liquidatedAt;
    @Column(name = "liquidated_by", length = 120) public String liquidatedBy;
    @Column(name = "replenishment_id") public Long replenishmentId;
    @Column(name = "created_by", length = 120) public String createdBy;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "void_reason", length = 300) public String voidReason;
    public Long getId() { return id; }
    public boolean isOpen() { return "OPEN".equals(status); }
    public boolean isOverdue() { return isOpen() && dueDate != null && dueDate.isBefore(LocalDate.now()); }
}
