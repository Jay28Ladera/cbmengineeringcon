package com.asmsi.admin_system.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class ElibraryBookingForm {

    private LocalDate bookingDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String roomNumber;
    private Integer computersNeeded;
    private String requestedBy;
    private String sectionName;
    private String purpose;
    private String remarks;
    private List<String> studentPcUserIds = new ArrayList<>();

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(LocalDate bookingDate) {
        this.bookingDate = bookingDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public Integer getComputersNeeded() {
        return computersNeeded;
    }

    public void setComputersNeeded(Integer computersNeeded) {
        this.computersNeeded = computersNeeded;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public String getSectionName() {
        return sectionName;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public List<String> getStudentPcUserIds() {
        return studentPcUserIds;
    }

    public void setStudentPcUserIds(List<String> studentPcUserIds) {
        this.studentPcUserIds = studentPcUserIds;
    }
}