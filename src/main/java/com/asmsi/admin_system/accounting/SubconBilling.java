package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A subcontractor's billing to CBM on a subcontract: downpayment (advance), progress by % accomplished, or retention release.
 * Posting it creates the bill that Finance pays through a payment voucher.
 */
@Entity
@Table(name = "acc_subcon_billing")
public class SubconBilling {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 30) public String number;
    @Column(name = "contract_id", nullable = false) public Long contractId;
    @Column(name = "supplier_id", nullable = false) public Long supplierId;
    @Column(name = "job_order_id", nullable = false) public Long jobOrderId;
    @Column(nullable = false, length = 12) public String kind = "PROGRESS";
    @Column(name = "seq_no") public int seqNo = 1;
    @Column(name = "billing_date", nullable = false) public LocalDate billingDate;
    @Column(name = "invoice_no", length = 60) public String invoiceNo;
    @Column(name = "pct_to_date", precision = 7, scale = 3) public BigDecimal pctToDate = BigDecimal.ZERO;
    @Column(name = "gross_to_date", nullable = false, precision = 16, scale = 2) public BigDecimal grossToDate = BigDecimal.ZERO;
    @Column(name = "prev_gross", nullable = false, precision = 16, scale = 2) public BigDecimal prevGross = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal gross = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal recoupment = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal retention = BigDecimal.ZERO;
    @Column(name = "amount_due", nullable = false, precision = 16, scale = 2) public BigDecimal amountDue = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal vat = BigDecimal.ZERO;
    /** DRAFT · POSTED · VOID */
    @Column(nullable = false, length = 8) public String status = "DRAFT";
    @Column(name = "verified_by", length = 160) public String verifiedBy;
    @Column(length = 600) public String memo;
    @Column(name = "bill_id") public Long billId;
    @Column(name = "journal_id") public Long journalId;
    @Column(name = "prepared_by", length = 160) public String preparedBy;
    @Column(name = "prepared_at") public LocalDateTime preparedAt = LocalDateTime.now();
    @Column(name = "posted_by", length = 160) public String postedBy;
    @Column(name = "posted_at") public LocalDateTime postedAt;
    @Column(name = "void_reason", length = 300) public String voidReason;

    public Long getId() { return id; }
    public boolean isEditable() { return "DRAFT".equals(status); }
    public boolean isPosted() { return "POSTED".equals(status); }
    public String getTitle() {
        return switch (kind) { case "DOWNPAYMENT" -> "Downpayment (advance)"; case "RETENTION" -> "Retention release"; default -> "Progress billing no. " + seqNo; };
    }
}
