package com.asmsi.admin_system.inventory;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A price comparison: the same items quoted by several suppliers, with a winner per item. */
@Entity @Table(name = "pur_canvass")
public class Canvass {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 30) public String number;
    @Column(nullable = false, length = 200) public String title;
    @Column(name = "job_order_id") public Long jobOrderId;
    @Column(name = "needed_by") public LocalDate neededBy;
    /** Material request number (MR #) this canvass answers. */
    @Column(name = "mr_no", length = 40) public String mrNo;
    /** DRAFT, APPROVED (winners final), ORDERED (purchase orders made). */
    @Column(nullable = false, length = 12) public String status = "DRAFT";
    @Column(length = 1000) public String notes;
    @Column(name = "created_by", length = 120) public String createdBy;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "approved_by", length = 120) public String approvedBy;
    @Column(name = "approved_at") public LocalDateTime approvedAt;
    public Long getId() { return id; }
}
