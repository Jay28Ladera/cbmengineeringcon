package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.inventory.StockEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Posts stock movements to the ledger, in the same transaction as the movement (so both happen or neither):
 * <pre>
 *  Receiving             Dr Inventory                      Cr Goods received, not yet billed
 *  Issue to a job        Dr Materials used (job)           Cr Inventory
 *  Return from a site    Dr Inventory                      Cr Materials used (job)
 *  Opening balance       Dr Inventory                      Cr Opening balance equity
 *  Damaged / lost        Dr Inventory losses               Cr Inventory
 *  Count difference      Dr/Cr Inventory                   Cr/Dr Inventory losses
 * </pre>
 * Voiding a receipt or an issue reverses its entry.
 */
@Component
public class InventoryPosting {

    private final Ledger ledger;

    public InventoryPosting(Ledger ledger) { this.ledger = ledger; }

    @EventListener
    public void on(StockEvent e) {
        switch (e.kind()) {
            case "RECEIPT_VOID" -> { ledger.reverseSource("RECEIPT", e.docId(), "Receipt voided" + (e.reason() == null ? "" : ": " + e.reason()), e.by()); return; }
            case "ISSUE_VOID" -> { ledger.reverseSource("ISSUE", e.docId(), "Issuance voided" + (e.reason() == null ? "" : ": " + e.reason()), e.by()); return; }
            default -> { }
        }
        Long inv = ledger.id(Acct.INVENTORY);
        List<JournalLine> l = new ArrayList<>();
        String memo;
        switch (e.kind()) {
            case "RECEIPT" -> {
                memo = "Stock received " + e.docNumber();
                l.add(JournalLine.dr(inv, e.amount(), memo).supplier(e.supplierId()));
                l.add(JournalLine.cr(ledger.id(Acct.GRNI), e.amount(), memo).supplier(e.supplierId()));
            }
            case "ISSUE" -> {
                memo = "Materials issued " + e.docNumber() + (e.reason() == null ? "" : " · " + e.reason());
                l.add(JournalLine.dr(ledger.id(Acct.MATERIALS_USED), e.amount(), memo).job(e.jobOrderId()));
                l.add(JournalLine.cr(inv, e.amount(), memo).job(e.jobOrderId()));
            }
            case "ADJUSTMENT" -> {
                String reason = e.reason() == null ? "" : e.reason();
                memo = "Stock adjustment " + e.docNumber() + " · " + reason.toLowerCase();
                Long other = switch (reason) {
                    case "RETURNED" -> ledger.id(Acct.MATERIALS_USED);
                    case "OPENING" -> ledger.id(Acct.OPENING_BALANCE_EQUITY);
                    default -> ledger.id(Acct.INVENTORY_LOSSES);
                };
                if (e.direction() > 0) { l.add(JournalLine.dr(inv, e.amount(), memo)); l.add(JournalLine.cr(other, e.amount(), memo).job(e.jobOrderId())); }
                else { l.add(JournalLine.dr(other, e.amount(), memo).job(e.jobOrderId())); l.add(JournalLine.cr(inv, e.amount(), memo)); }
            }
            default -> { return; }
        }
        ledger.post(e.date(), memo, e.kind(), e.docId(), e.docNumber(), l, e.by());
    }
}
