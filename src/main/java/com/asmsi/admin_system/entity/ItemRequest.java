package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "item_requests")
public class ItemRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requested_by", nullable = false, length = 150)
    private String requestedBy;

    @Column(name = "department", nullable = false, length = 150)
    private String department;

    @Column(name = "date_requested", nullable = false)
    private LocalDate dateRequested;

    @Column(name = "date_needed", nullable = false)
    private LocalDate dateNeeded;

    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 0;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "purpose", columnDefinition = "TEXT")
    private String purpose;

    // =========================
    // CHECKED
    // =========================
    @Enumerated(EnumType.STRING)
    @Column(name = "checked_status", nullable = false)
    private DecisionStatus checkedStatus = DecisionStatus.PENDING;

    @Column(name = "checked_by", length = 150)
    private String checkedBy;

    @Column(name = "checked_reason", columnDefinition = "TEXT")
    private String checkedReason;

    // =========================
    // NOTED
    // =========================
    @Enumerated(EnumType.STRING)
    @Column(name = "noted_status", nullable = false)
    private DecisionStatus notedStatus = DecisionStatus.PENDING;

    @Column(name = "noted_by", length = 150)
    private String notedBy;

    @Column(name = "noted_reason", columnDefinition = "TEXT")
    private String notedReason;

    // =========================
    // APPROVED 1
    // =========================
    @Enumerated(EnumType.STRING)
    @Column(name = "approved1_status", nullable = false)
    private DecisionStatus approved1Status = DecisionStatus.PENDING;

    @Column(name = "approved_by_1", length = 150)
    private String approvedBy1;

    @Column(name = "approved1_reason", columnDefinition = "TEXT")
    private String approved1Reason;

    // =========================
    // APPROVED 2
    // =========================
    @Enumerated(EnumType.STRING)
    @Column(name = "approved2_status", nullable = false)
    private DecisionStatus approved2Status = DecisionStatus.PENDING;

    @Column(name = "approved_by_2", length = 150)
    private String approvedBy2;

    @Column(name = "approved2_reason", columnDefinition = "TEXT")
    private String approved2Reason;

    // =========================
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    // ===== getters/setters =====
    public Long getId() { return id; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public LocalDate getDateRequested() { return dateRequested; }
    public void setDateRequested(LocalDate dateRequested) { this.dateRequested = dateRequested; }

    public LocalDate getDateNeeded() { return dateNeeded; }
    public void setDateNeeded(LocalDate dateNeeded) { this.dateNeeded = dateNeeded; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public DecisionStatus getCheckedStatus() { return checkedStatus; }
    public void setCheckedStatus(DecisionStatus checkedStatus) { this.checkedStatus = checkedStatus; }

    public String getCheckedBy() { return checkedBy; }
    public void setCheckedBy(String checkedBy) { this.checkedBy = checkedBy; }

    public String getCheckedReason() { return checkedReason; }
    public void setCheckedReason(String checkedReason) { this.checkedReason = checkedReason; }

    public DecisionStatus getNotedStatus() { return notedStatus; }
    public void setNotedStatus(DecisionStatus notedStatus) { this.notedStatus = notedStatus; }

    public String getNotedBy() { return notedBy; }
    public void setNotedBy(String notedBy) { this.notedBy = notedBy; }

    public String getNotedReason() { return notedReason; }
    public void setNotedReason(String notedReason) { this.notedReason = notedReason; }

    public DecisionStatus getApproved1Status() { return approved1Status; }
    public void setApproved1Status(DecisionStatus approved1Status) { this.approved1Status = approved1Status; }

    public String getApprovedBy1() { return approvedBy1; }
    public void setApprovedBy1(String approvedBy1) { this.approvedBy1 = approvedBy1; }

    public String getApproved1Reason() { return approved1Reason; }
    public void setApproved1Reason(String approved1Reason) { this.approved1Reason = approved1Reason; }

    public DecisionStatus getApproved2Status() { return approved2Status; }
    public void setApproved2Status(DecisionStatus approved2Status) { this.approved2Status = approved2Status; }

    public String getApprovedBy2() { return approvedBy2; }
    public void setApprovedBy2(String approvedBy2) { this.approvedBy2 = approvedBy2; }

    public String getApproved2Reason() { return approved2Reason; }
    public void setApproved2Reason(String approved2Reason) { this.approved2Reason = approved2Reason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
