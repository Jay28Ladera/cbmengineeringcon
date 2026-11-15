package com.asmsi.admin_system.inventory;

import jakarta.persistence.*;

/** Who may sign a canvass at a step: APPROVE (Office Engineer/QS), NOTE (Project/Operations Manager), PROCESS (Finance). */
@Entity
@Table(name = "pur_canvass_signer", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "step"}))
public class CanvassSigner {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "user_id", nullable = false) public Long userId;
    @Column(nullable = false, length = 10) public String step;

    public CanvassSigner() {}
    public CanvassSigner(Long userId, String step) { this.userId = userId; this.step = step; }
}
