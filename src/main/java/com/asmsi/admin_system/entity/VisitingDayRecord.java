package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(
    name = "visitingday",
    indexes = {
        @Index(name = "idx_visitingday_date", columnList = "date"),
        @Index(name = "idx_visitingday_idnumber", columnList = "idNumber")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisitingDayRecord {

    @Id
    @GeneratedValue
    private UUID id;

    // Student context
    @Column(nullable = false)
    private String idNumber;

    private String lrn;
    private String name;
    private String campus;
    private String familySaint;
    private String batchNumber;
    private String imageUrl;

    @Column(columnDefinition = "text")
    private String address;

    // Visit info
    @Column(nullable = false)
    private LocalDate date;

    private LocalTime timeIn;

    private Integer adultVisitor;
    private Integer kidVisitor;
    private Integer totalVisitors;

    // Optional metadata captured in the modal
    @Column(length = 32)
    private String contactNumber;   // e.g. "0917-123-4567"

    @Column(length = 24)
    private String vehicleType;     // e.g. "MOTORCYCLE" or "FOUR_WHEELED"

    // Audit
    private String account;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
