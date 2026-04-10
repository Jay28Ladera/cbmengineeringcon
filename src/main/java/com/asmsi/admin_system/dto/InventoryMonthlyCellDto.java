package com.asmsi.admin_system.dto;

import java.time.LocalDate;

public class InventoryMonthlyCellDto {

    private LocalDate inventoryDate;
    private String preparedBy;

    private Integer newlyAcquired = 0;
    private Integer defectiveRepair = 0;
    private Integer lost = 0;
    private Integer discarded = 0;
    private Integer availableItems = 0;

    // ✅ NEW
    private String notes;

    public LocalDate getInventoryDate() { return inventoryDate; }
    public void setInventoryDate(LocalDate inventoryDate) { this.inventoryDate = inventoryDate; }

    public String getPreparedBy() { return preparedBy; }
    public void setPreparedBy(String preparedBy) { this.preparedBy = preparedBy; }

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

    // ✅ Aliases (your HTML uses these names)
    public Integer getDefectiveForRepair() { return defectiveRepair; }
    public Integer getUnusableDiscarded() { return discarded; }
}
