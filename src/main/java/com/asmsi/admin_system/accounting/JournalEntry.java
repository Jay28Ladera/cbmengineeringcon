package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A journal entry: equal debits and credits on a date. Most are posted automatically from the work they record
 * (a supplier bill, a payment, a stock issue…); source + sourceId point back to it. A posted entry is never edited:
 * it is voided by a reversing entry.
 */
@Entity
@Table(name = "acc_journal")
public class JournalEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 30) public String number;
    @Column(name = "entry_date", nullable = false) public LocalDate entryDate;
    @Column(nullable = false, length = 500) public String memo;
    /** MANUAL, OPENING, BILL, PAYMENT, ISSUE, ISSUE_VOID, ADJUSTMENT, REVERSAL … */
    @Column(nullable = false, length = 20) public String source = "MANUAL";
    @Column(name = "source_id") public Long sourceId;
    /** Document number shown with the entry, e.g. BL-2026-0003, DV-2026-0001, IS-2026-0002. */
    @Column(name = "source_ref", length = 40) public String sourceRef;
    /** POSTED or VOID (a VOID entry has a reversing entry pointing to it). */
    @Column(nullable = false, length = 8) public String status = "POSTED";
    @Column(name = "reverses_id") public Long reversesId;
    @Column(name = "reversed_by_id") public Long reversedById;
    @Column(name = "created_by", length = 120) public String createdBy;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "void_reason", length = 300) public String voidReason;

    public Long getId() { return id; }
    public boolean isPosted() { return "POSTED".equals(status); }
}
