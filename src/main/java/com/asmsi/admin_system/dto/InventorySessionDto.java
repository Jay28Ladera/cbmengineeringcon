package com.asmsi.admin_system.dto;

import java.util.List;

public class InventorySessionDto {

    private String sessionDate;          // yyyy-MM-dd (string from JS)
    private String inCharge;
    private String witnesses;
    private List<InventoryEntryDto> entries;

    public InventorySessionDto() {
    }

    // --- getters & setters ---

    public String getSessionDate() {
        return sessionDate;
    }
    public void setSessionDate(String sessionDate) {
        this.sessionDate = sessionDate;
    }

    public String getInCharge() {
        return inCharge;
    }
    public void setInCharge(String inCharge) {
        this.inCharge = inCharge;
    }

    public String getWitnesses() {
        return witnesses;
    }
    public void setWitnesses(String witnesses) {
        this.witnesses = witnesses;
    }

    public List<InventoryEntryDto> getEntries() {
        return entries;
    }
    public void setEntries(List<InventoryEntryDto> entries) {
        this.entries = entries;
    }
}
