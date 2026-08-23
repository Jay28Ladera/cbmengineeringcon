package com.asmsi.admin_system.dto;

import java.util.List;

public class BulkEnrollPassersRequest {

    private String batchNumber;
    private String via;
    private List<EnrollPasserRequest> students;

    public String getBatchNumber() {
        return batchNumber;
    }

    public void setBatchNumber(String batchNumber) {
        this.batchNumber = batchNumber;
    }

    public String getVia() {
        return via;
    }

    public void setVia(String via) {
        this.via = via;
    }

    public List<EnrollPasserRequest> getStudents() {
        return students;
    }

    public void setStudents(List<EnrollPasserRequest> students) {
        this.students = students;
    }
}