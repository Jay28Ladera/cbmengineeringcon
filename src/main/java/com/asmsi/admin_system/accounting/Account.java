package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** An account in the chart of accounts. Codes: 1xxx assets, 2xxx liabilities, 3xxx equity, 4xxx income, 5xxx direct costs, 6xxx expenses. */
@Entity
@Table(name = "acc_account")
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 12) public String code;
    @Column(nullable = false, length = 120) public String name;
    /** ASSET, LIABILITY, EQUITY, INCOME, EXPENSE */
    @Column(nullable = false, length = 10) public String type;
    /** Grouping shown on statements, e.g. "Current assets", "Cost of contracts", "Operating expenses". */
    @Column(name = "group_name", length = 60) public String groupName;
    @Column(length = 300) public String description;
    /** Used by automatic postings; can't be deleted or have its type changed. */
    @Column(name = "system_account", nullable = false) public boolean systemAccount;
    @Column(nullable = false) public boolean active = true;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    /** Assets and expenses grow with debits; liabilities, equity and income with credits. */
    public boolean isDebitNormal() { return "ASSET".equals(type) || "EXPENSE".equals(type); }
    public String getLabel() { return code + " · " + name; }
    public String getTypeLabel() {
        return switch (type) { case "ASSET" -> "Asset"; case "LIABILITY" -> "Liability"; case "EQUITY" -> "Equity"; case "INCOME" -> "Income"; default -> "Expense"; };
    }
}
