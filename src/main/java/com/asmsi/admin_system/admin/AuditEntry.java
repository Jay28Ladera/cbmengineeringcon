package com.asmsi.admin_system.admin;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One line of the audit trail: a record created, changed or deleted, or a sign-in.
 * Rows are written with plain JDBC by {@link AuditListener}; this entity is for reading and
 * so the table is created.
 */
@Entity
@Table(name = "sys_audit", indexes = {
        @Index(name = "ix_sys_audit_at", columnList = "at"),
        @Index(name = "ix_sys_audit_entity", columnList = "entity, entity_id")})
public class AuditEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public LocalDateTime at;
    @Column(length = 120) public String username;
    @Column(name = "user_name", length = 160) public String userName;
    /** CREATE, UPDATE, DELETE, LOGIN, LOGIN_FAILED, LOGOUT */
    @Column(nullable = false, length = 20) public String action;
    @Column(length = 40) public String module;
    @Column(length = 80) public String entity;
    @Column(name = "entity_id", length = 60) public String entityId;
    @Column(length = 200) public String label;
    /** One "field\u001fold\u001fnew" per line; for DELETE the full record. */
    @Column(columnDefinition = "text") public String changes;
    @Column(length = 64) public String ip;
    /** Same value for every line saved together. */
    @Column(length = 20) public String tx;
}
