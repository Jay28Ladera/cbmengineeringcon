package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/** A holiday used by the pay sheet. REGULAR pays double when worked, SPECIAL adds 30%. */
@Entity
@Table(name = "hr_holiday")
public class Holiday {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "holiday_date", nullable = false, unique = true) private LocalDate date;
    @Column(nullable = false, length = 160) private String name;
    /** REGULAR or SPECIAL. */
    @Column(nullable = false, length = 10) private String type = "REGULAR";

    public boolean isRegular() { return "REGULAR".equalsIgnoreCase(type); }
    public Long getId() { return id; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate v) { date = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getType() { return type; }
    public void setType(String v) { type = v; }
}
