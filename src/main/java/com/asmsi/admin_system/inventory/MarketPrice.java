package com.asmsi.admin_system.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A price seen for a material: from a canvass quote, a purchase order, a delivery, or entered by hand. */
@Entity @Table(name = "pur_market_price", indexes = { @Index(columnList = "material_id"), @Index(columnList = "price_date") })
public class MarketPrice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "material_id") public Long materialId;
    @Column(nullable = false, length = 200) public String description;
    @Column(name = "supplier_id") public Long supplierId;
    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2) public BigDecimal unitPrice;
    @Column(nullable = false, length = 20) public String unit = "pc";
    @Column(name = "price_date", nullable = false) public LocalDate priceDate;
    /** CANVASS, PO, RECEIPT, MANUAL. */
    @Column(nullable = false, length = 10) public String source;
    @Column(name = "ref_no", length = 30) public String refNo;
    @Column(name = "created_by", length = 120) public String createdBy;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();
    public Long getId() { return id; }
}
