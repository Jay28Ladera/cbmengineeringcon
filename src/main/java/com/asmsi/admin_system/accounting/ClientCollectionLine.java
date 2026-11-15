package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "acc_collection_line")
public class ClientCollectionLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "collection_id", nullable = false) public Long collectionId;
    @Column(name = "billing_id", nullable = false) public Long billingId;
    /** Part of the billing settled: cash received plus the tax the client withheld. */
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal applied = BigDecimal.ZERO;
    @Column(nullable = false, precision = 16, scale = 2) public BigDecimal cwt = BigDecimal.ZERO;
}
