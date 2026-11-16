package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A year's income and expenses closed to retained earnings. */
@Entity
@Table(name = "acc_year_close")
public class YearClose {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "fiscal_year", nullable = false) public int year;
    @Column(name = "net_income", nullable = false, precision = 16, scale = 2) public BigDecimal netIncome = BigDecimal.ZERO;
    @Column(name = "journal_id") public Long journalId;
    @Column(nullable = false, length = 8) public String status = "CLOSED";
    @Column(name = "closed_by", length = 120) public String closedBy;
    @Column(name = "closed_at") public LocalDateTime closedAt = LocalDateTime.now();
    public Long getId() { return id; }
}
