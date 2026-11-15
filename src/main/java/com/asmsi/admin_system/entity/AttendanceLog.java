package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * One punch (time-in or time-out). From a device the person is known by their device ID (PIN);
 * HR links PINs to employees with the employee's Biometric ID. Manual punches name the employee.
 */
@Entity
@Table(name = "hr_attendance_log",
       uniqueConstraints = @UniqueConstraint(columnNames = {"pin", "punch_time"}),
       indexes = { @Index(columnList = "punch_time"), @Index(columnList = "employee_id") })
public class AttendanceLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    /** User ID on the machine. Null for a manual punch of someone without a Biometric ID. */
    @Column(length = 32) private String pin;
    /** Set for manual punches; device punches are matched through the PIN. */
    @Column(name = "employee_id") private Long employeeId;
    @Column(name = "punch_time", nullable = false) private LocalDateTime punchTime;
    /** Device status code (0 check-in, 1 check-out, 4/5 overtime in/out …) when known. */
    @Column(name = "punch_state") private Integer punchState;
    /** DEVICE, IMPORT or MANUAL. */
    @Column(nullable = false, length = 10) private String source = "DEVICE";
    @Column(name = "device_id") private Long deviceId;
    @Column(length = 200) private String note;
    @Column(name = "created_by", length = 120) private String createdBy;
    @Column(name = "created_at") private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getPin() { return pin; }
    public void setPin(String v) { pin = v; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long v) { employeeId = v; }
    public LocalDateTime getPunchTime() { return punchTime; }
    public void setPunchTime(LocalDateTime v) { punchTime = v; }
    public Integer getPunchState() { return punchState; }
    public void setPunchState(Integer v) { punchState = v; }
    public String getSource() { return source; }
    public void setSource(String v) { source = v; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long v) { deviceId = v; }
    public String getNote() { return note; }
    public void setNote(String v) { note = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { createdBy = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
