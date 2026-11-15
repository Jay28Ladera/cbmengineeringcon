package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Money received from a client against one or more billings of a job; the client may withhold creditable tax (CWT). */
@Entity
@Table(name = "acc_collection")
public class ClientCollection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 30) public String number;
    @Column(name = "job_order_id", nullable = false) public Long jobOrderId;
    @Column(name = "received_date", nullable = false) public LocalDate receivedDate;
    /** CASH · CHEQUE · TRANSFER */
    @Column(nullable = false, length = 10) public String method = "CHEQUE";
    @Column(name = "bank_id", nullable = false) public Long bankId;
    @Column(name = "cheque_no", length = 40) public String chequeNo;
    @Column(name = "cheque_bank", length = 80) public String chequeBank;
    @Column(name = "cheque_date") public LocalDate chequeDate;
    /** CBM's official receipt number (from the BIR-registered booklet or e-receipt). */
    @Column(name = "or_no", length = 40) public String orNo;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal applied = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal cwt = BigDecimal.ZERO;
    @Column(name = "cash_amount", nullable = false, precision = 16, scale = 2) public BigDecimal cash = BigDecimal.ZERO;
    @Column(name = "form2307") public boolean form2307;
    @Column(name = "form2307_at") public LocalDate form2307At;
    @Column(length = 500) public String memo;
    /** POSTED · VOID */
    @Column(nullable = false, length = 8) public String status = "POSTED";
    @Column(name = "created_by", length = 120) public String createdBy;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "journal_id") public Long journalId;
    @Column(name = "void_reason", length = 300) public String voidReason;

    public Long getId() { return id; }
    public boolean isPosted() { return "POSTED".equals(status); }
    public String getMethodLabel() { return switch (method) { case "CASH" -> "Cash"; case "TRANSFER" -> "Bank transfer"; default -> "Cheque"; }; }
    public boolean isPdc() { return "CHEQUE".equals(method) && chequeDate != null && chequeDate.isAfter(receivedDate); }
}
