package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.inventory.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Supplier bills. A bill for a PO debits "goods received, not yet billed" (cleared by receiving); an expense bill
 * debits the expense. VAT goes to input VAT; the total is owed to the supplier (accounts payable).
 * Also decides whether a pay-before-ordering PO can be placed.
 */
@Service
public class PayablesService implements PoPaymentGate {

    public static final List<String> OPEN = List.of("OPEN", "PARTIAL");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BillRepository bills;
    private final BillLineRepository billLines;
    private final VoucherLineRepository voucherLines;
    private final PaymentVoucherRepository vouchers;
    private final PurchaseOrderRepository orders;
    private final PurchaseOrderLineRepository orderLines;
    private final SupplierRepository suppliers;
    private final Ledger ledger;
    private final InventoryService numbers;

    public PayablesService(BillRepository bills, BillLineRepository billLines, VoucherLineRepository voucherLines, PaymentVoucherRepository vouchers,
                           PurchaseOrderRepository orders, PurchaseOrderLineRepository orderLines, SupplierRepository suppliers,
                           Ledger ledger, InventoryService numbers) {
        this.bills = bills; this.billLines = billLines; this.voucherLines = voucherLines; this.vouchers = vouchers;
        this.orders = orders; this.orderLines = orderLines; this.suppliers = suppliers; this.ledger = ledger; this.numbers = numbers;
    }

    public record LineIn(String description, BigDecimal qty, BigDecimal unitPrice, Long accountId, Long jobOrderId, Long poLineId) {}

    // ------------------------------------------------------------------ from a PO

    /** Suggested bill for a PO: pay-first POs bill what was ordered; others bill what was received and not yet billed. */
    public Bill draftFromPo(PurchaseOrder po, List<LineIn> outLines) {
        Supplier s = suppliers.findById(po.supplierId).orElse(null);
        Bill b = new Bill();
        b.supplierId = po.supplierId; b.poId = po.id; b.jobOrderId = po.jobOrderId; b.kind = "PO";
        b.billDate = LocalDate.now();
        b.dueDate = po.termDays == null ? LocalDate.now() : LocalDate.now().plusDays(Math.max(0, po.termDays));
        if (po.taxRate != null && po.taxRate.signum() > 0) { b.vatMode = "EXCLUSIVE"; b.vatRate = po.taxRate; }
        else b.vatMode = po.pricesIncludeVat(s) ? "INCLUSIVE" : "NONE";
        b.ewtRate = s == null ? BigDecimal.ONE : s.ewt();
        Long grni = ledger.id(Acct.GRNI);
        for (PurchaseOrderLine l : orderLines.findByPoIdOrderBySortOrderAscIdAsc(po.id)) {
            BigDecimal basis = po.isPayFirst() ? l.qty : l.receivedQty;
            BigDecimal q = basis.subtract(l.billed());
            if (q.signum() <= 0) continue;
            outLines.add(new LineIn(l.description, q, l.unitPrice, grni, po.jobOrderId, l.id));
        }
        return b;
    }

    // ------------------------------------------------------------------ save and post

    @Transactional
    public Bill create(Bill b, List<LineIn> in, String by) {
        if (b.supplierId == null) throw new IllegalArgumentException("Choose the supplier.");
        List<LineIn> use = in.stream().filter(l -> l.description() != null && !l.description().isBlank() && l.qty() != null && l.qty().signum() > 0
                && l.unitPrice() != null && l.unitPrice().signum() > 0).toList();
        if (use.isEmpty()) throw new IllegalArgumentException("Enter at least one line with a quantity and a price.");
        if (use.stream().anyMatch(l -> l.accountId() == null)) throw new IllegalArgumentException("Choose an account for every line.");
        if (b.dueDate == null) b.dueDate = b.billDate;
        if (b.dueDate.isBefore(b.billDate)) throw new IllegalArgumentException("The due date can't be before the bill date.");
        if (b.ewtRate == null || b.ewtRate.signum() < 0) b.ewtRate = BigDecimal.ZERO;
        PurchaseOrder po = b.poId == null ? null : orders.findById(b.poId).orElse(null);
        Map<Long, PurchaseOrderLine> poLines = new HashMap<>();
        if (po != null) for (PurchaseOrderLine l : orderLines.findByPoIdOrderBySortOrderAscIdAsc(po.id)) poLines.put(l.id, l);
        if (po != null) {
            // VAT follows the PO, so the bill clears exactly what the delivery put into "goods received, not yet billed"
            if (po.taxRate != null && po.taxRate.signum() > 0) { b.vatMode = "EXCLUSIVE"; b.vatRate = po.taxRate; }
            else b.vatMode = po.pricesIncludeVat(suppliers.findById(po.supplierId).orElse(null)) ? "INCLUSIVE" : "NONE";
            for (LineIn l : use) {
                PurchaseOrderLine pl = l.poLineId() == null ? null : poLines.get(l.poLineId());
                if (pl == null) throw new IllegalArgumentException("A line doesn't belong to PO " + po.number + ".");
                BigDecimal room = (po.isPayFirst() ? pl.qty : pl.receivedQty).subtract(pl.billed());
                if (l.qty().compareTo(room) > 0) throw new IllegalArgumentException(pl.description + ": only " + room.stripTrailingZeros().toPlainString() + " left to bill.");
            }
        }

        b.number = numbers.nextNumber("acc_bill", "BL");
        b.createdBy = by;
        BigDecimal sum = BigDecimal.ZERO;
        List<BillLine> saved = new ArrayList<>();
        bills.save(b);
        int n = 0;
        for (LineIn l : use) {
            BillLine x = new BillLine();
            x.billId = b.id; x.description = cut(l.description().trim(), 300); x.qty = l.qty(); x.unitPrice = Ledger.r2(l.unitPrice());
            x.amount = Ledger.r2(l.qty().multiply(l.unitPrice())); x.accountId = l.accountId(); x.jobOrderId = l.jobOrderId() != null ? l.jobOrderId() : b.jobOrderId;
            x.poLineId = l.poLineId(); x.sortOrder = n++;
            billLines.save(x); saved.add(x);
            sum = sum.add(x.amount);
            if (x.poLineId != null) {
                PurchaseOrderLine pl = poLines.get(x.poLineId);
                if (pl != null) { pl.billedQty = pl.billed().add(x.qty); orderLines.save(pl); }
            }
        }
        switch (b.vatMode) {
            case "INCLUSIVE" -> { b.gross = sum; b.vat = Ledger.r2(sum.multiply(BigDecimal.valueOf(12)).divide(BigDecimal.valueOf(112), 6, RoundingMode.HALF_UP)); b.vatRate = BigDecimal.valueOf(12); }
            case "EXCLUSIVE" -> { b.vat = Ledger.r2(sum.multiply(b.vatRate).divide(HUNDRED, 6, RoundingMode.HALF_UP)); b.gross = sum.add(b.vat); }
            default -> { b.vatMode = "NONE"; b.gross = sum; b.vat = BigDecimal.ZERO; }
        }
        bills.save(b);
        post(b, saved, by);
        return b;
    }

    /** Dr each line (net of its share of VAT), Dr input VAT, Cr accounts payable. */
    private void post(Bill b, List<BillLine> ls, String by) {
        String supplier = suppliers.findById(b.supplierId).map(s -> s.name).orElse("supplier");
        String memo = "Bill " + b.number + " · " + supplier + (b.invoiceNo == null ? "" : " · invoice " + b.invoiceNo);
        List<JournalLine> j = new ArrayList<>();
        BigDecimal vatLeft = "INCLUSIVE".equals(b.vatMode) ? b.vat : BigDecimal.ZERO;
        BigDecimal sum = ls.stream().map(l -> l.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        for (int i = 0; i < ls.size(); i++) {
            BillLine l = ls.get(i);
            BigDecimal share = BigDecimal.ZERO;
            if ("INCLUSIVE".equals(b.vatMode) && sum.signum() > 0)
                share = i == ls.size() - 1 ? vatLeft : Ledger.r2(b.vat.multiply(l.amount).divide(sum, 6, RoundingMode.HALF_UP));
            vatLeft = vatLeft.subtract(share);
            j.add(JournalLine.dr(l.accountId, l.amount.subtract(share), l.description).job(l.jobOrderId).supplier(b.supplierId));
        }
        if (b.vat.signum() > 0) j.add(JournalLine.dr(ledger.id(Acct.INPUT_VAT), b.vat, "Input VAT · " + b.number).supplier(b.supplierId));
        j.add(JournalLine.cr(ledger.id(Acct.AP_SUPPLIERS), b.gross, memo).supplier(b.supplierId));
        ledger.post(b.billDate, memo, "BILL", b.id, b.number, j, by);
    }

    /** Saves a bill whose posting was made by its source document (a subcontractor billing). */
    @Transactional
    public Bill registerPosted(Bill b, String description, Long accountId, String by) {
        b.number = numbers.nextNumber("acc_bill", "BL");
        b.createdBy = by;
        bills.save(b);
        BillLine x = new BillLine();
        x.billId = b.id; x.description = cut(description, 300); x.qty = BigDecimal.ONE; x.unitPrice = b.gross; x.amount = b.gross;
        x.accountId = accountId; x.jobOrderId = b.jobOrderId;
        billLines.save(x);
        return b;
    }

    /** Voids a bill together with its source document (no posting of its own to reverse). */
    @Transactional
    public void voidPosted(Bill b, String reason) {
        if (b.paid.signum() > 0) throw new IllegalStateException("Bill " + b.number + " has payments. Void the payment voucher first.");
        boolean pending = voucherLines.findByBillId(b.id).stream().anyMatch(vl -> vouchers.findById(vl.voucherId)
                .map(v -> !"CANCELLED".equals(v.status) && !"VOID".equals(v.status)).orElse(false));
        if (pending) throw new IllegalStateException("Bill " + b.number + " is on a payment voucher. Cancel the voucher first.");
        b.status = "VOID"; b.voidReason = reason;
        bills.save(b);
    }

    @Transactional
    public void voidBill(Bill b, String reason, String by) {
        if ("VOID".equals(b.status)) return;
        if ("SUBCON".equals(b.kind)) throw new IllegalStateException("This bill comes from a subcontractor billing. Void it there.");
        if (b.paid.signum() > 0) throw new IllegalStateException("Bill " + b.number + " has payments. Void the payment voucher first.");
        boolean pending = voucherLines.findByBillId(b.id).stream().anyMatch(vl -> vouchers.findById(vl.voucherId)
                .map(v -> !"CANCELLED".equals(v.status) && !"VOID".equals(v.status)).orElse(false));
        if (pending) throw new IllegalStateException("Bill " + b.number + " is on a payment voucher. Cancel the voucher first.");
        for (BillLine l : billLines.findByBillIdOrderBySortOrderAscIdAsc(b.id))
            if (l.poLineId != null) orderLines.findById(l.poLineId).ifPresent(pl -> { pl.billedQty = pl.billed().subtract(l.qty).max(BigDecimal.ZERO); orderLines.save(pl); });
        ledger.reverseSource("BILL", b.id, reason, by);
        b.status = "VOID"; b.voidReason = reason;
        bills.save(b);
    }

    /** Recomputes paid and status from released vouchers. */
    @Transactional
    public void refresh(Bill b) {
        if ("VOID".equals(b.status)) return;
        BigDecimal paid = BigDecimal.ZERO;
        for (VoucherLine vl : voucherLines.findByBillId(b.id)) {
            PaymentVoucher v = vouchers.findById(vl.voucherId).orElse(null);
            if (v != null && "RELEASED".equals(v.status)) paid = paid.add(vl.gross);
        }
        b.paid = paid;
        b.status = paid.signum() == 0 ? "OPEN" : (paid.compareTo(b.gross) >= 0 ? "PAID" : "PARTIAL");
        bills.save(b);
    }

    // ------------------------------------------------------------------ PO gate

    @Override
    public String blockReason(PurchaseOrder po) {
        if (!po.isPayFirst()) return null;
        List<Bill> bs = bills.findByPoIdOrderByIdAsc(po.id).stream().filter(b -> !"VOID".equals(b.status)).toList();
        if (bs.isEmpty()) return "This PO is pay-before-ordering (cash/COD). Accounting must release the payment first: use \"Request payment\".";
        Bill open = bs.stream().filter(b -> !"PAID".equals(b.status)).findFirst().orElse(null);
        if (open == null) return null;
        String v = pendingVoucher(open);
        return "Waiting for payment of bill " + open.number + (v == null ? " (no payment voucher yet)." : " (" + v + ").");
    }

    @Override
    public String paymentStatus(PurchaseOrder po) {
        List<Bill> bs = bills.findByPoIdOrderByIdAsc(po.id).stream().filter(b -> !"VOID".equals(b.status)).toList();
        if (bs.isEmpty()) return null;
        List<String> parts = new ArrayList<>();
        for (Bill b : bs) {
            if ("PAID".equals(b.status)) parts.add(b.number + " paid");
            else {
                String v = pendingVoucher(b);
                parts.add(b.number + " ₱" + String.format("%,.2f", b.getBalance()) + (v != null ? " · " + v : " · due " + b.dueDate));
            }
        }
        return String.join("; ", parts);
    }

    private String pendingVoucher(Bill b) {
        for (VoucherLine vl : voucherLines.findByBillId(b.id)) {
            PaymentVoucher v = vouchers.findById(vl.voucherId).orElse(null);
            if (v == null) continue;
            switch (v.status) {
                case "DRAFT" -> { return v.number + " being prepared"; }
                case "FOR_APPROVAL" -> { return v.number + " waiting for approval"; }
                case "APPROVED" -> { return v.number + " approved, to be released"; }
                default -> { }
            }
        }
        return null;
    }

    private static String cut(String s, int n) { return s == null ? null : (s.length() <= n ? s : s.substring(0, n)); }
}
