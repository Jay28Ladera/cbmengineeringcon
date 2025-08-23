package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "graduatevisitor") // change if you prefer another table name
public class GraduateVisitor {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, length=64)
    private String idNumber;

    @Column(length=64)
    private String batchNumber;

    @Column(length=512)
    private String address;

    @Column(length=512)
    private String imageUrl;

    @Column(nullable=false)
    private Instant visitedAt = Instant.now();

    @Column(name="adult_visitor")
    private Integer adultVisitor;

    @Column(name="kid_visitor")
    private Integer kidVisitor;

    @Column(name="total_visitors")
    private Integer totalVisitors;

    @Column(name="contact_number", length=64)
    private String contactNumber;

    @Enumerated(EnumType.STRING)
    @Column(name="vehicle_type", length=32)
    private VehicleType vehicleType;  // MOTORCYCLE | FOUR_WHEELED

    @Column(name="recorded_by", length=64)
    private String recordedBy;

    // getters/setters
    public Long getId() { return id; }
    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Instant getVisitedAt() { return visitedAt; }
    public void setVisitedAt(Instant visitedAt) { this.visitedAt = visitedAt; }
    public Integer getAdultVisitor() { return adultVisitor; }
    public void setAdultVisitor(Integer adultVisitor) { this.adultVisitor = adultVisitor; }
    public Integer getKidVisitor() { return kidVisitor; }
    public void setKidVisitor(Integer kidVisitor) { this.kidVisitor = kidVisitor; }
    public Integer getTotalVisitors() { return totalVisitors; }
    public void setTotalVisitors(Integer totalVisitors) { this.totalVisitors = totalVisitors; }
    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
    public VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleType vehicleType) { this.vehicleType = vehicleType; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
}
