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
    @Transient
    public BigDecimal getLineTotal() { return getQuantity().multiply(getUnitPrice()).setScale(2, java.math.RoundingMode.HALF_UP); }
}
