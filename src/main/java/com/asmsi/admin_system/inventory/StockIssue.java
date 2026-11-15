package com.asmsi.admin_system.inventory;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Materials released from the warehouse, normally to a job order (they become its material cost). */
@Entity @Table(name = "inv_stock_issue")
public class StockIssue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 30) public String number;
    @Column(name = "issued_on", nullable = false) public LocalDate issuedOn;
    @Column(name = "job_order_id") public Long jobOrderId;
    /** Person who took the materials. */
    @Column(name = "issued_to", length = 120) public String issuedTo;
    @Column(length = 300) public String purpose;
    @Column(nullable = false) public boolean voided;
    @Column(name = "void_reason", length = 300) public String voidReason;
    @Column(name = "created_by", length = 120) public String createdBy;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();
    public Long getId() { return id; }
}
