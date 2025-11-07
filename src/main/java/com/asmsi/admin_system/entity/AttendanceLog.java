// src/main/java/com/asmsi/admin_system/entity/AttendanceLog.java
package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "attendance_logs")
public class AttendanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_number")
    private String idNumber;

    private String lrn;
    private String name;
    private String campus;

    @Column(name = "family_saint")
    private String familySaint;

    @Column(name = "batch_number")
    private String batchNumber;

    @Column(name = "image_url")
    private String imageUrl;

    private LocalDate date;

    @Column(name = "time_in")
    private LocalTime timeIn;

    @Column(name = "time_out")
    private LocalTime timeOut;

    private String status;
    private String remarks;

    // “Recorded by” — your table has BOTH `account` and `recorded_by`.
    // We’ll map both; the filter will prefer whichever is present.
    private String account;

    @Column(name = "recorded_by")
    private String recordedBy;

    // getters/setters …

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }

    public String getLrn() { return lrn; }
    public void setLrn(String lrn) { this.lrn = lrn; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCampus() { return campus; }
    public void setCampus(String campus) { this.campus = campus; }

    public String getFamilySaint() { return familySaint; }
    public void setFamilySaint(String familySaint) { this.familySaint = familySaint; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public LocalTime getTimeIn() { return timeIn; }
    public void setTimeIn(LocalTime timeIn) { this.timeIn = timeIn; }

    public LocalTime getTimeOut() { return timeOut; }
    public void setTimeOut(LocalTime timeOut) { this.timeOut = timeOut; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }

    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
}
