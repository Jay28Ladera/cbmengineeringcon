package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** A receipt in the liquidation of a cash advance. */
@Entity
@Table(name = "acc_cash_advance_line")
public class CashAdvanceLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "advance_id", nullable = false) public Long advanceId;
    @Column(nullable = false, length = 300) public String description;
    @Column(name = "account_id", nullable = false) public Long accountId;
    @Column(name = "job_order_id") public Long jobOrderId;
    @Column(nullable = false, precision = 14, scale = 2) public BigDecimal amount = BigDecimal.ZERO;
    @Column(name = "receipt_no", length = 60) public String receiptNo;
}
