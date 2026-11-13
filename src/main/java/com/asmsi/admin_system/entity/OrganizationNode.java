package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One position (box) on the organizational chart. The person in it is optional, so a vacant
 * position can be shown. Hierarchy uses {@link #parentId}; extra "also reports to" lines are
 * stored in {@link OrganizationReporting}.
 */
@Entity
@Table(name = "organization_node")
public class OrganizationNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Position title, e.g. "Project Engineer". */
    @Column(nullable = false, length = 160)
    private String title;

    @Column(name = "person_name", length = 160)
    private String personName;

    @Column(length = 120)
    private String department;

    /** Office, project site or branch the person is assigned to. */
    @Column(name = "site_assignment", length = 160)
    private String siteAssignment;

    @Column(name = "photo_url", length = 600)
    private String photoUrl;

    @Column(name = "email_address", length = 180)
    private String emailAddress;

    @Column(name = "contact_number", length = 60)
    private String contactNumber;

    @Column(name = "employee_id", length = 64)
    private String employeeId;

    @Column(name = "date_hired")
    private LocalDate dateHired;

    /** Profession or trade, e.g. "Civil Engineer", "Licensed Electrician", "AC Technician". */
    @Column(length = 160)
    private String profession;

    /** PRC licence or TESDA certificate number, if any. */
    @Column(name = "license_number", length = 80)
    private String licenseNumber;

    @Column(name = "license_expiry")
    private LocalDate licenseExpiry;

    /** Linked login account; its profile photo is used when this node has none. */
    @Column(name = "account_user_id")
    private Long accountUserId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    /** Drafts are only visible to admins. */
    @Column(nullable = false)
    private boolean published = false;

    @Column(nullable = false)
    private boolean resigned = false;

    @Column(name = "date_resigned")
    private LocalDate dateResigned;

    /** Saved manual position, relative to the automatic layout (pixels). */
    @Column(name = "layout_offset_x")
    private Double layoutOffsetX;

    @Column(name = "layout_offset_y")
    private Double layoutOffsetY;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String v) { title = v; }
    public String getPersonName() { return personName; }
    public void setPersonName(String v) { personName = v; }
    public String getDepartment() { return department; }
    public void setDepartment(String v) { department = v; }
    public String getSiteAssignment() { return siteAssignment; }
    public void setSiteAssignment(String v) { siteAssignment = v; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String v) { photoUrl = v; }
    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String v) { emailAddress = v; }
    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String v) { contactNumber = v; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String v) { employeeId = v; }
    public LocalDate getDateHired() { return dateHired; }
    public void setDateHired(LocalDate v) { dateHired = v; }
    public String getProfession() { return profession; }
    public void setProfession(String v) { profession = v; }
    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String v) { licenseNumber = v; }
    public LocalDate getLicenseExpiry() { return licenseExpiry; }
    public void setLicenseExpiry(LocalDate v) { licenseExpiry = v; }
    public Long getAccountUserId() { return accountUserId; }
    public void setAccountUserId(Long v) { accountUserId = v; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long v) { parentId = v; }
    public Integer getSortOrder() { return sortOrder == null ? 0 : sortOrder; }
    public void setSortOrder(Integer v) { sortOrder = v; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean v) { published = v; }
    public boolean isResigned() { return resigned; }
    public void setResigned(boolean v) { resigned = v; }
    public LocalDate getDateResigned() { return dateResigned; }
    public void setDateResigned(LocalDate v) { dateResigned = v; }
    public Double getLayoutOffsetX() { return layoutOffsetX; }
    public void setLayoutOffsetX(Double v) { layoutOffsetX = v; }
    public Double getLayoutOffsetY() { return layoutOffsetY; }
    public void setLayoutOffsetY(Double v) { layoutOffsetY = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
