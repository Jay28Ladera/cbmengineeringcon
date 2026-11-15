package com.asmsi.admin_system.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A tool or piece of equipment tracked one by one (by asset tag), not by quantity. */
@Entity @Table(name = "inv_tool")
public class Tool {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 40) public String tag;
    @Column(nullable = false, length = 160) public String name;
    @Column(length = 80) public String category;
    @Column(name = "brand_model", length = 120) public String brandModel;
    @Column(name = "serial_number", length = 80) public String serialNumber;
    @Column(name = "purchase_date") public LocalDate purchaseDate;
    @Column(precision = 14, scale = 2) public BigDecimal cost;
    /** GOOD, FAIR, NEEDS_REPAIR. */
    @Column(name = "item_condition", nullable = false, length = 14) public String condition = "GOOD";
    /** AVAILABLE, ISSUED, MAINTENANCE, RETIRED. */
    @Column(nullable = false, length = 12) public String status = "AVAILABLE";
    @Column(length = 120) public String location;
    @Column(name = "next_maintenance") public LocalDate nextMaintenance;
    @Column(length = 600) public String notes;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();
    public Long getId() { return id; }
    public boolean isMaintenanceDue() { return nextMaintenance != null && !nextMaintenance.isAfter(LocalDate.now().plusDays(7)) && !"RETIRED".equals(status); }
}
