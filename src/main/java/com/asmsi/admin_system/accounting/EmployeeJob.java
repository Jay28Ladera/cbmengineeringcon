package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;

/** Where an employee's pay is charged by default when payroll is posted: a job order (direct labor) or the office. */
@Entity
@Table(name = "acc_employee_job")
public class EmployeeJob {
    @Id @Column(name = "employee_id") public Long employeeId;
    @Column(name = "job_order_id") public Long jobOrderId;
    public EmployeeJob() {}
    public EmployeeJob(Long employeeId, Long jobOrderId) { this.employeeId = employeeId; this.jobOrderId = jobOrderId; }
}
