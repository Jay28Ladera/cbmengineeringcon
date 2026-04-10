package com.asmsi.admin_system.dto;

import java.time.LocalDate;

public class ElibraryReportRowDto {

    private Long bookingId;
    private String bookingNumber;
    private LocalDate bookingDate;
    private String startTime;
    private String endTime;
    private String roomNumber;
    private String requestedBy;

    private Integer pcNumber;
    private String studentId;
    private String studentName;

    public ElibraryReportRowDto(Long bookingId, String bookingNumber, LocalDate bookingDate,
                                String startTime, String endTime, String roomNumber, String requestedBy,
                                Integer pcNumber, String studentId, String studentName) {
        this.bookingId = bookingId;
        this.bookingNumber = bookingNumber;
        this.bookingDate = bookingDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.roomNumber = roomNumber;
        this.requestedBy = requestedBy;
        this.pcNumber = pcNumber;
        this.studentId = studentId;
        this.studentName = studentName;
    }

    public Long getBookingId() {
        return bookingId;
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

    public Integer getPcNumber() {
        return pcNumber;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getStudentName() {
        return studentName;
    }
}