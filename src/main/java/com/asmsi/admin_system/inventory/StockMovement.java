package com.asmsi.admin_system.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One line of a material's stock card. Quantity is signed: + in, − out. */
@Entity @Table(name = "inv_stock_movement", indexes = { @Index(columnList = "material_id"), @Index(columnList = "job_order_id") })
public class StockMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "material_id", nullable = false) public Long materialId;
    @Column(name = "moved_on", nullable = false) public LocalDate movedOn;
    /** RECEIPT, ISSUE, RETURN, ADJUSTMENT, VOID. */
    @Column(nullable = false, length = 12) public String kind;
    @Column(nullable = false, precision = 14, scale = 3) public BigDecimal qty;
    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 4) public BigDecimal unitCost;
    @Column(name = "balance_after", nullable = false, precision = 14, scale = 3) public BigDecimal balanceAfter;
    /** RR-…, IS-…, ADJ-… */
    @Column(name = "ref_no", length = 30) public String refNo;
    @Column(name = "job_order_id") public Long jobOrderId;
    @Column(length = 300) public String note;
    @Column(name = "created_by", length = 120) public String createdBy;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public BigDecimal getAmount() { return qty.multiply(unitCost).setScale(2, java.math.RoundingMode.HALF_UP); }
}
