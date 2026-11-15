package com.asmsi.admin_system.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "inv_stock_receipt_line", indexes = @Index(columnList = "receipt_id"))
public class StockReceiptLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "receipt_id", nullable = false) public Long receiptId;
    @Column(name = "po_line_id") public Long poLineId;
    @Column(name = "material_id", nullable = false) public Long materialId;
    @Column(nullable = false, precision = 14, scale = 3) public BigDecimal qty;
    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2) public BigDecimal unitCost;
    public Long getId() { return id; }
    public BigDecimal getAmount() { return qty.multiply(unitCost).setScale(2, java.math.RoundingMode.HALF_UP); }
}
