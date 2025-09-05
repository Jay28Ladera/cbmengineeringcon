package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "sale_items")
public class SaleItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;
    
    // NEWLY ADDED: Store product name at time of sale
    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;
    
    // NEWLY ADDED: Store item number at time of sale  
    @Column(name = "item_number", nullable = false, length = 50)
    private String itemNumber;
    
    @Column(name = "quantity", nullable = false)
    private Integer quantity;
    
    @Column(name = "price_at_sale", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtSale;
    
    @Column(name = "sub_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal subTotal;
    
    // Constructors
    public SaleItem() {}
    
    public SaleItem(Sale sale, Item item, Integer quantity, BigDecimal priceAtSale) {
        this.sale = sale;
        this.item = item;
        this.quantity = quantity;
        this.priceAtSale = priceAtSale;
        this.subTotal = priceAtSale.multiply(BigDecimal.valueOf(quantity));
        
        // Capture product details at time of sale
        this.productName = item.getName();
        this.itemNumber = item.getItemNumber();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Sale getSale() {
        return sale;
    }
    
    public void setSale(Sale sale) {
        this.sale = sale;
    }
    
    public Item getItem() {
        return item;
    }
    
    public void setItem(Item item) {
        this.item = item;
    }
    
    public String getProductName() {
        return productName;
    }
    
    public void setProductName(String productName) {
        this.productName = productName;
    }
    
    public String getItemNumber() {
        return itemNumber;
    }
    
    public void setItemNumber(String itemNumber) {
        this.itemNumber = itemNumber;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
        // Recalculate subtotal when quantity changes
        if (this.priceAtSale != null) {
            this.subTotal = this.priceAtSale.multiply(BigDecimal.valueOf(quantity));
        }
    }
    
    public BigDecimal getPriceAtSale() {
        return priceAtSale;
    }
    
    public void setPriceAtSale(BigDecimal priceAtSale) {
        this.priceAtSale = priceAtSale;
        // Recalculate subtotal when price changes
        if (this.quantity != null) {
            this.subTotal = priceAtSale.multiply(BigDecimal.valueOf(this.quantity));
        }
    }
    
    public BigDecimal getSubTotal() {
        return subTotal;
    }
    
    public void setSubTotal(BigDecimal subTotal) {
        this.subTotal = subTotal;
    }
    
    // Helper method to ensure subtotal is always correct
    public void recalculateSubTotal() {
        if (this.priceAtSale != null && this.quantity != null) {
            this.subTotal = this.priceAtSale.multiply(BigDecimal.valueOf(this.quantity));
        }
    }
    
    // Method to update from current item (in case you need to refresh)
    public void updateFromCurrentItem() {
        if (this.item != null) {
            this.productName = this.item.getName();
            this.itemNumber = this.item.getItemNumber();
        }
    }
}