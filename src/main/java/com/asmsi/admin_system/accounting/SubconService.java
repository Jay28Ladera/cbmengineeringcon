package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.inventory.InventoryService;
import com.asmsi.admin_system.inventory.Supplier;
import com.asmsi.admin_system.inventory.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.asmsi.admin_system.accounting.ClientBillingService.*;

/**
 * Subcontracts and subcontractor billings. Posting a billing records the cost on the job and creates the bill Finance pays:
 *   Downpayment: Dr advances to subcontractors / Cr AP subcontractors
 *   Progress:    Dr subcontract costs (job, net) + input VAT / Cr advances (recouped) + retention payable + AP subcontractors
 *   Retention:   Dr retention payable / Cr AP subcontractors
 */
@Service
public class SubconService {

    private final SubconContractRepository contracts;
    private final SubconBillingRepository billings;
    private final BillRepository bills;
    private final PayablesService payables;
    private final SupplierRepository suppliers;
    private final Ledger ledger;
    private final InventoryService numbers;
    private final ClientBillingService jobs;

    public SubconService(SubconContractRepository contracts, SubconBillingRepository billings, BillRepository bills, PayablesService payables,
                         SupplierRepository suppliers, Ledger ledger, InventoryService numbers, ClientBillingService jobs) {
        this.contracts = contracts; this.billings = billings; this.bills = bills; this.payables = payables; this.suppliers = suppliers;
        this.ledger = ledger; this.numbers = numbers; this.jobs = jobs;
    }

    // ------------------------------------------------------------------ contracts

    @Transactional
    public SubconContract saveContract(SubconContract c, String by) {
        if (c.supplierId == null) throw new IllegalArgumentException("Choose the subcontractor.");
        if (c.jobOrderId == null) throw new IllegalArgumentException("Choose the job order.");
        if (c.scope == null || c.scope.isBlank()) throw new IllegalArgumentException("Describe the scope of work.");
        if (c.amount == null || c.amount.signum() <= 0) throw new IllegalArgumentException("Enter the subcontract amount.");
        if (c.dpPct.signum() < 0 || c.dpPct.compareTo(HUNDRED) > 0 || c.retentionPct.signum() < 0 || c.retentionPct.compareTo(BigDecimal.valueOf(50)) > 0)
            throw new IllegalArgumentException("Check the downpayment and retention %.");
        if (c.id != null) {
            Summary s = summary(c);
            if (c.amount.compareTo(s.accomplished()) < 0) throw new IllegalArgumentException("The amount can't be below what was already billed (₱" + peso(s.accomplished()) + ").");
        } else { c.number = numbers.nextNumber("acc_subcon_contract", "SC"); c.createdBy = by; }
        c.amount = r2(c.amount);
        return contracts.save(c);
    }

    public record Summary(BigDecimal amount, BigDecimal dpBilled, BigDecimal recouped, BigDecimal accomplished, BigDecimal pct,
                          BigDecimal retentionHeld, BigDecimal retentionReleased, BigDecimal billed, BigDecimal paid) {
        public BigDecimal getDpUnrecouped() { return dpBilled.subtract(recouped); }
        public BigDecimal getRetentionUnreleased() { return retentionHeld.subtract(retentionReleased); }
        public BigDecimal getUnbilled() { return amount.subtract(accomplished).max(BigDecimal.ZERO); }
        public BigDecimal getPayable() { return billed.subtract(paid); }
        public BigDecimal getPctBilled() { return amount.signum() == 0 ? BigDecimal.ZERO : accomplished.multiply(HUNDRED).divide(amount, 1, RoundingMode.HALF_UP); }
    }

    @Transactional(readOnly = true)
    public Summary summary(SubconContract c) {
        BigDecimal dp = BigDecimal.ZERO, rec = BigDecimal.ZERO, acc = BigDecimal.ZERO, pct = BigDecimal.ZERO, held = BigDecimal.ZERO, rel = BigDecimal.ZERO,
                billed = BigDecimal.ZERO, paid = BigDecimal.ZERO;
        if (c.id != null) for (SubconBilling b : billings.findByContractIdOrderByIdAsc(c.id)) {
            if (!b.isPosted()) continue;
            switch (b.kind) {
                case "DOWNPAYMENT" -> dp = dp.add(b.gross);
                case "RETENTION" -> rel = rel.add(b.gross);
                default -> { rec = rec.add(b.recoupment); held = held.add(b.retention); acc = b.grossToDate; pct = b.pctToDate; }
            }
            billed = billed.add(b.amountDue);
            if (b.billId != null) paid = paid.add(bills.findById(b.billId).map(x -> x.paid).orElse(BigDecimal.ZERO));
        }
        return new Summary(c.amount, dp, rec, acc, pct, held, rel, billed, paid);
    }

    // ------------------------------------------------------------------ billings

    @Transactional
    public SubconBilling draft(SubconContract c, String kind, String by) {
        if (!"ACTIVE".equals(c.status)) throw new IllegalStateException("Subcontract " + c.number + " is " + c.status.toLowerCase() + ".");
        billings.findByContractIdOrderByIdAsc(c.id).stream().filter(SubconBilling::isEditable).findFirst()
                .ifPresent(d -> { throw new IllegalStateException("Finish the draft " + d.number + " first."); });
        Summary s = summary(c);
        SubconBilling b = new SubconBilling();
        b.contractId = c.id; b.supplierId = c.supplierId; b.jobOrderId = c.jobOrderId; b.kind = kind;
        b.seqNo = (int) billings.findByContractIdOrderByIdAsc(c.id).stream().filter(x -> kind.equals(x.kind) && !"VOID".equals(x.status)).count() + 1;
        b.billingDate = LocalDate.now(); b.preparedBy = by;
        b.number = numbers.nextNumber("acc_subcon_billing", "SCB");
        switch (kind) {
            case "DOWNPAYMENT" -> {
                BigDecimal room = c.getDpAmount().subtract(s.dpBilled());
                if (room.signum() <= 0) throw new IllegalStateException("No downpayment left on this subcontract (" + c.dpPct.stripTrailingZeros().toPlainString() + "%).");
                b.gross = room; b.amountDue = room;
            }
            case "RETENTION" -> {
                BigDecimal room = s.getRetentionUnreleased();
                if (room.signum() <= 0) throw new IllegalStateException("No retention is held for this subcontractor.");
                b.gross = room; b.amountDue = room;
            }
            default -> { b.pctToDate = s.pct(); b.prevGross = s.accomplished(); b.grossToDate = s.accomplished(); compute(b, c, s); }
        }
        return billings.save(b);
    }

    @Transactional
    public SubconBilling update(SubconBilling b, BigDecimal pctToDate, BigDecimal amount, LocalDate date, String invoiceNo, String verifiedBy, String memo) {
        if (!b.isEditable()) throw new IllegalStateException(b.number + " is already posted.");
        SubconContract c = contracts.findById(b.contractId).orElseThrow();
        Summary s = summary(c);
        if (date != null) b.billingDate = date;
        b.invoiceNo = cut(invoiceNo, 60); b.verifiedBy = cut(verifiedBy, 160); b.memo = cut(memo, 600);
        if ("PROGRESS".equals(b.kind)) {
            if (pctToDate != null) {
                BigDecimal p = pctToDate.setScale(3, RoundingMode.HALF_UP);
                if (p.compareTo(s.pct()) < 0) throw new IllegalArgumentException("Can't go below the " + s.pct().stripTrailingZeros().toPlainString() + "% already billed.");
                if (p.compareTo(HUNDRED) > 0) throw new IllegalArgumentException("Can't be more than 100%.");
                b.pctToDate = p;
            }
            compute(b, c, s);
        } else if (amount != null && amount.signum() > 0) {
            BigDecimal room = "DOWNPAYMENT".equals(b.kind) ? c.getDpAmount().subtract(s.dpBilled()) : s.getRetentionUnreleased();
            if (r2(amount).compareTo(room) > 0) throw new IllegalArgumentException("Only ₱" + peso(room) + " is left.");
            b.gross = r2(amount); b.amountDue = b.gross;
        }
        return billings.save(b);
    }

    void compute(SubconBilling b, SubconContract c, Summary s) {
        b.prevGross = s.accomplished();
        b.grossToDate = b.pctToDate.compareTo(HUNDRED) >= 0 ? c.amount : pctOf(c.amount, b.pctToDate).max(s.accomplished());
        b.gross = b.grossToDate.subtract(b.prevGross);
        boolean last = b.grossToDate.compareTo(c.amount) >= 0;
        BigDecimal unrec = s.getDpUnrecouped().max(BigDecimal.ZERO);
        b.recoupment = last ? unrec : pctOf(b.gross, c.dpPct).min(unrec);
        b.retention = pctOf(b.gross, c.retentionPct);
        b.amountDue = b.gross.subtract(b.recoupment).subtract(b.retention);
        b.vat = c.isVat() ? vatIn(b.gross) : BigDecimal.ZERO;
    }

    /** Records the cost and creates the bill to pay. */
    @Transactional
    public Bill post(SubconBilling b, String by) {
        if (!b.isEditable()) throw new IllegalStateException(b.number + " is already posted.");
        SubconContract c = contracts.findById(b.contractId).orElseThrow();
        if ("PROGRESS".equals(b.kind)) compute(b, c, summary(c));
        if (b.amountDue.signum() <= 0) throw new IllegalStateException("Nothing to bill: raise the % accomplished to date.");
        Supplier sup = suppliers.findById(b.supplierId).orElseThrow();
        Long job = b.jobOrderId;
        String memo = "Subcon " + b.getTitle().toLowerCase() + " " + b.number + " · " + sup.name + " · " + jobs.jobLabel(job) + (b.invoiceNo != null ? " · billing " + b.invoiceNo : "");
        List<JournalLine> j = new ArrayList<>();
        Long ap = ledger.id(Acct.AP_SUBCONTRACTORS);
        switch (b.kind) {
            case "DOWNPAYMENT" -> j.add(JournalLine.dr(ledger.id(Acct.ADVANCES_SUPPLIERS), b.gross, "Advance to " + sup.name).job(job).supplier(sup.id));
            case "RETENTION" -> j.add(JournalLine.dr(ledger.id(Acct.RETENTION_PAYABLE), b.gross, "Retention released").job(job).supplier(sup.id));
            default -> {
                j.add(JournalLine.dr(ledger.id(Acct.SUBCONTRACT_COSTS), b.gross.subtract(b.vat), c.scope).job(job).supplier(sup.id));
                if (b.vat.signum() > 0) j.add(JournalLine.dr(ledger.id(Acct.INPUT_VAT), b.vat, "Input VAT · " + b.number).job(job).supplier(sup.id));
                if (b.recoupment.signum() > 0) j.add(JournalLine.cr(ledger.id(Acct.ADVANCES_SUPPLIERS), b.recoupment, "Advance recouped").job(job).supplier(sup.id));
                if (b.retention.signum() > 0) j.add(JournalLine.cr(ledger.id(Acct.RETENTION_PAYABLE), b.retention, "Retention " + c.retentionPct.stripTrailingZeros().toPlainString() + "%").job(job).supplier(sup.id));
            }
        }
        j.add(JournalLine.cr(ap, b.amountDue, memo).job(job).supplier(sup.id));
        billings.save(b);
        JournalEntry e = ledger.post(b.billingDate, memo, "SUBCON", b.id, b.number, j, by);
        Bill bill = new Bill();
        bill.supplierId = sup.id; bill.jobOrderId = job; bill.kind = "SUBCON"; bill.invoiceNo = b.invoiceNo;
        bill.billDate = b.billingDate; bill.dueDate = b.billingDate.plusDays(15);
        bill.gross = b.amountDue; bill.vatMode = c.isVat() ? "INCLUSIVE" : "NONE";
        // VAT share of what is paid now, so withholding is on the amount net of VAT
        bill.vat = c.isVat() && !"DOWNPAYMENT".equals(b.kind) ? vatIn(b.amountDue) : BigDecimal.ZERO;
        bill.ewtRate = c.ewtRate; bill.memo = memo;
        payables.registerPosted(bill, b.getTitle() + " · " + c.number + " · " + c.scope, ap, by);
        b.status = "POSTED"; b.billId = bill.id; b.journalId = e.id; b.postedBy = by; b.postedAt = LocalDateTime.now();
        billings.save(b);
        return bill;
    }

    @Transactional
    public void voidBilling(SubconBilling b, String reason, String by) {
        if (!b.isPosted()) throw new IllegalStateException("Only a posted billing can be voided; delete a draft instead.");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Give the reason.");
        if ("PROGRESS".equals(b.kind) && billings.findByContractIdOrderByIdAsc(b.contractId).stream()
                .anyMatch(x -> x.id > b.id && "PROGRESS".equals(x.kind) && !"VOID".equals(x.status)))
            throw new IllegalStateException("A later progress billing exists. Void it first.");
        if (b.billId != null) bills.findById(b.billId).ifPresent(bill -> payables.voidPosted(bill, reason.trim()));
        ledger.reverseSource("SUBCON", b.id, reason.trim(), by);
        b.status = "VOID"; b.voidReason = cut(reason.trim(), 300);
        billings.save(b);
    }

    @Transactional
    public void deleteDraft(SubconBilling b) {
        if (!b.isEditable()) throw new IllegalStateException("Only a draft can be deleted.");
        billings.delete(b);
    }
}
