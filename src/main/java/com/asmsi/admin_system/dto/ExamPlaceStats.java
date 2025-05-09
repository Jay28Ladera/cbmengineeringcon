package com.asmsi.admin_system.dto;

public class ExamPlaceStats {
    private String examPlace;
    private Long total;
    private Long arrived;
    private Long sentHome; // Changed to Long for consistency

    public ExamPlaceStats(String examPlace, Long total, Long arrived, Long sentHome) {
        this.examPlace = examPlace;
        this.total = total;
        this.arrived = arrived;
        this.sentHome = sentHome;
    }

    public String getExamPlace() {
        return examPlace;
    }

    public Long getTotal() {
        return total;
    }

    public Long getArrived() {
        return arrived;
    }

    public Long getSentHome() {
        return sentHome;
    }
}
