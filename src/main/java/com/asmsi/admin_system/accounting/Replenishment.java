package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Petty cash replenishment: a cheque (or cash) from a bank refills the fund for the vouchers and advances paid out of it. */
@Entity
@Table(name = "acc_pcf_replenishment")
public class Replenishment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 30) public String number;
    @Column(name = "fund_id", nullable = false) public Long fundId;
    @Column(name = "bank_id", nullable = false) public Long bankId;
    @Column(name = "repl_date", nullable = false) public LocalDate replDate;
    @Column(name = "cheque_no", length = 40) public String chequeNo;
    @Column(nullable = false, precision = 14, scale = 2) public BigDecimal amount = BigDecimal.ZERO;
    @Column(nullable = false, length = 8) public String status = "POSTED";
    @Column(name = "journal_id") public Long journalId;
    @Column(name = "created_by", length = 120) public String createdBy;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();
    public Long getId() { return id; }
}
