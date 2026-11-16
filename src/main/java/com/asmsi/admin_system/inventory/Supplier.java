package com.asmsi.admin_system.inventory;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A supplier of materials, tools or services. (Entities in this package use public fields: they hold ids, not relations.) */
@Entity @Table(name = "pur_supplier")
public class Supplier {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, length = 160) public String name;
    @Column(name = "contact_person", length = 120) public String contactPerson;
    @Column(length = 60) public String phone;
    @Column(length = 160) public String email;
    @Column(length = 300) public String address;
    @Column(length = 40) public String tin;
    /** VAT-registered supplier: their invoices carry 12% input VAT. Null = not stated (treated as VAT-registered). */
    @Column(name = "vat_registered") public Boolean vatRegistered;
    /** Expanded withholding tax withheld when paying them, in percent: 1 (goods), 2 (services), 5, 10, 15, or 0 (none). Null = 1. */
    @Column(name = "ewt_rate", precision = 5, scale = 2) public java.math.BigDecimal ewtRate;
    public boolean isVat() { return vatRegistered == null || vatRegistered; }
    public java.math.BigDecimal ewt() { return ewtRate == null ? (isSubcon() ? new java.math.BigDecimal("2") : java.math.BigDecimal.ONE) : ewtRate; }
    /** Subcontractors are kept with the suppliers (same bills, vouchers and withholding) and listed apart. */
    @Column(name = "subcon") public Boolean subcon;
    @Column(length = 80) public String trade;
    /** For BIR forms: CORPORATION or INDIVIDUAL, and an ATC that overrides the one worked out from the EWT rate. */
    @Column(name = "payee_type", length = 12) public String payeeType;
    @Column(length = 10) public String atc;
    public boolean isIndividual() { return "INDIVIDUAL".equals(payeeType); }
    @Column(name = "accreditation", length = 300) public String accreditation;
    public boolean isSubcon() { return Boolean.TRUE.equals(subcon); }
    /** e.g. COD, 30 days, 50% down. */
    @Column(name = "payment_terms", length = 120) public String paymentTerms;
    /** What they supply, free text: "Copper pipes, refrigerant, ducting". */
    @Column(length = 300) public String supplies;
    @Column(length = 1000) public String notes;
    @Column(nullable = false) public boolean active = true;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();
    public Long getId() { return id; }
}
