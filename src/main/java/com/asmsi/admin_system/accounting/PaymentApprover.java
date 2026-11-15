package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;

/** A user who approves payment vouchers before Finance releases the cash or cheque (e.g. the CEO). */
@Entity
@Table(name = "acc_payment_approver")
public class PaymentApprover {
    @Id @Column(name = "user_id") public Long userId;
    public PaymentApprover() {}
    public PaymentApprover(Long userId) { this.userId = userId; }
}
