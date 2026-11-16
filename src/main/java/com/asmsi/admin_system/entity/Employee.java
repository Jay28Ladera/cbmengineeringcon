package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Payroll record of one employee (the MASTER sheet). People on the organizational chart get a
 * record automatically and keep their name, position and department in step with their box;
 * HR can also add people who are not on the chart (site crews, drivers, project hires).
 */
@Entity
@Table(name = "hr_employee")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Org chart box this record follows, or null for someone added only in HR. */
    @Column(name = "org_node_id", unique = true)
    private Long orgNodeId;

    @Column(name = "employee_no", length = 64)
    private String employeeNo;

    /** User ID enrolled on the biometric machine; links device punches to this employee. */
    @Column(name = "biometric_id", unique = true, length = 32)
    private String biometricId;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(length = 160)
    private String position;

    @Column(length = 120)
    private String department;

    /** Payroll group, e.g. "Cebu Office" or "Manila Site". Pay sheets are grouped by this. */
    @Column(name = "pay_group", length = 80)
    private String payGroup;

    /** Short code used on the pay sheet: R, PR, PB, PT, SC. */
    @Column(name = "status_code", length = 8)
    private String statusCode;

    /** Regular, Probationary, Project Based, Special. */
    @Column(name = "employment_status", length = 40)
    private String employmentStatus;

    /** DAILY or MONTHLY. */
    @Column(name = "rate_type", nullable = false, length = 10)
    private String rateType = "DAILY";

    @Column(name = "daily_rate", precision = 12, scale = 2)
    private BigDecimal dailyRate;

    @Column(name = "monthly_rate", precision = 12, scale = 2)
    private BigDecimal monthlyRate;

    /** Standard contributions deducted automatically, unless a pay sheet entry replaces them. */
    @Column(name = "sss_amount", precision = 10, scale = 2)
    private BigDecimal sssAmount;

    @Column(name = "philhealth_amount", precision = 10, scale = 2)
    private BigDecimal philhealthAmount;

    @Column(name = "pagibig_amount", precision = 10, scale = 2)
    private BigDecimal pagibigAmount;

    /** Which cut-off takes the contributions: FIRST (26–10), SECOND (11–25) or BOTH. */
    @Column(name = "contribution_cutoff", length = 10)
    private String contributionCutoff = "SECOND";

    // Government numbers and tax (BIR 2316, contribution reports)
    @Column(length = 40) private String tin;
    @Column(name = "sss_no", length = 40) private String sssNo;
    @Column(name = "philhealth_no", length = 40) private String philhealthNo;
    @Column(name = "pagibig_no", length = 40) private String pagibigNo;
    @Column(name = "birth_date") private LocalDate birthDate;
    @Column(length = 300) private String address;
    /** REGULAR = withholding tax applies; MWE = minimum wage earner (exempt); NONE = no withholding. */
    @Column(name = "tax_status", length = 10) private String taxStatus;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "date_hired")
    private LocalDate dateHired;

    @Column(length = 600)
    private String remarks;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    public boolean isLinked() { return orgNodeId != null; }
    public boolean isMonthly() { return "MONTHLY".equalsIgnoreCase(rateType); }

    public Long getId() { return id; }
    public Long getOrgNodeId() { return orgNodeId; }
    public void setOrgNodeId(Long v) { orgNodeId = v; }
    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String v) { employeeNo = v; }
    public String getBiometricId() { return biometricId; }
    public void setBiometricId(String v) { biometricId = v; }
    public String getFullName() { return fullName; }
    public void setFullName(String v) { fullName = v; }
    public String getPosition() { return position; }
    public void setPosition(String v) { position = v; }
    public String getDepartment() { return department; }
    public void setDepartment(String v) { department = v; }
    public String getPayGroup() { return payGroup; }
    public void setPayGroup(String v) { payGroup = v; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String v) { statusCode = v; }
    public String getEmploymentStatus() { return employmentStatus; }
    public void setEmploymentStatus(String v) { employmentStatus = v; }
    public String getRateType() { return rateType; }
    public void setRateType(String v) { rateType = v; }
    public BigDecimal getDailyRate() { return dailyRate; }
    public void setDailyRate(BigDecimal v) { dailyRate = v; }
    public BigDecimal getMonthlyRate() { return monthlyRate; }
    public void setMonthlyRate(BigDecimal v) { monthlyRate = v; }
    public BigDecimal getSssAmount() { return sssAmount; }
    public void setSssAmount(BigDecimal v) { sssAmount = v; }
    public BigDecimal getPhilhealthAmount() { return philhealthAmount; }
    public void setPhilhealthAmount(BigDecimal v) { philhealthAmount = v; }
    public BigDecimal getPagibigAmount() { return pagibigAmount; }
    public void setPagibigAmount(BigDecimal v) { pagibigAmount = v; }
    public String getContributionCutoff() { return contributionCutoff; }
    public void setContributionCutoff(String v) { contributionCutoff = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { active = v; }
    public LocalDate getDateHired() { return dateHired; }
    public void setDateHired(LocalDate v) { dateHired = v; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { remarks = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public String getTin() { return tin; }
    public void setTin(String v) { tin = v; }
    public String getSssNo() { return sssNo; }
    public void setSssNo(String v) { sssNo = v; }
    public String getPhilhealthNo() { return philhealthNo; }
    public void setPhilhealthNo(String v) { philhealthNo = v; }
    public String getPagibigNo() { return pagibigNo; }
    public void setPagibigNo(String v) { pagibigNo = v; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate v) { birthDate = v; }
    public String getAddress() { return address; }
    public void setAddress(String v) { address = v; }
    public String getTaxStatus() { return taxStatus == null || taxStatus.isBlank() ? "REGULAR" : taxStatus; }
    public void setTaxStatus(String v) { taxStatus = v; }
    public boolean isMinimumWage() { return "MWE".equals(getTaxStatus()); }
}
