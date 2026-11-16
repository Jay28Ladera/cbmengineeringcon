package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** A line imported from the bank's statement file (+ deposit, − withdrawal), matched to a ledger line when found. */
@Entity
@Table(name = "acc_bank_stmt_line")
public class StatementLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "recon_id", nullable = false) public Long reconId;
    @Column(name = "txn_date") public LocalDate txnDate;
    @Column(length = 300) public String description;
    @Column(length = 60) public String reference;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal amount = BigDecimal.ZERO;
    @Column(name = "matched_line_id") public Long matchedLineId;
    /** Ignored by the user (e.g. already recorded another way). */
    @Column(nullable = false) public boolean ignored;
    public Long getId() { return id; }
}
