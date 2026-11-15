package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A locked payroll period posted to the ledger, and the release of the net pay. */
@Entity
@Table(name = "acc_payroll_posting")
public class PayrollPosting {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "period_id", nullable = false) public Long periodId;
    @Column(name = "period_label", length = 80) public String periodLabel;
    @Column(name = "post_date", nullable = false) public LocalDate postDate;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal earned = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal deductions = BigDecimal.ZERO;
    @Column(name = "net_pay", nullable = false, precision = 16, scale = 2) public BigDecimal net = BigDecimal.ZERO;
    @Column(name = "to_jobs", nullable = false, precision = 16, scale = 2) public BigDecimal toJobs = BigDecimal.ZERO;
    /** POSTED · RELEASED · VOID */
    @Column(nullable = false, length = 10) public String status = "POSTED";
    @Column(name = "journal_id") public Long journalId;
    @Column(name = "posted_by", length = 120) public String postedBy;
    @Column(name = "posted_at") public LocalDateTime postedAt = LocalDateTime.now();
    @Column(name = "release_bank_id") public Long releaseBankId;
    @Column(name = "release_date") public LocalDate releaseDate;
    @Column(name = "release_ref", length = 60) public String releaseRef;
    @Column(name = "release_journal_id") public Long releaseJournalId;
    @Column(name = "released_by", length = 120) public String releasedBy;
    @Column(name = "void_reason", length = 300) public String voidReason;
    public Long getId() { return id; }
    public boolean isActive() { return !"VOID".equals(status); }
}
