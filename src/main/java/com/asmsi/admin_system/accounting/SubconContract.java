package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A subcontract on a job order: the scope a subcontractor does for an agreed amount, with downpayment and retention. */
@Entity
@Table(name = "acc_subcon_contract")
public class SubconContract {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 30) public String number;
    @Column(name = "supplier_id", nullable = false) public Long supplierId;
    @Column(name = "job_order_id", nullable = false) public Long jobOrderId;
    @Column(nullable = false, length = 600) public String scope;
    /** As billed by the subcontractor (VAT included when VAT applies). */
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal amount = BigDecimal.ZERO;
    @Column(name = "vat_mode", nullable = false, length = 8) public String vatMode = "VAT";
    @Column(name = "dp_pct", nullable = false, precision = 5, scale = 2) public BigDecimal dpPct = BigDecimal.ZERO;
    @Column(name = "retention_pct", nullable = false, precision = 5, scale = 2) public BigDecimal retentionPct = new BigDecimal("10");
    @Column(name = "ewt_rate", nullable = false, precision = 5, scale = 2) public BigDecimal ewtRate = new BigDecimal("2");
    @Column(name = "start_date") public LocalDate startDate;
    @Column(name = "end_date") public LocalDate endDate;
    /** ACTIVE · DONE · CANCELLED */
    @Column(nullable = false, length = 10) public String status = "ACTIVE";
    @Column(length = 600) public String notes;
    @Column(name = "created_by", length = 120) public String createdBy;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public boolean isVat() { return "VAT".equals(vatMode); }
    public BigDecimal getDpAmount() { return ClientBillingService.pctOf(amount, dpPct); }
}
