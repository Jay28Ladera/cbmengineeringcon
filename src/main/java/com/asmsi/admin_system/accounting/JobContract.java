package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Billing terms of one job order: contract amount (as billed to the client, VAT included when VAT applies), downpayment, retention, withholding. */
@Entity
@Table(name = "acc_job_contract")
public class JobContract {
    @Id @Column(name = "job_order_id") public Long jobOrderId;
    @Column(name = "contract_amount", nullable = false, precision = 16, scale = 2) public BigDecimal contractAmount = BigDecimal.ZERO;
    /** VAT = amounts include 12% VAT; NONE = no VAT. */
    @Column(name = "vat_mode", nullable = false, length = 8) public String vatMode = "VAT";
    @Column(name = "dp_pct", nullable = false, precision = 5, scale = 2) public BigDecimal dpPct = new BigDecimal("30");
    @Column(name = "retention_pct", nullable = false, precision = 5, scale = 2) public BigDecimal retentionPct = new BigDecimal("10");
    /** Creditable withholding tax the client deducts (2% for contractors). */
    @Column(name = "cwt_rate", nullable = false, precision = 5, scale = 2) public BigDecimal cwtRate = new BigDecimal("2");
    @Column(name = "term_days", nullable = false) public int termDays = 15;
    /** ITEMS = % accomplishment per quotation item; LUMP = one overall %. */
    @Column(nullable = false, length = 6) public String basis = "ITEMS";
    @Column(name = "client_ref", length = 80) public String clientRef;
    @Column(name = "bill_to", length = 300) public String billTo;
    @Column(name = "updated_by", length = 120) public String updatedBy;
    @Column(name = "updated_at") public LocalDateTime updatedAt;

    public Long getId() { return jobOrderId; }
    public boolean isVat() { return "VAT".equals(vatMode); }
    public BigDecimal getDpAmount() { return Ledger.r2(contractAmount.multiply(dpPct).divide(BigDecimal.valueOf(100), 6, java.math.RoundingMode.HALF_UP)); }
}
