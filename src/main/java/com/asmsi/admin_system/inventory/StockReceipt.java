package com.asmsi.admin_system.inventory;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A delivery received into stock (receiving report). */
@Entity @Table(name = "inv_stock_receipt")
public class StockReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 30) public String number;
    @Column(name = "received_on", nullable = false) public LocalDate receivedOn;
    @Column(name = "supplier_id") public Long supplierId;
    @Column(name = "po_id") public Long poId;
    @Column(name = "dr_number", length = 60) public String drNumber;
    @Column(name = "invoice_number", length = 60) public String invoiceNumber;
    @Column(name = "received_by", length = 120) public String receivedBy;
    @Column(length = 600) public String notes;
    @Column(nullable = false) public boolean voided;
    @Column(name = "void_reason", length = 300) public String voidReason;
    @Column(name = "created_by", length = 120) public String createdBy;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();
    public Long getId() { return id; }
}
