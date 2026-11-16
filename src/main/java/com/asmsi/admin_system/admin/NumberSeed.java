package com.asmsi.admin_system.admin;

import jakarta.persistence.*;

/** "Start the next PO of 2026 at 151": used when moving from manual books mid-year. */
@Entity
@Table(name = "sys_number_seed", uniqueConstraints = @UniqueConstraint(columnNames = {"prefix", "yr"}))
public class NumberSeed {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, length = 10) public String prefix;
    /** Year of the series; 0 for series without a year (canvass). */
    @Column(nullable = false) public int yr;
    @Column(name = "next_no", nullable = false) public long nextNo;
}
