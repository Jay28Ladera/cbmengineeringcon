package com.asmsi.admin_system.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A correction to one material's stock: damaged, lost, recount, returned from site, opening balance. */
@Entity @Table(name = "inv_stock_adjustment")
public class StockAdjustment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 30) public String number;
    @Column(name = "adjusted_on", nullable = false) public LocalDate adjustedOn;
    @Column(name = "material_id", nullable = false) public Long materialId;
    /** Signed: + adds stock, − removes it. */
    @Column(nullable = false, precision = 14, scale = 3) public BigDecimal qty;
    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 4) public BigDecimal unitCost;
    /** OPENING, RECOUNT, DAMAGED, LOST, RETURNED, OTHER. */
    @Column(nullable = false, length = 12) public String reason;
    @Column(name = "job_order_id") public Long jobOrderId;
    @Column(length = 300) public String note;
    @Column(name = "created_by", length = 120) public String createdBy;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();
    public Long getId() { return id; }
}
