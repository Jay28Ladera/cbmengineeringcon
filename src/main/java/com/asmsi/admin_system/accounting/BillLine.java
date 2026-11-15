package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "acc_bill_line")
public class BillLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "bill_id", nullable = false) public Long billId;
    @Column(nullable = false, length = 300) public String description;
    @Column(nullable = false, precision = 14, scale = 3) public BigDecimal qty = BigDecimal.ONE;
    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2) public BigDecimal unitPrice = BigDecimal.ZERO;
    /** qty × unit price, as on the supplier's invoice (VAT-inclusive when the bill is). */
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal amount = BigDecimal.ZERO;
    /** Debited account: goods received not billed for PO lines, or the expense. */
    @Column(name = "account_id", nullable = false) public Long accountId;
    @Column(name = "job_order_id") public Long jobOrderId;
    @Column(name = "po_line_id") public Long poLineId;
    @Column(name = "sort_order") public int sortOrder;
}
