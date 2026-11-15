package com.asmsi.admin_system.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "pur_purchase_order")
public class PurchaseOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 30) public String number;
    @Column(name = "supplier_id", nullable = false) public Long supplierId;
    /** Job order the purchase is for; null = for warehouse stock. */
    @Column(name = "job_order_id") public Long jobOrderId;
    @Column(name = "canvass_id") public Long canvassId;
    @Column(name = "order_date", nullable = false) public LocalDate orderDate;
    @Column(name = "expected_date") public LocalDate expectedDate;
    /** DRAFT → ORDERED → PARTIAL → RECEIVED; or CANCELLED. */
    @Column(nullable = false, length = 12) public String status = "DRAFT";
    @Column(name = "deliver_to", length = 300) public String deliverTo;
    @Column(name = "payment_terms", length = 120) public String paymentTerms;
    @Column(name = "tax_rate", precision = 6, scale = 2) public BigDecimal taxRate = BigDecimal.ZERO;
    /** CASH or CHEQUE (from the canvass). */
    @Column(name = "payment_mode", length = 10) public String paymentMode;
    /** Days of credit: 0 = pay before ordering (cash/COD), 30, 60, 90. Null = not set. */
    @Column(name = "term_days") public Integer termDays;
    @Column(name = "pdc") public Boolean pdc;
    /** Prices include 12% VAT (a VAT-registered supplier's usual quote). Stock is then valued without the VAT. */
    @Column(name = "vat_inclusive") public Boolean vatInclusive;
    /** Payment must be released before the order can be placed. */
    public boolean isPayFirst() { return termDays != null && termDays == 0; }
    public boolean isVatInclusive() { return Boolean.TRUE.equals(vatInclusive); }
    /** Whether the unit prices on this PO already include VAT: VAT added on top → no; else the PO's own setting; else the supplier's VAT status. */
    public boolean pricesIncludeVat(Supplier s) {
        if (taxRate != null && taxRate.signum() > 0) return false;
        if (vatInclusive != null) return vatInclusive;
        return s == null || s.isVat();
    }
    public String termsLabel() {
        java.util.List<String> p = new java.util.ArrayList<>();
        if (paymentMode != null) p.add("CASH".equals(paymentMode) ? "Cash" : "Cheque");
        if (termDays != null) p.add(termDays == 0 ? "pay before ordering" : termDays + " days");
        if (Boolean.TRUE.equals(pdc)) p.add("PDC");
        return String.join(" · ", p);
    }
    @Column(length = 1000) public String notes;
    @Column(name = "created_by", length = 120) public String createdBy;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "ordered_by", length = 120) public String orderedBy;
    @Column(name = "ordered_at") public LocalDateTime orderedAt;
    public Long getId() { return id; }
    public boolean isEditable() { return "DRAFT".equals(status); }
    public boolean isReceivable() { return "ORDERED".equals(status) || "PARTIAL".equals(status); }
}
