// src/main/java/com/asmsi/admin_system/entity/NtsVisitor.java
package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ntsvisitor") // change to "nts_visitor" if that’s your actual table
public class NtsVisitor {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    // getters/setters
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
}
