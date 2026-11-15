package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Payment of taxes withheld, VAT or government contributions to BIR, SSS, PhilHealth or Pag-IBIG. */
@Entity
@Table(name = "acc_remittance")
public class Remittance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 30) public String number;
    /** EWT · WTC · VAT · SSS · PHILHEALTH · PAGIBIG */
    @Column(nullable = false, length = 12) public String kind;
    @Column(name = "period_label", length = 60) public String periodLabel;
    @Column(name = "remit_date", nullable = false) public LocalDate remitDate;
    @Column(name = "bank_id", nullable = false) public Long bankId;
    @Column(length = 60) public String reference;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal amount = BigDecimal.ZERO;
    @Column(name = "credit_used", nullable = false, precision = 16, scale = 2) public BigDecimal creditUsed = BigDecimal.ZERO;
    @Column(nullable = false, length = 8) public String status = "POSTED";
    @Column(name = "journal_id") public Long journalId;
    @Column(name = "created_by", length = 120) public String createdBy;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "void_reason", length = 300) public String voidReason;
    public Long getId() { return id; }
}
