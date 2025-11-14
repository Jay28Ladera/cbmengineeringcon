// src/main/java/com/asmsi/admin_system/entity/VacationSchedule.java
package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "vacation_schedule")
public class VacationSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String via;

    @Column(name = "total_students")
    private Integer totalStudents;

    @Column(name = "logged_in")
    private Integer loggedIn;

    @Column(name = "est_datetime")
    private LocalDateTime estDateTime;

    private String venue;
    private String vehicle;
    private String status;
    private String message;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // --- getters & setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getVia() { return via; }
    public void setVia(String via) { this.via = via; }

    public Integer getTotalStudents() { return totalStudents; }
    public void setTotalStudents(Integer totalStudents) { this.totalStudents = totalStudents; }

    public Integer getLoggedIn() { return loggedIn; }
    public void setLoggedIn(Integer loggedIn) { this.loggedIn = loggedIn; }

    public LocalDateTime getEstDateTime() { return estDateTime; }
    public void setEstDateTime(LocalDateTime estDateTime) { this.estDateTime = estDateTime; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public String getVehicle() { return vehicle; }
    public void setVehicle(String vehicle) { this.vehicle = vehicle; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
