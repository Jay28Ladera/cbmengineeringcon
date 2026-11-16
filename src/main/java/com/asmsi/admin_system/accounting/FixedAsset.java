package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

/** Equipment, vehicles, tools and other property, depreciated straight-line every month. */
@Entity
@Table(name = "acc_fixed_asset")
public class FixedAsset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, length = 20) public String code;
    @Column(nullable = false, length = 160) public String name;
    /** TOOLS, EQUIPMENT, VEHICLE, OFFICE, BUILDING */
    @Column(nullable = false, length = 20) public String category = "EQUIPMENT";
    @Column(name = "asset_account_id", nullable = false) public Long assetAccountId;
    @Column(name = "serial_no", length = 80) public String serialNo;
    @Column(name = "plate_no", length = 20) public String plateNo;
    @Column(length = 120) public String custodian;
    @Column(length = 160) public String location;
    /** Project the asset is used on now: its depreciation is charged to the job. */
    @Column(name = "job_order_id") public Long jobOrderId;
    @Column(name = "supplier_id") public Long supplierId;
    @Column(name = "acquired_date", nullable = false) public LocalDate acquiredDate;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal cost = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal salvage = BigDecimal.ZERO;
    @Column(name = "life_months", nullable = false) public int lifeMonths = 60;
    /** First month depreciated. */
    @Column(name = "dep_start", nullable = false) public LocalDate depStart;
    /** Depreciation before this system (for assets brought in with an opening balance). */
    @Column(name = "opening_accum", nullable = false, precision = 16, scale = 2) public BigDecimal openingAccum = BigDecimal.ZERO;
    /** BOOKED (cost already in the books), OPENING (opening balance) or PAID (paid now from a bank) */
    @Column(name = "acquired_how", nullable = false, length = 10) public String acquiredHow = "BOOKED";
    @Column(name = "acquisition_journal_id") public Long acquisitionJournalId;
    /** ACTIVE or DISPOSED */
    @Column(nullable = false, length = 10) public String status = "ACTIVE";
    @Column(name = "disposed_date") public LocalDate disposedDate;
    @Column(name = "disposal_proceeds", precision = 16, scale = 2) public BigDecimal disposalProceeds;
    @Column(name = "disposal_note", length = 300) public String disposalNote;
    @Column(name = "disposal_journal_id") public Long disposalJournalId;
    @Column(length = 600) public String notes;
    @Column(name = "created_by", length = 120) public String createdBy;
    @Column(name = "created_at") public LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public boolean isActive() { return "ACTIVE".equals(status); }
    public BigDecimal getDepreciable() { return cost.subtract(salvage).max(BigDecimal.ZERO); }
    /** Straight-line amount for a full month. */
    public BigDecimal getMonthly() {
        return lifeMonths <= 0 ? BigDecimal.ZERO : getDepreciable().divide(BigDecimal.valueOf(lifeMonths), 2, RoundingMode.HALF_UP);
    }
    public YearMonth getStartMonth() { return YearMonth.from(depStart); }
    /** First month not already covered by the depreciation entered as "to date". */
    public YearMonth getFirstOpenMonth() {
        BigDecimal m = getMonthly();
        long covered = m.signum() == 0 || openingAccum == null ? 0 : openingAccum.divide(m, 0, RoundingMode.DOWN).longValue();
        return getStartMonth().plusMonths(covered);
    }
    public YearMonth getEndMonth() { return getStartMonth().plusMonths(Math.max(lifeMonths, 1) - 1L); }
    public String getCategoryLabel() { return FixedAssetService.categoryLabel(category); }
}
