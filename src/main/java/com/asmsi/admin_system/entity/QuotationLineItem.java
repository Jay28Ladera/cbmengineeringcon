package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "quotation_line_items")
public class QuotationLineItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;
    @Column(nullable = false, length = 500)
    private String description;
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;
    @Column(nullable = false, length = 40)
    private String unit = "unit";
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;
    /** MATERIAL, LABOR, SUBCON, EQUIPMENT or OTHER. Null on lines saved before costing existed. */
    @Column(length = 12)
    private String kind;
    /** Inventory material this line is for (material lines only). */
    @Column(name = "material_id")
    private Long materialId;
    /** Our own cost per unit. Internal: never printed for the client. */
    @Column(name = "unit_cost", precision = 14, scale = 2)
    private BigDecimal unitCost;
    /** Labor lines: workers × days × rate per day. Internal. */
    @Column(name = "crew_size")
    private Integer crewSize;
    @Column(name = "work_days", precision = 8, scale = 2)
    private BigDecimal workDays;
    @Column(name = "daily_rate", precision = 12, scale = 2)
    private BigDecimal dailyRate;

    public Long getId() { return id; }
    public Quotation getQuotation() { return quotation; }
    public void setQuotation(Quotation quotation) { this.quotation = quotation; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getQuantity() { return quantity == null ? BigDecimal.ZERO : quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getUnitPrice() { return unitPrice == null ? BigDecimal.ZERO : unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public String getKind() { return kind == null || kind.isBlank() ? "OTHER" : kind; }
    public void setKind(String kind) { this.kind = kind; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public Integer getCrewSize() { return crewSize; }
    public void setCrewSize(Integer crewSize) { this.crewSize = crewSize; }
    public BigDecimal getWorkDays() { return workDays; }
    public void setWorkDays(BigDecimal workDays) { this.workDays = workDays; }
    public BigDecimal getDailyRate() { return dailyRate; }
    public void setDailyRate(BigDecimal dailyRate) { this.dailyRate = dailyRate; }
    /** A labor line costed as a crew: workers × days × rate per day. */
    @Transient public boolean isCrew() { return "LABOR".equals(getKind()) && crewSize != null && workDays != null && dailyRate != null; }
    @Transient public BigDecimal getCrewCost() {
        return isCrew() ? BigDecimal.valueOf(crewSize).multiply(workDays).multiply(dailyRate).setScale(2, java.math.RoundingMode.HALF_UP) : null;
    }
    @Transient public BigDecimal getManDays() { return isCrew() ? BigDecimal.valueOf(crewSize).multiply(workDays).stripTrailingZeros() : null; }
    @Transient public boolean isMaterial() { return "MATERIAL".equals(getKind()); }
    @Transient public boolean isCosted() { return unitCost != null || isCrew(); }
    @Transient
    public BigDecimal getLineCost() { if (isCrew()) return getCrewCost(); return unitCost == null ? BigDecimal.ZERO : getQuantity().multiply(unitCost).setScale(2, java.math.RoundingMode.HALF_UP); }
    /** Markup on cost, in percent; null when there is no cost. */
    @Transient
    public BigDecimal getMarkupPct() {
        BigDecimal lc = getLineCost(), amt = getLineTotal();
        if (isCrew()) return lc.signum() <= 0 ? null : amt.subtract(lc).multiply(new BigDecimal("100")).divide(lc, 1, java.math.RoundingMode.HALF_UP);
        if (unitCost == null || unitCost.signum() <= 0) return null;
        return getUnitPrice().subtract(unitCost).multiply(new BigDecimal("100")).divide(unitCost, 1, java.math.RoundingMode.HALF_UP);
    }
    @Transient
    public String getKindLabel() {
        return switch (getKind()) { case "MATERIAL" -> "Material"; case "LABOR" -> "Labor"; case "SUBCON" -> "Subcontract"; case "EQUIPMENT" -> "Equipment"; default -> "Other"; };
    }
    @Transient
    public BigDecimal getLineTotal() { return getQuantity().multiply(getUnitPrice()).setScale(2, java.math.RoundingMode.HALF_UP); }
}
