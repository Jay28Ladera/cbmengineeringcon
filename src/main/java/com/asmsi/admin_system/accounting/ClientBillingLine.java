package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** One scope item on a progress billing: its contract value and % accomplished before and to date. */
@Entity
@Table(name = "acc_client_billing_line")
public class ClientBillingLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "billing_id", nullable = false) public Long billingId;
    @Column(name = "quote_item_id") public Long quoteItemId;
    @Column(nullable = false, length = 400) public String description;
    @Column(name = "contract_value", nullable = false, precision = 16, scale = 2) public BigDecimal contractValue = BigDecimal.ZERO;
    @Column(name = "prev_pct", nullable = false, precision = 7, scale = 3) public BigDecimal prevPct = BigDecimal.ZERO;
    @Column(name = "to_date_pct", nullable = false, precision = 7, scale = 3) public BigDecimal toDatePct = BigDecimal.ZERO;
    @Column(name = "prev_amount", nullable = false, precision = 16, scale = 2) public BigDecimal prevAmount = BigDecimal.ZERO;
    @Column(name = "to_date_amount", nullable = false, precision = 16, scale = 2) public BigDecimal toDateAmount = BigDecimal.ZERO;
    @Column(name = "sort_order") public int sortOrder;

    public BigDecimal getThisPct() { return toDatePct.subtract(prevPct); }
    public BigDecimal getThisAmount() { return toDateAmount.subtract(prevAmount); }
}
