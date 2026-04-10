package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "elibrary_pc",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_elibrary_pc_room_pcnum",
                columnNames = {"room_id", "pc_number"}
        )
)
public class ElibraryPc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ElibraryRoom room;

    @Column(name = "pc_number", nullable = false)
    private Integer pcNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PcStatus status = PcStatus.AVAILABLE;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ElibraryRoom getRoom() {
        return room;
    }

    public void setRoom(ElibraryRoom room) {
        this.room = room;
    }

    public Integer getPcNumber() {
        return pcNumber;
    }

    public void setPcNumber(Integer pcNumber) {
        this.pcNumber = pcNumber;
    }

    public PcStatus getStatus() {
        return status;
    }

    public void setStatus(PcStatus status) {
        this.status = status;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}