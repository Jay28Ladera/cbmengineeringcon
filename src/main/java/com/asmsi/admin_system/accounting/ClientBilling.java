package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A billing to the client for one job order: downpayment, progress (by % accomplishment) or retention release.
 * Amounts are as the client pays them (VAT included when the contract has VAT). Posted when approved (issued).
 */
@Entity
@Table(name = "acc_client_billing")
public class ClientBilling {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 30) public String number;
    @Column(name = "job_order_id", nullable = false) public Long jobOrderId;
    /** DOWNPAYMENT · PROGRESS · RETENTION */
    @Column(nullable = false, length = 12) public String kind = "PROGRESS";
    /** Running number of this kind within the job: Progress billing no. 3. */
    @Column(name = "seq_no") public int seqNo = 1;
    @Column(name = "billing_date", nullable = false) public LocalDate billingDate;
    @Column(name = "due_date", nullable = false) public LocalDate dueDate;
    @Column(name = "as_of") public LocalDate asOf;
    @Column(name = "pct_to_date", precision = 7, scale = 3) public BigDecimal pctToDate = BigDecimal.ZERO;
    @Column(name = "gross_to_date", nullable = false, precision = 16, scale = 2) public BigDecimal grossToDate = BigDecimal.ZERO;
    @Column(name = "prev_gross", nullable = false, precision = 16, scale = 2) public BigDecimal prevGross = BigDecimal.ZERO;
    /** Value of work (or downpayment / retention) billed this time. */
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal gross = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal recoupment = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal retention = BigDecimal.ZERO;
    @Column(name = "amount_due", nullable = false, precision = 16, scale = 2) public BigDecimal amountDue = BigDecimal.ZERO;
    /** Output VAT on this billing (already netted for the downpayment's VAT). */
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal vat = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal collected = BigDecimal.ZERO;
    /** DRAFT · FOR_APPROVAL · ISSUED · PARTIAL · PAID · VOID */
    @Column(nullable = false, length = 12) public String status = "DRAFT";
    @Column(length = 600) public String memo;

    @Column(name = "prepared_by", length = 160) public String preparedBy;
    @Column(name = "prepared_by_id") public Long preparedById;
    @Column(name = "prepared_at") public LocalDateTime preparedAt = LocalDateTime.now();
    @Column(name = "submitted_at") public LocalDateTime submittedAt;
    @Column(name = "approved_by", length = 160) public String approvedBy;
    @Column(name = "approved_by_id") public Long approvedById;
    @Column(name = "approved_title", length = 160) public String approvedTitle;
    @Column(name = "approved_at") public LocalDateTime approvedAt;
    @Lob @Basic(fetch = FetchType.LAZY) @Column(name = "approved_sig") public byte[] approvedSig;
    @Column(name = "approved_sig_type", length = 40) public String approvedSigType;
    @Column(name = "return_note", length = 600) public String returnNote;
    @Column(name = "returned_by", length = 160) public String returnedBy;
    @Column(name = "public_token", length = 60, unique = true) public String publicToken;
    @Column(name = "sent_at") public LocalDateTime sentAt;
    @Column(name = "sent_to", length = 300) public String sentTo;
    @Column(name = "viewed_at") public LocalDateTime viewedAt;
    @Column(name = "journal_id") public Long journalId;
    @Column(name = "void_reason", length = 300) public String voidReason;

    public Long getId() { return id; }
    public BigDecimal getBalance() { return amountDue.subtract(collected); }
    public boolean isEditable() { return "DRAFT".equals(status); }
    public boolean isIssued() { return "ISSUED".equals(status) || "PARTIAL".equals(status) || "PAID".equals(status); }
    public boolean isOpen() { return "ISSUED".equals(status) || "PARTIAL".equals(status); }
    public boolean isOverdue() { return isOpen() && dueDate.isBefore(LocalDate.now()); }
    public long getDaysOverdue() { return Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(dueDate, LocalDate.now())); }
    public String getTitle() {
        return switch (kind) { case "DOWNPAYMENT" -> "Downpayment billing"; case "RETENTION" -> "Retention release"; default -> "Progress billing no. " + seqNo; };
    }
    public String getStatusLabel() {
        return switch (status) { case "FOR_APPROVAL" -> "For approval"; case "ISSUED" -> "Issued"; case "PARTIAL" -> "Partly paid"; case "PAID" -> "Paid"; case "VOID" -> "Void"; default -> "Draft"; };
    }
    public String getApprovedSigUrl() {
        return approvedSig == null ? null : "data:" + (approvedSigType == null ? "image/png" : approvedSigType) + ";base64," + java.util.Base64.getEncoder().encodeToString(approvedSig);
    }
}
