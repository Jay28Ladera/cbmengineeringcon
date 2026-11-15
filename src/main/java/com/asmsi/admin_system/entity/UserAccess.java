package com.asmsi.admin_system.entity;

import jakarta.persistence.*;

/**
 * Per-person access settings. When {@link #followDepartment} is true the person gets their
 * department's modules; otherwise they get {@link #modules}. Admins always have full access.
 */
@Entity
@Table(name = "user_access")
public class UserAccess {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "follow_department", nullable = false)
    private boolean followDepartment = true;

    /** Comma-separated {@link AccessModule} names, used when not following the department. */
    @Column(length = 400)
    private String modules = "";

    /** Account created from the org chart, waiting for the person to set a password. */
    @Column(name = "invite_pending", nullable = false)
    private boolean invitePending = false;

    public UserAccess() {}
    public UserAccess(Long userId) { this.userId = userId; }

    public Long getUserId() { return userId; }
    public boolean isFollowDepartment() { return followDepartment; }
    public void setFollowDepartment(boolean v) { followDepartment = v; }
    public String getModules() { return modules == null ? "" : modules; }
    public void setModules(String v) { modules = v; }
    public boolean isInvitePending() { return invitePending; }
    public void setInvitePending(boolean v) { invitePending = v; }
}
