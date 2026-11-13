package com.asmsi.admin_system.entity;

import jakarta.persistence.*;

/** An extra ("dotted line") reporting relationship besides the main supervisor. */
@Entity
@Table(name = "organization_reporting",
        uniqueConstraints = @UniqueConstraint(columnNames = {"supervisor_id", "member_id"}))
public class OrganizationReporting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supervisor_id", nullable = false)
    private Long supervisorId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    public OrganizationReporting() {}
    public OrganizationReporting(Long supervisorId, Long memberId) {
        this.supervisorId = supervisorId;
        this.memberId = memberId;
    }
    public Long getId() { return id; }
    public Long getSupervisorId() { return supervisorId; }
    public Long getMemberId() { return memberId; }
}
