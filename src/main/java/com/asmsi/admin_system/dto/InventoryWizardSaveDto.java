package com.asmsi.admin_system.dto;

public class InventoryWizardSaveDto {
    public String inventoryDate;   // yyyy-MM-dd
    public String preparedBy;
    public String managerName;

    public String shopScope;
    public String categoryFilter;
    public String statusFilter;
    public String searchTerm;

    public Long itemId;

    public Integer newlyAcquired;
    public Integer defectiveRepair;
    public Integer lost;
    public Integer discarded;
    public Integer availableItems;

    public String notes;
}
