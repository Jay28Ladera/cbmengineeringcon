package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * An attendance machine (ZKTeco or compatible) that sends its logs to the portal with the
 * ADMS / "cloud server" push protocol, identified by its serial number.
 */
@Entity
@Table(name = "hr_biometric_device")
public class BiometricDevice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "serial_number", nullable = false, unique = true, length = 64) private String serialNumber;
    @Column(nullable = false, length = 120) private String name;
    @Column(length = 160) private String location;
    /** Registered devices that are not active are ignored. Unregistered serials appear as "waiting". */
    @Column(nullable = false) private boolean approved = true;
    @Column(name = "last_seen_at") private LocalDateTime lastSeenAt;
    @Column(name = "last_ip", length = 64) private String lastIp;
    @Column(name = "last_log_at") private LocalDateTime lastLogAt;
    @Column(name = "att_stamp", length = 32) private String attStamp;
    @Column(name = "op_stamp", length = 32) private String opStamp;
    @Column(name = "firmware", length = 120) private String firmware;
    @Column(name = "user_count") private Integer userCount;
    @Column(name = "log_count") private Integer logCount;
    /** One-time command sent on the device's next check-in, e.g. to resend all logs. */
    @Column(name = "pending_command", length = 300) private String pendingCommand;
    @Column(name = "created_at") private LocalDateTime createdAt = LocalDateTime.now();

    /** Seen in the last 5 minutes (devices check in every few seconds to a minute). */
    public boolean isOnline() { return lastSeenAt != null && lastSeenAt.isAfter(LocalDateTime.now().minusMinutes(5)); }

    public Long getId() { return id; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String v) { serialNumber = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getLocation() { return location; }
    public void setLocation(String v) { location = v; }
    public boolean isApproved() { return approved; }
    public void setApproved(boolean v) { approved = v; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime v) { lastSeenAt = v; }
    public String getLastIp() { return lastIp; }
    public void setLastIp(String v) { lastIp = v; }
    public LocalDateTime getLastLogAt() { return lastLogAt; }
    public void setLastLogAt(LocalDateTime v) { lastLogAt = v; }
    public String getAttStamp() { return attStamp; }
    public void setAttStamp(String v) { attStamp = v; }
    public String getOpStamp() { return opStamp; }
    public void setOpStamp(String v) { opStamp = v; }
    public String getFirmware() { return firmware; }
    public void setFirmware(String v) { firmware = v; }
    public Integer getUserCount() { return userCount; }
    public void setUserCount(Integer v) { userCount = v; }
    public Integer getLogCount() { return logCount; }
    public void setLogCount(Integer v) { logCount = v; }
    public String getPendingCommand() { return pendingCommand; }
    public void setPendingCommand(String v) { pendingCommand = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
