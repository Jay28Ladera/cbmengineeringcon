package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales")
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** e.g. "jmladera" or whatever Authentication.getName() returns */
    @Column(name = "cashier_name", length = 120)
    private String cashierName;

    /** e.g. POS-20250903-000123 */
    @Column(name = "receipt_no", length = 64, unique = true)
    private String receiptNo;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "cash", nullable = false, precision = 12, scale = 2)
    private BigDecimal cash = BigDecimal.ZERO;

    @Column(name = "change_due", nullable = false, precision = 12, scale = 2)
    private BigDecimal changeDue = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Child rows; cascade ensures children are saved with the parent. */
    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SaleItem> items = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    /* Helper methods keep both sides in sync */
    public void addItem(SaleItem si) {
        if (si == null) return;
        items.add(si);
        si.setSale(this); // sets FK side
    }
    public void removeItem(SaleItem si) {
        if (si == null) return;
        items.remove(si);
        si.setSale(null);
    }

    /* getters & setters */

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCashierName() { return cashierName; }
    public void setCashierName(String cashierName) { this.cashierName = cashierName; }

    public String getReceiptNo() { return receiptNo; }
    public void setReceiptNo(String receiptNo) { this.receiptNo = receiptNo; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public BigDecimal getCash() { return cash; }
    public void setCash(BigDecimal cash) { this.cash = cash; }

    public BigDecimal getChangeDue() { return changeDue; }
    public void setChangeDue(BigDecimal changeDue) { this.changeDue = changeDue; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<SaleItem> getItems() { return items; }
    public void setItems(List<SaleItem> items) { this.items = items; }
}
