package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A monthly expense (rent, internet, utilities) that becomes a draft-free bill each month with one click. */
@Entity
@Table(name = "acc_recurring_expense")
public class RecurringExpense {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "supplier_id", nullable = false) public Long supplierId;
    @Column(nullable = false, length = 300) public String description;
    @Column(name = "account_id", nullable = false) public Long accountId;
    @Column(name = "job_order_id") public Long jobOrderId;
    /** Usual amount; utilities can be changed when the month's bill is created. */
    @Column(nullable = false, precision = 14, scale = 2) public BigDecimal amount = BigDecimal.ZERO;
    @Column(name = "vat_mode", nullable = false, length = 10) public String vatMode = "INCLUSIVE";
    @Column(name = "ewt_rate", nullable = false, precision = 5, scale = 2) public BigDecimal ewtRate = BigDecimal.ZERO;
    @Column(name = "day_of_month", nullable = false) public int dayOfMonth = 1;
    @Column(name = "due_days", nullable = false) public int dueDays = 15;
    @Column(nullable = false) public boolean active = true;
    /** yyyy-MM of the last month billed. */
    @Column(name = "last_month", length = 7) public String lastMonth;
    @Column(name = "created_by", length = 120) public String createdBy;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();
    public Long getId() { return id; }
}
