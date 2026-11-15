package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** Part of a bill settled by a voucher: the gross amount settled, the tax withheld from it, and the net paid. */
@Entity
@Table(name = "acc_voucher_line")
public class VoucherLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "voucher_id", nullable = false) public Long voucherId;
    @Column(name = "bill_id", nullable = false) public Long billId;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal gross = BigDecimal.ZERO;
    @Column(name = "ewt_rate", nullable = false, precision = 5, scale = 2) public BigDecimal ewtRate = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal ewt = BigDecimal.ZERO;
    @Column(name = "net_amount", nullable = false, precision = 16, scale = 2) public BigDecimal net = BigDecimal.ZERO;
}
