package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "inquiries")
public class Inquiry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String fullName;
    private String company;
    @Column(nullable = false) private String email;
    @Column(nullable = false) private String phone;
    private String preferredContactMethod;
    @Column(nullable = false, length = 1500) private String servicesNeeded;
    @Column(nullable = false) private String projectName;
    @Column(nullable = false) private String projectLocation;
    private String propertyType;
    private String workNeeded;
    private String floorAreaOrQuantity;
    private String estimatedBudget;
    private LocalDate targetStartDate;
    private LocalDate targetCompletionDate;
    private String siteVisit;
    @Column(nullable = false, length = 5000) private String projectDetails;
    @Column(length = 2000) private String plansUrl;
    @Column(nullable = false) private String status = "NEW";
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPreferredContactMethod() { return preferredContactMethod; }
    public void setPreferredContactMethod(String value) { this.preferredContactMethod = value; }
    public String getServicesNeeded() { return servicesNeeded; }
    public void setServicesNeeded(String servicesNeeded) { this.servicesNeeded = servicesNeeded; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getProjectLocation() { return projectLocation; }
    public void setProjectLocation(String projectLocation) { this.projectLocation = projectLocation; }
    public String getPropertyType() { return propertyType; }
    public void setPropertyType(String propertyType) { this.propertyType = propertyType; }
    public String getWorkNeeded() { return workNeeded; }
    public void setWorkNeeded(String workNeeded) { this.workNeeded = workNeeded; }
    public String getFloorAreaOrQuantity() { return floorAreaOrQuantity; }
    public void setFloorAreaOrQuantity(String value) { this.floorAreaOrQuantity = value; }
    public String getEstimatedBudget() { return estimatedBudget; }
    public void setEstimatedBudget(String estimatedBudget) { this.estimatedBudget = estimatedBudget; }
    public LocalDate getTargetStartDate() { return targetStartDate; }
    public void setTargetStartDate(LocalDate targetStartDate) { this.targetStartDate = targetStartDate; }
    public LocalDate getTargetCompletionDate() { return targetCompletionDate; }
    public void setTargetCompletionDate(LocalDate targetCompletionDate) { this.targetCompletionDate = targetCompletionDate; }
    public String getSiteVisit() { return siteVisit; }
    public void setSiteVisit(String siteVisit) { this.siteVisit = siteVisit; }
    public String getProjectDetails() { return projectDetails; }
    public void setProjectDetails(String projectDetails) { this.projectDetails = projectDetails; }
    public String getPlansUrl() { return plansUrl; }
    public void setPlansUrl(String plansUrl) { this.plansUrl = plansUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
