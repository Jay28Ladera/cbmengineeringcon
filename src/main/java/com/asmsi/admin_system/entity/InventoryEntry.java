package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_entries",
       uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "school_item_id"}))
public class InventoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // link to session
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private InventorySession session;

    // link to item
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "school_item_id")
    private SchoolItem schoolItem;

    @Column(nullable = false)
    private Integer newlyAcquired = 0;

    @Column(nullable = false)
    private Integer defectiveRepair = 0;

    @Column(nullable = false)
    private Integer lost = 0;

    @Column(nullable = false)
    private Integer discarded = 0;

    @Column(nullable = false)
    private Integer availableItems = 0;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // getters/setters
    public Long getId() { return id; }

    public InventorySession getSession() { return session; }
    public void setSession(InventorySession session) { this.session = session; }

    public SchoolItem getSchoolItem() { return schoolItem; }
    public void setSchoolItem(SchoolItem schoolItem) { this.schoolItem = schoolItem; }

    public Integer getNewlyAcquired() { return newlyAcquired; }
    public void setNewlyAcquired(Integer newlyAcquired) { this.newlyAcquired = newlyAcquired; }

    public Integer getDefectiveRepair() { return defectiveRepair; }
    public void setDefectiveRepair(Integer defectiveRepair) { this.defectiveRepair = defectiveRepair; }

    public Integer getLost() { return lost; }
    public void setLost(Integer lost) { this.lost = lost; }

    public Integer getDiscarded() { return discarded; }
    public void setDiscarded(Integer discarded) { this.discarded = discarded; }

    public Integer getAvailableItems() { return availableItems; }
    public void setAvailableItems(Integer availableItems) { this.availableItems = availableItems; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
