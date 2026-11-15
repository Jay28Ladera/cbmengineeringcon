package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Where money is kept: cash on hand, petty cash or a bank account. Each has its own ledger account. */
@Entity
@Table(name = "acc_bank")
public class BankAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, length = 120) public String name;
    /** CASH, PETTY or BANK */
    @Column(nullable = false, length = 8) public String kind = "BANK";
    @Column(name = "bank_name", length = 80) public String bankName;
    @Column(name = "account_no", length = 40) public String accountNo;
    @Column(name = "account_id", nullable = false) public Long accountId;
    @Column(nullable = false) public boolean active = true;
    /** Petty cash: who keeps the fund and its imprest amount. */
    @Column(length = 120) public String custodian;
    @Column(name = "fund_limit", precision = 14, scale = 2) public java.math.BigDecimal fundLimit;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public boolean isCheques() { return "BANK".equals(kind); }
    public boolean isPetty() { return "PETTY".equals(kind); }
    public String getLabel() { return name + (accountNo == null || accountNo.isBlank() ? "" : " · " + accountNo); }
}
