package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Which modules people in a department get by default (Administration → Department Access). */
@Entity
@Table(name = "department_access")
public class DepartmentAccess {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Lower-case, trimmed department name, so "Sales" and "sales " are the same department. */
    @Column(name = "department_key", nullable = false, unique = true, length = 120)
    private String departmentKey;

    @Column(name = "department_name", nullable = false, length = 120)
    private String departmentName;

    /** Comma-separated {@link AccessModule} names. */
    @Column(length = 400)
    private String modules = "";

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }

    public static String keyOf(String department) {
        return department == null ? "" : department.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    public Long getId() { return id; }
    public String getDepartmentKey() { return departmentKey; }
    public void setDepartmentKey(String v) { departmentKey = v; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String v) { departmentName = v; }
    public String getModules() { return modules == null ? "" : modules; }
    public void setModules(String v) { modules = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
