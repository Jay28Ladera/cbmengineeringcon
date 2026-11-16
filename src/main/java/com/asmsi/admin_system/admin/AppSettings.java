package com.asmsi.admin_system.admin;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** System-wide defaults (one row, id 1). Edited in Admin › Settings. */
@Entity
@Table(name = "sys_settings")
public class AppSettings {
    @Id public Long id = 1L;
    /** VAT/tax % put on a new quotation. */
    @Column(name = "quote_tax_rate", precision = 6, scale = 2) public BigDecimal quoteTaxRate = BigDecimal.ZERO;
    /** Terms and notes put on a new quotation. */
    @Column(name = "quote_terms", length = 4000) public String quoteTerms;
    /** Days a sent quotation stays valid (pre-filled when sending). */
    @Column(name = "quote_valid_days") public Integer quoteValidDays = 30;
    /** Days to keep the audit trail; 0 = keep everything. */
    @Column(name = "audit_keep_days") public Integer auditKeepDays = 0;
    @Column(name = "updated_by", length = 120) public String updatedBy;
    @Column(name = "updated_at") public LocalDateTime updatedAt;

    public BigDecimal getQuoteTaxRate() { return quoteTaxRate == null ? BigDecimal.ZERO : quoteTaxRate; }
    public int getQuoteValidDays() { return quoteValidDays == null || quoteValidDays < 1 ? 30 : quoteValidDays; }
    public int getAuditKeepDays() { return auditKeepDays == null || auditKeepDays < 0 ? 0 : auditKeepDays; }
}
