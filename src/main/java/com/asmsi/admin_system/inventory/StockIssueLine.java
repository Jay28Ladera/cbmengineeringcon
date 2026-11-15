package com.asmsi.admin_system.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "inv_stock_issue_line", indexes = @Index(columnList = "issue_id"))
public class StockIssueLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "issue_id", nullable = false) public Long issueId;
    @Column(name = "material_id", nullable = false) public Long materialId;
    @Column(nullable = false, precision = 14, scale = 3) public BigDecimal qty;
    /** Average cost at the time of issue. */
    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 4) public BigDecimal unitCost;
    public Long getId() { return id; }
    public BigDecimal getAmount() { return qty.multiply(unitCost).setScale(2, java.math.RoundingMode.HALF_UP); }
}
