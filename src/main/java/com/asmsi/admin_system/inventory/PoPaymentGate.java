package com.asmsi.admin_system.inventory;

/** Accounting answers whether a purchase order may be placed (a pay-before-ordering PO must be paid first). */
public interface PoPaymentGate {
    /** Null when the order may be placed, otherwise the reason shown to the user. */
    String blockReason(PurchaseOrder po);
    /** Short payment status for the PO page, e.g. "Paid · DV-2026-0003", "Billed, not paid", or null. */
    default String paymentStatus(PurchaseOrder po) { return null; }
}
