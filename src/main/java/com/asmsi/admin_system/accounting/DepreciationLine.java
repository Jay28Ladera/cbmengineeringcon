package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** One asset's depreciation in a monthly run. */
@Entity
@Table(name = "acc_depreciation_line", indexes = {@Index(columnList = "run_id"), @Index(columnList = "asset_id")})
public class DepreciationLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "run_id", nullable = false) public Long runId;
    @Column(name = "asset_id", nullable = false) public Long assetId;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal amount = BigDecimal.ZERO;
    @Column(name = "job_order_id") public Long jobOrderId;
}
