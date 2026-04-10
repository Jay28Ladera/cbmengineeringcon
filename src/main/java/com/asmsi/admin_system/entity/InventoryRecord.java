package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "inventory_records",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_inventory_one_per_item_per_session",
        columnNames = {"inventory_date", "prepared_by", "manager_name", "school_item_id"}
    )
)
public class InventoryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== Modal 1 (Session/Header) fields =====
    @Column(name = "inventory_date", nullable = false)
    private LocalDate inventoryDate;

    @Column(name = "prepared_by", nullable = false)
    private String preparedBy;

    @Column(name = "manager_name", nullable = false)
    private String managerName;

    // optional context (filters/search)
    @Column(name = "shop_scope")
    private String shopScope;

    @Column(name = "category_filter")
    private String categoryFilter;

    @Column(name = "status_filter")
    private String statusFilter;

    @Column(name = "search_term")
    private String searchTerm;

    // ===== Item snapshot =====
    @Column(name = "school_item_id", nullable = false)
    private Long schoolItemId;

    @Column(name = "item_code")
    private String itemCode;

    @Column(name = "item_name")
    private String itemName;

    @Column(name = "shops")
    private String shops;

    @Column(name = "qty_on_hand")
    private Integer qtyOnHand;

    // ===== Modal 2 fields =====
    @Column(name = "newly_acquired", nullable = false)
    private Integer newlyAcquired = 0;

    @Column(name = "defective_repair", nullable = false)
    private Integer defectiveRepair = 0;

    @Column(name = "lost", nullable = false)
    private Integer lost = 0;

    @Column(name = "discarded", nullable = false)
    private Integer discarded = 0;

    @Column(name = "available_items", nullable = false)
    private Integer availableItems = 0;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ===== getters/setters =====
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

    public Long getSchoolItemId() { return schoolItemId; }
    public void setSchoolItemId(Long schoolItemId) { this.schoolItemId = schoolItemId; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getShops() { return shops; }
    public void setShops(String shops) { this.shops = shops; }

    public Integer getQtyOnHand() { return qtyOnHand; }
    public void setQtyOnHand(Integer qtyOnHand) { this.qtyOnHand = qtyOnHand; }

    public Integer getNewlyAcquired() { return newlyAcquired; }
    public void setNewlyAcquired(Integer newlyAcquired) { this.newlyAcquired = newlyAcquired; }

    public Integer getDefectiveRepair() { return defectiveRepair; }
    public void setDefectiveRepair(Integer defectiveRepair) { this.defectiveRepair = defectiveRepair; }

    public Integer getLost() { return lost; }
    public void setLost(Integer lost) { this.lost = lost; }

    public Integer getDiscarded() { return discarded; }
    public void setDiscarded(Integer discarded) { this.discarded = discarded; }

    public Integer getAvailableItems() { return availableItems; }
    public void setAvailableItems(Integer availableItems) { this.availableItems = availableItems; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
