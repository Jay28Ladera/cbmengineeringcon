package com.asmsi.admin_system.dto;

public class ElibraryReportPcAssignmentDto {

    private Integer pcNumber;
    private String studentId;
    private String studentName;

    public ElibraryReportPcAssignmentDto() {
    }

    public ElibraryReportPcAssignmentDto(Integer pcNumber, String studentId, String studentName) {
        this.pcNumber = pcNumber;
        this.studentId = studentId;
        this.studentName = studentName;
    }

    public Integer getPcNumber() {
        return pcNumber;
    }

    public void setPcNumber(Integer pcNumber) {
        this.pcNumber = pcNumber;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }
}