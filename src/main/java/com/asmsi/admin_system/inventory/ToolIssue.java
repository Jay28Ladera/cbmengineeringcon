package com.asmsi.admin_system.inventory;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A tool checked out to a person and/or job order, and its return. */
@Entity @Table(name = "inv_tool_issue", indexes = @Index(columnList = "tool_id"))
public class ToolIssue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "tool_id", nullable = false) public Long toolId;
    @Column(name = "issued_on", nullable = false) public LocalDate issuedOn;
    @Column(name = "employee_id") public Long employeeId;
    @Column(name = "issued_to", nullable = false, length = 160) public String issuedTo;
    @Column(name = "job_order_id") public Long jobOrderId;
    @Column(name = "expected_return") public LocalDate expectedReturn;
    @Column(name = "returned_on") public LocalDate returnedOn;
    @Column(name = "return_condition", length = 14) public String returnCondition;
    @Column(length = 300) public String notes;
    @Column(name = "issued_by", length = 120) public String issuedBy;
    @Column(name = "received_by", length = 120) public String receivedBy;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();
    public Long getId() { return id; }
    public boolean isOut() { return returnedOn == null; }
    public boolean isOverdue() { return returnedOn == null && expectedReturn != null && expectedReturn.isBefore(LocalDate.now()); }
}
