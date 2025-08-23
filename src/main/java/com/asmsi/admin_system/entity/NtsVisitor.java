// src/main/java/com/asmsi/admin_system/entity/NtsVisitor.java
package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ntsvisitor") // keep your current table name
public class NtsVisitor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, length=64)
    private String employeeId;

    @Column(nullable=false, length=128)
    private String name;

    @Column(length=512)
    private String imageUrl;

    @Column(length=128)
    private String position;

    @Column(length=128)
    private String department;

    @Column(nullable=false)
    private Instant visitedAt = Instant.now();

    /* ---------- NEW FIELDS ---------- */
    @Column(name = "adult_visitor")
    private Integer adultVisitor;      // nullable -> treat as 0 in reads

    @Column(name = "kid_visitor")
    private Integer kidVisitor;

    @Column(name = "total_visitors")
    private Integer totalVisitors;

    @Column(name = "contact_number", length = 64)
    private String contactNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", length = 32)
    private VehicleType vehicleType;   // MOTORCYCLE | FOUR_WHEELED

    @Column(name = "recorded_by", length = 64)
    private String recordedBy;

    /* ---------- getters/setters ---------- */
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

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
