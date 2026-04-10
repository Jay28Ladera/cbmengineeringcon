package com.asmsi.admin_system.dto;

public class RoomLiveAvailabilityDto {

    private String roomName;
    private int totalPcs;
    private int usedPcs;
    private int availablePcs;
    private boolean active;

    public RoomLiveAvailabilityDto() {
    }

    public RoomLiveAvailabilityDto(String roomName, int totalPcs, int usedPcs, int availablePcs, boolean active) {
        this.roomName = roomName;
        this.totalPcs = totalPcs;
        this.usedPcs = usedPcs;
        this.availablePcs = availablePcs;
        this.active = active;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public int getTotalPcs() {
        return totalPcs;
    }

    public void setTotalPcs(int totalPcs) {
        this.totalPcs = totalPcs;
    }

    public int getUsedPcs() {
        return usedPcs;
    }

    public void setUsedPcs(int usedPcs) {
        this.usedPcs = usedPcs;
    }

    public int getAvailablePcs() {
        return availablePcs;
    }

    public void setAvailablePcs(int availablePcs) {
        this.availablePcs = availablePcs;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}