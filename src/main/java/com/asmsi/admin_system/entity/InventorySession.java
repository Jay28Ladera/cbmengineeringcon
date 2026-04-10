package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_sessions")
public class InventorySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate inventoryDate;

    @Column(nullable = false)
    private String preparedBy;      // username (readonly)

    @Column(nullable = false)
    private String managerName;

    private String shopScope;        // optional: locked shop or filterShop
    private String categoryFilter;   // optional
    private String statusFilter;     // optional
    private String searchTerm;       // optional

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // getters/setters
    public Long getId() { return id; }
    public LocalDate getInventoryDate() { return inventoryDate; }
    public void setInventoryDate(LocalDate inventoryDate) { this.inventoryDate = inventoryDate; }

    public String getPreparedBy() { return preparedBy; }
    public void setPreparedBy(String preparedBy) { this.preparedBy = preparedBy; }

    public String getManagerName() { return managerName; }
    public void setManagerName(String managerName) { this.managerName = managerName; }

    public String getShopScope() { return shopScope; }
    public void setShopScope(String shopScope) { this.shopScope = shopScope; }

    public String getCategoryFilter() { return categoryFilter; }
    public void setCategoryFilter(String categoryFilter) { this.categoryFilter = categoryFilter; }

    public String getStatusFilter() { return statusFilter; }
    public void setStatusFilter(String statusFilter) { this.statusFilter = statusFilter; }

    public String getSearchTerm() { return searchTerm; }
    public void setSearchTerm(String searchTerm) { this.searchTerm = searchTerm; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
