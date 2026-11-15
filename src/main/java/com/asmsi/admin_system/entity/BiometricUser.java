package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A user enrolled on a machine (PIN and the name typed on the device), used to match PINs to employees. */
@Entity
@Table(name = "hr_biometric_user")
public class BiometricUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 32) private String pin;
    @Column(length = 120) private String name;
    @Column(name = "device_id") private Long deviceId;
    @Column(name = "updated_at") private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getPin() { return pin; }
    public void setPin(String v) { pin = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long v) { deviceId = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
}
