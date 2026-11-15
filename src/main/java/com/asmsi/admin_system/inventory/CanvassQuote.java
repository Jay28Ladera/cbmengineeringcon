package com.asmsi.admin_system.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** One supplier's unit price for one canvass item. */
@Entity @Table(name = "pur_canvass_quote", uniqueConstraints = @UniqueConstraint(columnNames = {"item_id", "supplier_id"}))
public class CanvassQuote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "canvass_id", nullable = false) public Long canvassId;
    @Column(name = "item_id", nullable = false) public Long itemId;
    @Column(name = "supplier_id", nullable = false) public Long supplierId;
    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2) public BigDecimal unitPrice;
    /** Brand this supplier offers for the item (e.g. Home Aid, La Fonza). */
    @Column(length = 80) public String brand;
    /** The supplier answered that they don't carry the item ("NOT AVAILABLE"); unit price is 0 and is ignored. */
    @Column(name = "not_available") public Boolean notAvailable;
    public boolean isNotAvailable() { return Boolean.TRUE.equals(notAvailable); }
    public Long getId() { return id; }
}
