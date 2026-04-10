package com.asmsi.admin_system.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ElibraryReportBookingDto {

    private Long id;
    private String bookingNumber;
    private LocalDate bookingDate;
    private String startTime;
    private String endTime;
    private String roomNumber;
    private String requestedBy;
    private List<ElibraryReportPcAssignmentDto> pcAssignments = new ArrayList<>();

    public ElibraryReportBookingDto() {
    }

    public ElibraryReportBookingDto(Long id, String bookingNumber, LocalDate bookingDate,
                                    String startTime, String endTime, String roomNumber, String requestedBy) {
        this.id = id;
        this.bookingNumber = bookingNumber;
        this.bookingDate = bookingDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.roomNumber = roomNumber;
        this.requestedBy = requestedBy;
    }

    public void addPcAssignment(ElibraryReportPcAssignmentDto dto) {
        this.pcAssignments.add(dto);
    }

    public Long getId() {
        return id;
    }

    public String getBookingNumber() {
        return bookingNumber;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public List<ElibraryReportPcAssignmentDto> getPcAssignments() {
        return pcAssignments;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setBookingNumber(String bookingNumber) {
        this.bookingNumber = bookingNumber;
    }

    public void setBookingDate(LocalDate bookingDate) {
        this.bookingDate = bookingDate;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public void setPcAssignments(List<ElibraryReportPcAssignmentDto> pcAssignments) {
        this.pcAssignments = pcAssignments;
    }
}