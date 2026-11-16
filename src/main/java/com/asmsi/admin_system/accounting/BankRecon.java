package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One bank statement reconciled against the ledger: the lines ticked as cleared must add up to the statement's ending balance. */
@Entity
@Table(name = "acc_bank_recon")
public class BankRecon {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "bank_id", nullable = false) public Long bankId;
    @Column(name = "statement_date", nullable = false) public LocalDate statementDate;
    @Column(name = "opening_balance", nullable = false, precision = 16, scale = 2) public BigDecimal openingBalance = BigDecimal.ZERO;
    @Column(name = "statement_balance", nullable = false, precision = 16, scale = 2) public BigDecimal statementBalance = BigDecimal.ZERO;
    /** DRAFT · DONE */
    @Column(nullable = false, length = 6) public String status = "DRAFT";
    @Column(name = "created_by", length = 120) public String createdBy;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "completed_by", length = 120) public String completedBy;
    @Column(name = "completed_at") public LocalDateTime completedAt;
    public Long getId() { return id; }
    public boolean isDone() { return "DONE".equals(status); }
}
