package com.asmsi.admin_system.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Published by InventoryService whenever stock value changes, so Accounting can post it to the ledger.
 * kind: RECEIPT, RECEIPT_VOID, ISSUE, ISSUE_VOID, ADJUSTMENT. amount = total cost moved (always positive);
 * for ADJUSTMENT, direction is +1 (stock in) or -1 (stock out) and reason is the adjustment reason.
 */
public record StockEvent(String kind, Long docId, String docNumber, LocalDate date, Long jobOrderId, Long supplierId,
                         BigDecimal amount, int direction, String reason, String by) {}
