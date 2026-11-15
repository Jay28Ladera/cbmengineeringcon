package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A supplier bill (accounts payable): what we owe a supplier for a PO or an expense. Posting it books
 * Dr goods-received-not-billed or the expense, Dr input VAT, Cr accounts payable. Payment vouchers pay it.
 */
@Entity
@Table(name = "acc_bill")
public class Bill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 30) public String number;
    @Column(name = "supplier_id", nullable = false) public Long supplierId;
    /** Supplier's sales invoice, delivery receipt or billing number. */
    @Column(name = "invoice_no", length = 60) public String invoiceNo;
    @Column(name = "bill_date", nullable = false) public LocalDate billDate;
    @Column(name = "due_date", nullable = false) public LocalDate dueDate;
    @Column(name = "po_id") public Long poId;
    @Column(name = "job_order_id") public Long jobOrderId;
    /** PO (materials on a purchase order) or EXPENSE (anything else). */
    @Column(nullable = false, length = 8) public String kind = "EXPENSE";
    /** INCLUSIVE (prices include 12% VAT), EXCLUSIVE (VAT added on top) or NONE. */
    @Column(name = "vat_mode", nullable = false, length = 10) public String vatMode = "INCLUSIVE";
    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2) public BigDecimal vatRate = new BigDecimal("12");
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal gross = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal vat = BigDecimal.ZERO;
    /** Expanded withholding tax rate (%), applied to the amount net of VAT when paying. */
    @Column(name = "ewt_rate", nullable = false, precision = 5, scale = 2) public BigDecimal ewtRate = BigDecimal.ONE;
    /** Gross amount settled by released payment vouchers (including the tax withheld). */
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal paid = BigDecimal.ZERO;
    /** OPEN, PARTIAL, PAID or VOID */
    @Column(nullable = false, length = 8) public String status = "OPEN";
    @Column(length = 500) public String memo;
    @Column(name = "created_by", length = 120) public String createdBy;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "void_reason", length = 300) public String voidReason;

    public Long getId() { return id; }
    public BigDecimal getBalance() { return gross.subtract(paid); }
    /** Payables account this bill sits in: subcontractors apart from suppliers. */
    public String apCode() { return "SUBCON".equals(kind) ? Acct.AP_SUBCONTRACTORS : Acct.AP_SUPPLIERS; }
    public BigDecimal getNetOfVat() { return gross.subtract(vat); }
    public boolean isOverdue() { return ("OPEN".equals(status) || "PARTIAL".equals(status)) && dueDate.isBefore(LocalDate.now()); }
    public boolean isPayable() { return "OPEN".equals(status) || "PARTIAL".equals(status); }
    /** Days until due (negative when overdue). */
    public long getDueInDays() { return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), dueDate); }
    public String getVatLabel() {
        return switch (vatMode) { case "INCLUSIVE" -> "VAT-inclusive"; case "EXCLUSIVE" -> "VAT added " + vatRate.stripTrailingZeros().toPlainString() + "%"; default -> "No VAT"; };
    }
}
