package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** One debit or credit of a journal entry, optionally tagged with a job order and a supplier for job costing and subledgers. */
@Entity
@Table(name = "acc_journal_line", indexes = {
        @Index(name = "ix_acc_line_journal", columnList = "journal_id"),
        @Index(name = "ix_acc_line_account", columnList = "account_id"),
        @Index(name = "ix_acc_line_job", columnList = "job_order_id")})
public class JournalLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "journal_id", nullable = false) public Long journalId;
    @Column(name = "account_id", nullable = false) public Long accountId;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal debit = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal credit = BigDecimal.ZERO;
    @Column(length = 300) public String memo;
    @Column(name = "job_order_id") public Long jobOrderId;
    @Column(name = "supplier_id") public Long supplierId;
    @Column(name = "sort_order") public int sortOrder;
    /** Bank reconciliation in which this bank/cash line was matched to the statement (null = not yet cleared). */
    @Column(name = "recon_id") public Long reconId;

    public JournalLine() {}
    public static JournalLine dr(Long accountId, BigDecimal amount, String memo) { JournalLine l = new JournalLine(); l.accountId = accountId; l.debit = amount; l.memo = memo; return l; }
    public static JournalLine cr(Long accountId, BigDecimal amount, String memo) { JournalLine l = new JournalLine(); l.accountId = accountId; l.credit = amount; l.memo = memo; return l; }
    public JournalLine job(Long jobOrderId) { this.jobOrderId = jobOrderId; return this; }
    public JournalLine supplier(Long supplierId) { this.supplierId = supplierId; return this; }
}
