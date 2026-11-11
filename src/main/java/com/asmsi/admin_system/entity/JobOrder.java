package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_orders")
public class JobOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, length = 24) private String jobOrderNumber;
    @OneToOne(optional = false) @JoinColumn(name = "quotation_id", nullable = false, unique = true)
    private Quotation quotation;
    @Column(nullable = false, length = 30) private String status = "PLANNED";
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;

    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public String getJobOrderNumber() { return jobOrderNumber; }
    public void setJobOrderNumber(String jobOrderNumber) { this.jobOrderNumber = jobOrderNumber; }
    public Quotation getQuotation() { return quotation; }
    public void setQuotation(Quotation quotation) { this.quotation = quotation; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
