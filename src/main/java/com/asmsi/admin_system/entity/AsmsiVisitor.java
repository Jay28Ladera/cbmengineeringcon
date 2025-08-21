package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "asmsi_visitor")
public class AsmsiVisitor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "alumni_id")
    private AsmsiAlumni alumni;

    @Column(nullable = false)
    private Instant visitedAt = Instant.now();

    @Column(length = 128)
    private String recordedBy; // optional (username)

    // getters/setters
    public Long getId() { return id; }

    public AsmsiAlumni getAlumni() { return alumni; }
    public void setAlumni(AsmsiAlumni alumni) { this.alumni = alumni; }

    public Instant getVisitedAt() { return visitedAt; }
    public void setVisitedAt(Instant visitedAt) { this.visitedAt = visitedAt; }

    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
}
