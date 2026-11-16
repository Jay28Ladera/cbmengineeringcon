package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One month's depreciation, posted as one journal entry. */
@Entity
@Table(name = "acc_depreciation_run")
public class DepreciationRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    /** Last day of the month depreciated. */
    @Column(name = "month_end", nullable = false) public LocalDate monthEnd;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal total = BigDecimal.ZERO;
    @Column(name = "asset_count") public int assetCount;
    @Column(name = "journal_id") public Long journalId;
    /** POSTED or VOID */
    @Column(nullable = false, length = 10) public String status = "POSTED";
    @Column(name = "posted_by", length = 120) public String postedBy;
    @Column(name = "posted_at") public LocalDateTime postedAt = LocalDateTime.now();
    public Long getId() { return id; }
    public boolean isPosted() { return "POSTED".equals(status); }
}
