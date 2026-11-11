package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quotations")
public class Quotation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true) private String quotationNumber;
    @OneToOne(optional = false) @JoinColumn(name = "inquiry_id", nullable = false, unique = true)
    private Inquiry inquiry;
    @Column(nullable = false) private String status = "DRAFT";
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column private LocalDateTime sentAt;
    @Column private LocalDateTime decidedAt;
    @Column(length = 4000) private String terms;
    @Column(precision = 14, scale = 2) private BigDecimal taxRate = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2) private BigDecimal discountAmount = BigDecimal.ZERO;
    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<QuotationLineItem> lineItems = new ArrayList<>();

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getQuotationNumber() { return quotationNumber; }
    public void setQuotationNumber(String quotationNumber) { this.quotationNumber = quotationNumber; }
    public Inquiry getInquiry() { return inquiry; }
    public void setInquiry(Inquiry inquiry) { this.inquiry = inquiry; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
    public String getTerms() { return terms; }
    public void setTerms(String terms) { this.terms = terms; }
    public BigDecimal getTaxRate() { return taxRate == null ? BigDecimal.ZERO : taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    public BigDecimal getDiscountAmount() { return discountAmount == null ? BigDecimal.ZERO : discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public List<QuotationLineItem> getLineItems() { return lineItems; }
    public void setLineItems(List<QuotationLineItem> lineItems) { this.lineItems = lineItems; }
    public void addLineItem(QuotationLineItem item) { lineItems.add(item); item.setQuotation(this); }
    public void clearLineItems() { lineItems.clear(); }

    @Transient
    public BigDecimal getSubtotal() {
        return lineItems.stream().map(QuotationLineItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    @Transient
    public BigDecimal getTaxAmount() { return getSubtotal().subtract(getDiscountAmount()).max(BigDecimal.ZERO).multiply(getTaxRate()).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP); }
    @Transient
    public BigDecimal getGrandTotal() { return getSubtotal().subtract(getDiscountAmount()).max(BigDecimal.ZERO).add(getTaxAmount()); }
}
