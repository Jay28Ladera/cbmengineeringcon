package com.asmsi.admin_system.inventory;

import jakarta.persistence.*;

/** A supplier asked to quote on a canvass (one column of the comparison). */
@Entity @Table(name = "pur_canvass_supplier", uniqueConstraints = @UniqueConstraint(columnNames = {"canvass_id", "supplier_id"}))
public class CanvassSupplier {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "canvass_id", nullable = false) public Long canvassId;
    @Column(name = "supplier_id", nullable = false) public Long supplierId;
    /** Their terms for this quote: delivery days, validity, payment. */
    @Column(length = 300) public String terms;
    /** The supplier's own quotation number. */
    @Column(name = "quote_ref", length = 60) public String quoteRef;
    @Column(name = "quote_date") public java.time.LocalDate quoteDate;
    /** Days from order to delivery, as quoted. */
    @Column(name = "delivery_days") public Integer deliveryDays;
    @Column(name = "valid_until") public java.time.LocalDate validUntil;
    /** Their prices include VAT (VAT) or not (NON-VAT). Null = not stated. */
    @Column(name = "vat_inclusive") public Boolean vatInclusive;
    /** CASH or CHEQUE. */
    @Column(name = "payment_mode", length = 10) public String paymentMode;
    /** Days of credit: 0 (on delivery), 30, 60 or 90. */
    @Column(name = "term_days") public Integer termDays;
    /** Paid by post-dated cheque. */
    @Column(name = "pdc") public Boolean pdc;
    public boolean isPdc() { return Boolean.TRUE.equals(pdc); }
    /** "Cheque · 30 days · PDC" style summary. */
    public String paymentSummary() {
        java.util.List<String> p = new java.util.ArrayList<>();
        if (paymentMode != null) p.add("CASH".equals(paymentMode) ? "Cash" : "Cheque");
        if (termDays != null) p.add(termDays == 0 ? "pay before ordering" : termDays + " days");
        if (isPdc()) p.add("PDC");
        return String.join(" · ", p);
    }
    public Long getId() { return id; }
}
