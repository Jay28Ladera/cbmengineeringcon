package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One payment out of a petty cash fund, posted at once: Dr expense (job) / Cr the fund. */
@Entity
@Table(name = "acc_pcv")
public class PettyCashVoucher {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 30) public String number;
    @Column(name = "fund_id", nullable = false) public Long fundId;
    @Column(name = "voucher_date", nullable = false) public LocalDate voucherDate;
    @Column(nullable = false, length = 160) public String payee;
    @Column(nullable = false, length = 300) public String description;
    @Column(name = "account_id", nullable = false) public Long accountId;
    @Column(name = "job_order_id") public Long jobOrderId;
    @Column(nullable = false, precision = 14, scale = 2) public BigDecimal amount = BigDecimal.ZERO;
    @Column(name = "receipt_no", length = 60) public String receiptNo;
    @Column(name = "received_by", length = 160) public String receivedBy;
    /** POSTED · VOID */
    @Column(nullable = false, length = 8) public String status = "POSTED";
    @Column(name = "replenishment_id") public Long replenishmentId;
    @Column(name = "journal_id") public Long journalId;
    @Column(name = "created_by", length = 120) public String createdBy;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "void_reason", length = 300) public String voidReason;
    public Long getId() { return id; }
    public boolean isPosted() { return "POSTED".equals(status); }
}
