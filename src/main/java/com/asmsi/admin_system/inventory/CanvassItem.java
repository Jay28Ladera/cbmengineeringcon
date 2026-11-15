package com.asmsi.admin_system.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "pur_canvass_item", indexes = @Index(columnList = "canvass_id"))
public class CanvassItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "canvass_id", nullable = false) public Long canvassId;
    @Column(name = "material_id") public Long materialId;
    @Column(nullable = false, length = 200) public String description;
    @Column(nullable = false, precision = 14, scale = 3) public BigDecimal qty;
    @Column(nullable = false, length = 20) public String unit = "pc";
    /** Supplier chosen for this item; null = the lowest price is used. */
    @Column(name = "awarded_supplier_id") public Long awardedSupplierId;
    @Column(name = "sort_order") public int sortOrder;
    /** Free remark printed in the REMARKS column of the canvass sheet. */
    @Column(length = 200) public String remarks;
    public Long getId() { return id; }
}
