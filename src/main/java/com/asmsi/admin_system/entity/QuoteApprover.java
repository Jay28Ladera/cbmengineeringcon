package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A user who may approve quotations before they are sent to the client (e.g. the CEO). */
@Entity
@Table(name = "quote_approver")
public class QuoteApprover {
    @Id
    @Column(name = "user_id")
    public Long userId;

    @Column(name = "added_at")
    public LocalDateTime addedAt;

    public QuoteApprover() {}
    public QuoteApprover(Long userId) { this.userId = userId; this.addedAt = LocalDateTime.now(); }
}
