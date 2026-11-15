package com.asmsi.admin_system.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity @Table(name = "pur_purchase_order_line", indexes = @Index(columnList = "po_id"))
public class PurchaseOrderLine {
    /** Quantity already on supplier bills (Accounting). Null on older rows = 0. */
    @Column(name = "billed_qty", precision = 14, scale = 3) public BigDecimal billedQty;
    public BigDecimal billed() { return billedQty == null ? BigDecimal.ZERO : billedQty; }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "po_id", nullable = false) public Long poId;
    @Column(name = "material_id") public Long materialId;
    @Column(nullable = false, length = 200) public String description;
    @Column(nullable = false, precision = 14, scale = 3) public BigDecimal qty;
    @Column(nullable = false, length = 20) public String unit = "pc";
    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2) public BigDecimal unitPrice;
    @Column(name = "received_qty", nullable = false, precision = 14, scale = 3) public BigDecimal receivedQty = BigDecimal.ZERO;
    @Column(name = "sort_order") public int sortOrder;
    public Long getId() { return id; }
    public BigDecimal getAmount() { return qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP); }
    public BigDecimal getRemaining() { return qty.subtract(receivedQty).max(BigDecimal.ZERO); }
}
