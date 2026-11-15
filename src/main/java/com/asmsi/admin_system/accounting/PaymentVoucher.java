package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A disbursement voucher: paying one supplier for one or more bills, in cash or by cheque.
 * DRAFT → FOR_APPROVAL → APPROVED (CEO, with signature) → RELEASED (posted: Dr AP, Cr cash/bank, Cr EWT payable).
 * CANCELLED before release; VOID after release (the posting is reversed and the bills reopen).
 */
@Entity
@Table(name = "acc_voucher")
public class PaymentVoucher {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 30) public String number;
    @Column(name = "supplier_id", nullable = false) public Long supplierId;
    @Column(name = "voucher_date", nullable = false) public LocalDate voucherDate;
    /** CASH or CHEQUE */
    @Column(nullable = false, length = 8) public String method = "CHEQUE";
    @Column(name = "bank_id") public Long bankId;
    @Column(name = "cheque_no", length = 40) public String chequeNo;
    /** Cheque date; a date after the release date makes it a post-dated cheque (PDC). */
    @Column(name = "cheque_date") public LocalDate chequeDate;
    @Column(length = 600) public String particulars;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal gross = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal ewt = BigDecimal.ZERO;
    @Column(name = "net_amount", nullable = false, precision = 16, scale = 2) public BigDecimal net = BigDecimal.ZERO;
    @Column(nullable = false, length = 12) public String status = "DRAFT";

    @Column(name = "prepared_by", length = 160) public String preparedBy;
    @Column(name = "prepared_by_id") public Long preparedById;
    @Column(name = "prepared_at") public LocalDateTime preparedAt = LocalDateTime.now();
    @Column(name = "submitted_at") public LocalDateTime submittedAt;
    @Column(name = "approved_by", length = 160) public String approvedBy;
    @Column(name = "approved_by_id") public Long approvedById;
    @Column(name = "approved_title", length = 160) public String approvedTitle;
    @Column(name = "approved_at") public LocalDateTime approvedAt;
    @Column(name = "approved_sig", columnDefinition = "bytea") public byte[] approvedSig;
    @Column(name = "approved_sig_type", length = 40) public String approvedSigType;
    @Column(name = "return_note", length = 600) public String returnNote;
    @Column(name = "returned_by", length = 160) public String returnedBy;
    @Column(name = "released_by", length = 160) public String releasedBy;
    @Column(name = "released_at") public LocalDateTime releasedAt;
    @Column(name = "release_date") public LocalDate releaseDate;
    /** Name of the supplier's representative who received the cash or cheque. */
    @Column(name = "received_by", length = 160) public String receivedBy;
    /** Supplier's official receipt number for this payment. */
    @Column(name = "or_no", length = 60) public String orNo;
    @Column(name = "journal_id") public Long journalId;
    @Column(name = "void_reason", length = 300) public String voidReason;

    public Long getId() { return id; }
    public boolean isPdc() { return "CHEQUE".equals(method) && chequeDate != null && chequeDate.isAfter(releaseDate != null ? releaseDate : voucherDate); }
    public boolean isEditable() { return "DRAFT".equals(status); }
    public String getApprovedSigUrl() {
        return approvedSig == null ? null : "data:" + (approvedSigType == null ? "image/png" : approvedSigType) + ";base64," + java.util.Base64.getEncoder().encodeToString(approvedSig);
    }
    public String getStatusLabel() {
        return switch (status) { case "FOR_APPROVAL" -> "For approval"; case "APPROVED" -> "Approved"; case "RELEASED" -> "Released"; case "CANCELLED" -> "Cancelled"; case "VOID" -> "Void"; default -> "Draft"; };
    }
}
