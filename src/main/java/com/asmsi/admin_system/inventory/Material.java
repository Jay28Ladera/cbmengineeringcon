package com.asmsi.admin_system.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A stock item. On-hand quantity and average cost are kept up to date by {@link InventoryService}
 * from stock movements; never set them directly.
 */
@Entity @Table(name = "inv_material")
public class Material {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 40) public String code;
    @Column(nullable = false, length = 200) public String name;
    @Column(name = "category_id") public Long categoryId;
    @Column(nullable = false, length = 20) public String unit = "pc";
    /** Below this on-hand quantity the material is flagged for reordering. */
    @Column(name = "reorder_level", precision = 14, scale = 3) public BigDecimal reorderLevel;
    @Column(name = "on_hand", nullable = false, precision = 14, scale = 3) public BigDecimal onHand = BigDecimal.ZERO;
    /** Moving average unit cost of what is on hand. */
    @Column(name = "avg_cost", nullable = false, precision = 14, scale = 4) public BigDecimal avgCost = BigDecimal.ZERO;
    @Column(name = "last_cost", precision = 14, scale = 2) public BigDecimal lastCost;
    @Column(length = 120) public String location;
    @Column(length = 600) public String notes;
    @Column(nullable = false) public boolean active = true;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public boolean isLow() { return reorderLevel != null && reorderLevel.signum() > 0 && onHand.compareTo(reorderLevel) <= 0; }
    public BigDecimal getStockValue() { return onHand.multiply(avgCost).setScale(2, java.math.RoundingMode.HALF_UP); }
    public String getLabel() { return code + " · " + name; }
}
