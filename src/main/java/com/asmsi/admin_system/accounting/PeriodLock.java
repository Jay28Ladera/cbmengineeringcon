package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** The books are closed through this date: nothing can be posted on or before it (one row). */
@Entity
@Table(name = "acc_period_lock")
public class PeriodLock {
    @Id public Long id = 1L;
    @Column(name = "locked_through") public LocalDate lockedThrough;
    @Column(name = "locked_by", length = 120) public String lockedBy;
    @Column(name = "locked_at") public LocalDateTime lockedAt;
}
